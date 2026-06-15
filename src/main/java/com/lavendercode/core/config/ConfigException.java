package com.lavendercode.core.config;

public class ConfigException extends RuntimeException {
    private final String fieldName;

    public ConfigException(String message, String fieldName) {
        super(message);
        this.fieldName = fieldName;
    }

    public String getFieldName() {
        return fieldName;
    }
}
