package com.petitcamel.shop.ai.support;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Validates model output against the same JSON schema that was sent to OpenAI. Covers the subset used by
 * the analysis schemas: type (single or list, including "null"), properties, required,
 * additionalProperties=false, enum, items, minimum/maximum, maxItems and maxLength.
 */
public final class JsonSchemaValidator {

    private JsonSchemaValidator() {
    }

    public static List<String> validate(JsonNode schema, JsonNode value) {
        List<String> errors = new ArrayList<>();
        check(schema, value, "$", errors);
        return errors;
    }

    private static void check(JsonNode schema, JsonNode value, String path, List<String> errors) {
        if (errors.size() > 50) {
            return;
        }
        JsonNode typeNode = schema.get("type");
        if (typeNode != null && !matchesType(typeNode, value)) {
            errors.add(path + ": expected " + typeNode + " but was " + describe(value));
            return;
        }
        if (value == null || value.isNull()) {
            return;
        }
        JsonNode enumNode = schema.get("enum");
        if (enumNode != null && enumNode.isArray()) {
            boolean found = false;
            for (JsonNode option : enumNode) {
                if (option.equals(value)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                errors.add(path + ": value not in enum");
            }
        }
        if (value.isNumber()) {
            if (schema.has("minimum") && value.decimalValue().compareTo(schema.get("minimum").decimalValue()) < 0) {
                errors.add(path + ": below minimum");
            }
            if (schema.has("maximum") && value.decimalValue().compareTo(schema.get("maximum").decimalValue()) > 0) {
                errors.add(path + ": above maximum");
            }
        }
        if (value.isTextual() && schema.has("maxLength") && value.asText().length() > schema.get("maxLength").asInt()) {
            errors.add(path + ": longer than maxLength");
        }
        if (value.isObject()) {
            JsonNode properties = schema.path("properties");
            for (JsonNode required : schema.path("required")) {
                if (!value.has(required.asText())) {
                    errors.add(path + "." + required.asText() + ": required");
                }
            }
            JsonNode additional = schema.get("additionalProperties");
            Iterator<Map.Entry<String, JsonNode>> fields = value.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                JsonNode propertySchema = properties.get(field.getKey());
                if (propertySchema == null) {
                    if (additional != null && additional.isBoolean() && !additional.asBoolean()) {
                        errors.add(path + "." + field.getKey() + ": not allowed");
                    }
                    continue;
                }
                check(propertySchema, field.getValue(), path + "." + field.getKey(), errors);
            }
        }
        if (value.isArray()) {
            if (schema.has("maxItems") && value.size() > schema.get("maxItems").asInt()) {
                errors.add(path + ": more than maxItems");
            }
            JsonNode items = schema.get("items");
            if (items != null) {
                for (int i = 0; i < value.size(); i++) {
                    check(items, value.get(i), path + "[" + i + "]", errors);
                }
            }
        }
    }

    private static boolean matchesType(JsonNode typeNode, JsonNode value) {
        if (typeNode.isArray()) {
            for (JsonNode option : typeNode) {
                if (matchesSingle(option.asText(), value)) {
                    return true;
                }
            }
            return false;
        }
        return matchesSingle(typeNode.asText(), value);
    }

    private static boolean matchesSingle(String type, JsonNode value) {
        boolean isNull = value == null || value.isNull() || value.isMissingNode();
        return switch (type) {
            case "null" -> isNull;
            case "string" -> !isNull && value.isTextual();
            case "integer" -> !isNull && (value.isIntegralNumber()
                    || (value.isNumber() && value.decimalValue().stripTrailingZeros().scale() <= 0));
            case "number" -> !isNull && value.isNumber();
            case "boolean" -> !isNull && value.isBoolean();
            case "object" -> !isNull && value.isObject();
            case "array" -> !isNull && value.isArray();
            default -> true;
        };
    }

    private static String describe(JsonNode value) {
        if (value == null || value.isMissingNode()) {
            return "missing";
        }
        return value.getNodeType().name().toLowerCase();
    }
}
