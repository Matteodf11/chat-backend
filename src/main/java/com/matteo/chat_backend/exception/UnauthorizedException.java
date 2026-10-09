package com.matteo.chat_backend.exception;

public class UnauthorizedException extends RuntimeException {
    private final String reason;

    public UnauthorizedException(String reason) {
        super("No autorizado: " + reason);
        this.reason = reason;
    }

    public UnauthorizedException() {
        this("Token inválido o expirado");
    }

    public String getReason() { return reason; }
}