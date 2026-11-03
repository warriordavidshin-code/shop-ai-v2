package com.petitcamel.shop.ai.support;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builds JSON schemas that satisfy OpenAI strict structured output: every object lists all of its
 * properties as required and forbids additional properties; optional values are expressed as nullable types.
 */
public final class StrictSchema {

    private static final JsonNodeFactory F = JsonNodeFactory.instance;

    private StrictSchema() {
    }

    public static ObjectNode string(String description) {
        return typed("string", description);
    }

    public static ObjectNode nullableString(String description) {
        return nullable("string", description);
    }

    public static ObjectNode nullableInteger(String description) {
        return nullable("integer", description);
    }

    public static ObjectNode number(String description) {
        return typed("number", description);
    }

    public static ObjectNode bool(String description) {
        return typed("boolean", description);
    }

    public static ObjectNode integer(String description) {
        return typed("integer", description);
    }

    public static ObjectNode enumOf(String description, String... values) {
        ObjectNode node = typed("string", description);
        ArrayNode options = node.putArray("enum");
        for (String value : values) {
            options.add(value);
        }
        return node;
    }

    public static ObjectNode nullableEnumOf(String description, String... values) {
        ObjectNode node = nullable("string", description);
        ArrayNode options = node.putArray("enum");
        for (String value : values) {
            options.add(value);
        }
        options.addNull();
        return node;
    }

    public static ObjectNode array(String description, ObjectNode items) {
        ObjectNode node = typed("array", description);
        node.set("items", items);
        return node;
    }

    public static Builder object() {
        return new Builder();
    }

    private static ObjectNode typed(String type, String description) {
        ObjectNode node = F.objectNode();
        node.put("type", type);
        if (description != null) {
            node.put("description", description);
        }
        return node;
    }

    private static ObjectNode nullable(String type, String description) {
        ObjectNode node = F.objectNode();
        node.putArray("type").add(type).add("null");
        if (description != null) {
            node.put("description", description);
        }
        return node;
    }

    public static final class Builder {
        private final Map<String, ObjectNode> properties = new LinkedHashMap<>();

        public Builder prop(String name, ObjectNode schema) {
            properties.put(name, schema);
            return this;
        }

        public ObjectNode build() {
            ObjectNode node = F.objectNode();
            node.put("type", "object");
            ObjectNode props = node.putObject("properties");
            ArrayNode required = node.putArray("required");
            properties.forEach((name, schema) -> {
                props.set(name, schema);
                required.add(name);
            });
            node.put("additionalProperties", false);
            return node;
        }
    }
}
