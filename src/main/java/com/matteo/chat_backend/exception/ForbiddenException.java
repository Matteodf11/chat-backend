package com.matteo.chat_backend.exception;

public class ForbiddenException extends RuntimeException {
    private final String action;
    private final String resourceType;
    private final String resourceId;

    public ForbiddenException(String action, String resourceType, String resourceId) {
        super("No tienes permiso para " + action + " " + resourceType + " " + resourceId);
        this.action = action;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
    }

    public ForbiddenException(String message) {
        super(message);
        this.action = "";
        this.resourceType = "";
        this.resourceId = "";
    }

    public String getAction() { return action; }
    public String getResourceType() { return resourceType; }
    public String getResourceId() { return resourceId; }
}