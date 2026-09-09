package com.gamemasterx.server.adventure.controller;

import com.gamemasterx.server.adventure.AdventureImportConflictException;
import com.gamemasterx.server.adventure.AdventurePackageImportException;
import com.gamemasterx.server.adventure.model.Adventure;
import com.gamemasterx.server.adventure.model.AdventureDto;
import com.gamemasterx.server.adventure.service.AdventureImporter;
import com.gamemasterx.server.adventure.repository.AdventureRepository;
import com.gamemasterx.server.exception.AuthorizationException;
import com.gamemasterx.server.exception.ErrorResponse;
import com.gamemasterx.server.exception.FieldError;
import com.gamemasterx.server.filter.AuthFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Import of local adventure packages.
 *
 * <p>An adventure package is uploaded as multipart form-data and imported through
 * {@link AdventureImporter}. The importer validates the package structure and
 * identifiers, protects against path traversal while staging the archive,
 * rejects oversized input, and refuses to silently overwrite an existing
 * adventure. Each failure mode is mapped to a consistent API error response.</p>
 */
@RestController
@RequestMapping("/api/adventures")
public class AdventureController {

    private static final String CORRELATION_ID_ATTRIBUTE = "correlationId";

    private final AdventureImporter adventureImporter;
    private final AdventureRepository adventureRepository;

    public AdventureController(AdventureImporter adventureImporter,
                               AdventureRepository adventureRepository) {
        this.adventureImporter = adventureImporter;
        this.adventureRepository = adventureRepository;
    }

    /**
     * Imports a local adventure package (a ZIP archive containing an
     * {@adventure.json} manifest). The import is create-only by default; supply
     * {@code overwrite=true} to replace an existing adventure with the same id.
     *
     * <p>Role requirement: the caller must be authenticated.</p>
     */
    @PostMapping(value = "/import")
    public ResponseEntity<AdventureDto> importAdventure(
            @RequestParam("package") MultipartFile packageFile,
            @RequestParam(value = "overwrite", required = false, defaultValue = "false") boolean overwrite,
            HttpServletRequest httpRequest) {
        requireActor(httpRequest);
        if (packageFile == null || packageFile.isEmpty()) {
            throw new AdventurePackageImportException("package", "No package file was supplied for import");
        }

        Path staged = uploadToStaging(packageFile);
        try {
            AdventureDto imported = adventureImporter.importAdventure(staged, overwrite);
            return new ResponseEntity<>(imported, HttpStatus.CREATED);
        } finally {
            deleteQuietly(staged);
        }
    }

    /**
     * Lists every adventure known to the repository. The caller must be
     * authenticated. Used by the adventure library screen.
     */
    @GetMapping
    public ResponseEntity<List<AdventureDto>> listAdventures(HttpServletRequest httpRequest) {
        requireActor(httpRequest);
        List<AdventureDto> dtoList = new ArrayList<>();
        for (Adventure adventure : adventureRepository.findAll()) {
            dtoList.add(AdventureDto.from(adventure));
        }
        return ResponseEntity.ok(dtoList);
    }

    /**
     * Reads a single adventure by its stable identifier. The caller must be
     * authenticated. A missing identifier yields a {@code 404 Not Found}.
     */
    @GetMapping("/{id}")
    public ResponseEntity<AdventureDto> getAdventure(@PathVariable String id,
                                                     HttpServletRequest httpRequest) {
        requireActor(httpRequest);
        Optional<Adventure> adventure = adventureRepository.findById(id);
        if (adventure.isPresent()) {
            return ResponseEntity.ok(AdventureDto.from(adventure.get()));
        }
        throw new IllegalArgumentException("No adventure found with id: " + id);
    }

    private Path uploadToStaging(MultipartFile packageFile) {
        try {
            Path target = Files.createTempFile("adventure-import-", ".zip");
            packageFile.transferTo(target);
            return target;
        } catch (IOException e) {
            throw new AdventurePackageImportException(
                    "package", "Unable to stage uploaded package: " + e.getMessage());
        }
    }

    private void deleteQuietly(Path path) {
        if (path != null) {
            try {
                Files.deleteIfExists(path);
            } catch (IOException ignored) {
                // best-effort cleanup; nothing further to do
            }
        }
    }

    private String requireActor(HttpServletRequest request) {
        Object attr = request.getAttribute(AuthFilter.AUTH_USER_ATTR);
        if (attr instanceof String s && !s.isBlank()) {
            return s;
        }
        throw new AuthorizationException(
                com.gamemasterx.server.campaign.membership.model.MembershipRole.OBSERVER,
                "Authentication required to import an adventure");
    }

    /**
     * Maps structural / identifier / path / size / parse failures to a
     * {@code 400 BAD_REQUEST} response with the offending package location when
     * one is known.
     */
    @ExceptionHandler(AdventurePackageImportException.class)
    @Order(3)
    public ResponseEntity<ErrorResponse> handleImportValidation(AdventurePackageImportException ex,
                                                              HttpServletRequest request) {
        List<FieldError> fieldErrors = new ArrayList<>();
        String location = ex.getLocation();
        if (location != null && !location.isBlank()) {
            fieldErrors.add(new FieldError(location, ex.getMessage()));
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("VALIDATION_ERROR",
                        ex.getMessage() == null ? "Adventure package import validation failed" : ex.getMessage(),
                        getCorrelationId(request), fieldErrors));
    }

    /**
     * Resolves the service's {@code IllegalArgumentException} for a missing
     * adventure to a {@code 404 Not Found} response in the consistent API error
     * format. This controller-level handler takes precedence over any global
     * handler for exceptions thrown within this controller.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    @Order(2)
    public ResponseEntity<ErrorResponse> handleNotFound(IllegalArgumentException ex,
                                                        HttpServletRequest request) {
        List<FieldError> fieldErrors = new ArrayList<>();
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("NOT_FOUND",
                        ex.getMessage() == null ? "The requested adventure was not found" : ex.getMessage(),
                        getCorrelationId(request), fieldErrors));
    }

    /**
     * Maps an identifier collision during import to a {@code 409 Conflict}
     * response, so callers can distinguish "already exists" from a malformed
     * package.
     */
    @ExceptionHandler(AdventureImportConflictException.class)
    @Order(3)
    public ResponseEntity<ErrorResponse> handleImportConflict(AdventureImportConflictException ex,
                                                              HttpServletRequest request) {
        List<FieldError> fieldErrors = new ArrayList<>();
        String identifier = ex.getIdentifier();
        if (identifier != null && !identifier.isBlank()) {
            fieldErrors.add(new FieldError("id", "Adventure id already exists: " + identifier));
        }
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("CONFLICT",
                        ex.getMessage() == null ? "Adventure already exists" : ex.getMessage(),
                        getCorrelationId(request), fieldErrors));
    }

    private static String getCorrelationId(HttpServletRequest request) {
        Object attr = request.getAttribute(CORRELATION_ID_ATTRIBUTE);
        if (attr instanceof String s && !s.isBlank()) {
            return s;
        }
        return UUID.randomUUID().toString();
    }
}
