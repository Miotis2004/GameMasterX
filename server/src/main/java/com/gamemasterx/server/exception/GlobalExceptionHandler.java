package com.gamemasterx.server.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.http.converter.HttpMessageNotReadableException;

import com.gamemasterx.server.adventure.AdventureImportConflictException;
import com.gamemasterx.server.character.CharacterSheetValidationException;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@ControllerAdvice
@Order(1)
public class GlobalExceptionHandler {

    private static final String CORRELATION_ID_ATTRIBUTE = "correlationId";

    private String getCorrelationId(HttpServletRequest request) {
        Object attr = request.getAttribute(CORRELATION_ID_ATTRIBUTE);
        if (attr instanceof String s && !s.isBlank()) {
            return s;
        }
        return UUID.randomUUID().toString();
    }

    private ErrorResponse buildErrorResponse(String errorCode, String message, String correlationId, List<com.gamemasterx.server.exception.FieldError> fieldErrors) {
        ErrorResponse response = new ErrorResponse();
        response.setErrorCode(errorCode);
        response.setMessage(message);
        response.setCorrelationId(correlationId);
        response.setFieldErrors(fieldErrors);
        return response;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String correlationId = getCorrelationId(request);
        List<com.gamemasterx.server.exception.FieldError> fieldErrors = new ArrayList<>();
        for (org.springframework.validation.FieldError fe : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.add(new com.gamemasterx.server.exception.FieldError(fe.getField(), fe.getDefaultMessage()));
        }
        ErrorResponse errorResponse = buildErrorResponse("VALIDATION_ERROR", "Validation failed for request input", correlationId, fieldErrors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(IllegalArgumentException ex, HttpServletRequest request) {
        String correlationId = getCorrelationId(request);
        ErrorResponse errorResponse = buildErrorResponse("BAD_REQUEST", "Invalid request parameters", correlationId, null);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    /**
     * Maps a deterministic character-sheet validation failure to a consistent
     * {@code 400 BAD_REQUEST} response. When the validation failure is tied to a
     * specific field (for example {@code abilityScores.strength}) the offending
     * field is included in the {@code fieldErrors} list to match the shape used
     * by bean-validation failures.
     */
    @ExceptionHandler(AdventureImportConflictException.class)
    public ResponseEntity<ErrorResponse> handleImportConflict(AdventureImportConflictException ex, HttpServletRequest request) {
        String correlationId = getCorrelationId(request);
        List<com.gamemasterx.server.exception.FieldError> fieldErrors = new ArrayList<>();
        String identifier = ex.getIdentifier();
        if (identifier != null && !identifier.isBlank()) {
            fieldErrors.add(new com.gamemasterx.server.exception.FieldError("id", "Adventure id already exists: " + identifier));
        }
        ErrorResponse errorResponse = buildErrorResponse(
                "CONFLICT", ex.getMessage() == null ? "Adventure already exists" : ex.getMessage(), correlationId, fieldErrors);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(errorResponse);
    }

    @ExceptionHandler(CharacterSheetValidationException.class)
    public ResponseEntity<ErrorResponse> handleCharacterSheetValidationException(CharacterSheetValidationException ex, HttpServletRequest request) {
        String correlationId = getCorrelationId(request);
        List<com.gamemasterx.server.exception.FieldError> fieldErrors = new ArrayList<>();
        String field = ex.getField();
        if (field != null && !field.isBlank()) {
            fieldErrors.add(new com.gamemasterx.server.exception.FieldError(field, ex.getMessage()));
        }
        ErrorResponse errorResponse = buildErrorResponse(
                "VALIDATION_ERROR", ex.getMessage() == null ? "Character sheet validation failed" : ex.getMessage(),
                correlationId, fieldErrors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    /**
     * Maps an authorization denial (a missing or insufficient
     * {@link MembershipRole}) to a consistent {@code 403 Forbidden} response.
     * This handler must be declared before the generic {@link Exception}
     * handler below so that authorization failures are reported with the
     * {@code FORBIDDEN} error code rather than a generic internal error.
     */
    @ExceptionHandler(AuthorizationException.class)
    public ResponseEntity<ErrorResponse> handleAuthorizationException(AuthorizationException ex, HttpServletRequest request) {
        String correlationId = getCorrelationId(request);
        String roleSuffix = ex.getRequiredRole() != null
                ? ": requires " + ex.getRequiredRole().name() + " role"
                : "";
        ErrorResponse errorResponse = buildErrorResponse(
                "FORBIDDEN", ex.getMessage() + roleSuffix, correlationId, null);
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorResponse);
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoHandlerFound(NoHandlerFoundException ex, HttpServletRequest request) {
        String correlationId = getCorrelationId(request);
        ErrorResponse errorResponse = buildErrorResponse("NOT_FOUND", "The requested resource was not found", correlationId, null);
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleHttpMessageNotReadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
        String correlationId = getCorrelationId(request);
        ErrorResponse errorResponse = buildErrorResponse("BAD_REQUEST", "Malformed JSON request body", correlationId, null);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParameter(MissingServletRequestParameterException ex, HttpServletRequest request) {
        String correlationId = getCorrelationId(request);
        ErrorResponse errorResponse = buildErrorResponse("BAD_REQUEST", "Required request parameter is missing", correlationId, null);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex, HttpServletRequest request) {
        String correlationId = getCorrelationId(request);
        ErrorResponse errorResponse = buildErrorResponse("INTERNAL_ERROR", "An unexpected error occurred", correlationId, null);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }
}
