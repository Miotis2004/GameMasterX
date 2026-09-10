package com.gamemasterx.server.exception;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public class ErrorResponse {
    private String errorCode;
    private String message;
    private String correlationId;
    private String timestamp;
    private List<FieldError> fieldErrors;
    private Map<String, Object> diagnostics;

    public ErrorResponse() {
        this.timestamp = Instant.now().toString();
    }

    public ErrorResponse(String errorCode, String message, String correlationId, List<FieldError> fieldErrors) {
        this.errorCode = errorCode;
        this.message = message;
        this.correlationId = correlationId;
        this.timestamp = Instant.now().toString();
        this.fieldErrors = fieldErrors;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public List<FieldError> getFieldErrors() {
        return fieldErrors;
    }

    public void setFieldErrors(List<FieldError> fieldErrors) {
        this.fieldErrors = fieldErrors;
    }

    public Map<String, Object> getDiagnostics() {
        return diagnostics;
    }

    public void setDiagnostics(Map<String, Object> diagnostics) {
        this.diagnostics = diagnostics;
    }
}
