package com.petitcamel.shop.ai.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Brings output from providers without strict structured output (Gemini) into the shape the strict schema
 * expects before {@link JsonSchemaValidator} runs: missing nullable properties become null, undeclared
 * properties are dropped, strings are cut to maxLength and arrays to maxItems. Wrong types are left alone, so
 * the validator still rejects them.
 */
public final class JsonSchemaNormalizer {

    private JsonSchemaNormalizer() {
    }

    public static JsonNode normalize(JsonNode schema, JsonNode value) {
        if (schema == null || value == null || value.isNull()) {
            return value;
        }
        if (value.isObject() && schema.has("properties")) {
            ObjectNode source = (ObjectNode) value;
            ObjectNode result = JsonNodeFactory.instance.objectNode();
            JsonNode properties = schema.get("properties");
            Iterator<String> names = properties.fieldNames();
            while (names.hasNext()) {
                String name = names.next();
                JsonNode propertySchema = properties.get(name);
                if (source.has(name)) {
                    result.set(name, normalize(propertySchema, source.get(name)));
                } else if (allowsNull(propertySchema)) {
                    result.putNull(name);
                }
            }
            JsonNode additional = schema.get("additionalProperties");
            boolean closed = additional != null && additional.isBoolean() && !additional.asBoolean();
            if (!closed) {
                source.fields().forEachRemaining(field -> {
                    if (!result.has(field.getKey())) {
                        result.set(field.getKey(), field.getValue());
                    }
                });
            }
            return result;
        }
        if (value.isArray() && schema.has("items")) {
            int max = schema.has("maxItems") ? schema.get("maxItems").asInt() : Integer.MAX_VALUE;
            List<JsonNode> items = new ArrayList<>();
            for (JsonNode item : value) {
                if (items.size() >= max) {
                    break;
                }
                items.add(normalize(schema.get("items"), item));
            }
            ArrayNode result = JsonNodeFactory.instance.arrayNode();
            items.forEach(result::add);
            return result;
        }
        if (value.isTextual() && schema.has("maxLength")) {
            int max = schema.get("maxLength").asInt();
            String text = value.asText();
            return text.length() > max ? TextNode.valueOf(text.substring(0, max)) : value;
        }
        return value;
    }

    private static boolean allowsNull(JsonNode schema) {
        JsonNode type = schema.get("type");
        if (type == null) {
            return false;
        }
        if (type.isTextual()) {
            return "null".equals(type.asText());
        }
        for (JsonNode option : type) {
            if ("null".equals(option.asText())) {
                return true;
            }
        }
        return false;
    }
}
