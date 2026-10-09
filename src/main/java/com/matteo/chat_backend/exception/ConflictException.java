package com.matteo.chat_backend.exception;

public class ConflictException extends RuntimeException {
    private final String resourceType;
    private final String field;
    private final String value;

    public ConflictException(String resourceType, String field, String value) {
        super(resourceType + " ya existe con " + field + ": " + value);
        this.resourceType = resourceType;
        this.field = field;
        this.value = value;
    }

    public ConflictException(String message) {
        super(message);
        this.resourceType = "";
        this.field = "";
        this.value = "";
    }

    public String getResourceType() { return resourceType; }
    public String getField() { return field; }
    public String getValue() { return value; }
}