package com.gamemasterx.server.ai.operation;

import com.gamemasterx.server.exception.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Read-only, backend-owned description of the versioned proposed-operation
 * schemas.
 *
 * <p>The schema <em>definitions</em> live exclusively on the backend and are
 * never exposed to the browser as editable or mutable inputs. This controller
 * publishes only a minimal, read-only catalogue of the supported schema
 * versions and their {@link OperationType}s so a client can discover which
 * versions it may reference. It intentionally exposes no endpoint that can
 * create, edit, remove or reorder schemas: the catalogue is fixed by the
 * {@link OperationSchemaRegistry} at startup and cannot be changed through the
 * API.</p>
 */
@RestController
@RequestMapping("/api/ai/operation-schemas")
public class OperationSchemaController {

    private static final String CORRELATION_ID_ATTRIBUTE = "correlationId";

    private final OperationSchemaRegistry registry;

    /**
     * @param registry the backend registry of versioned schemas
     */
    public OperationSchemaController(OperationSchemaRegistry registry) {
        this.registry = registry;
    }

    /**
     * Returns the read-only catalogue of supported schema versions.
     *
     * @return the catalogue, listing each version and its supported operation
     *         types; never mutable through this API
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> schemas() {
        List<Object> versions = new ArrayList<>();
        for (OperationSchemaVersion version : registry.supportedVersions()) {
            OperationSchema schema = registry.resolve(version);
            versions.add(Map.of(
                    "version", version.wire(),
                    "operationTypes", List.copyOf(schema.supportedOperationTypes())));
        }
        return ResponseEntity.ok(Map.of(
                "versions", versions,
                "note", "Schema definitions are backend-owned and cannot be edited through this API."));
    }

    @ExceptionHandler(OperationSchemaException.class)
    @Order(2)
    public ResponseEntity<ErrorResponse> handleOperationSchemaException(
            OperationSchemaException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(buildBadRequest(ex.getMessage(), request));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @Order(3)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(
            IllegalArgumentException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(buildBadRequest(ex.getMessage(), request));
    }

    private static ErrorResponse buildBadRequest(String message, HttpServletRequest request) {
        return new ErrorResponse("BAD_REQUEST",
                message == null ? "The operation schema request was invalid" : message,
                getCorrelationId(request), List.of());
    }

    private static String getCorrelationId(HttpServletRequest request) {
        Object attr = request.getAttribute(CORRELATION_ID_ATTRIBUTE);
        if (attr instanceof String s && !s.isBlank()) {
            return s;
        }
        return UUID.randomUUID().toString();
    }
}
