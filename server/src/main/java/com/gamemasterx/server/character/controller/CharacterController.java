package com.gamemasterx.server.character.controller;

import com.gamemasterx.server.character.model.CharacterDto;
import com.gamemasterx.server.character.model.CharacterSheetCreateRequest;
import com.gamemasterx.server.character.model.CharacterSheetUpdateRequest;
import com.gamemasterx.server.character.service.CharacterService;
import com.gamemasterx.server.campaign.membership.model.MembershipRole;
import com.gamemasterx.server.exception.AuthorizationException;
import com.gamemasterx.server.exception.ErrorResponse;
import com.gamemasterx.server.filter.AuthFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/characters")
public class CharacterController {

    private static final String CORRELATION_ID_ATTRIBUTE = "correlationId";

    private final CharacterService characterService;

    public CharacterController(CharacterService characterService) {
        this.characterService = characterService;
    }

    /**
     * Creates a new character sheet. Role requirement: the caller must be
     * authenticated (and is recorded as the character owner).
     */
    @PostMapping
    public ResponseEntity<CharacterDto> createCharacter(@Valid @RequestBody CharacterSheetCreateRequest request,
                                                        HttpServletRequest httpRequest) {
        CharacterDto created = characterService.createCharacter(request, requireActor(httpRequest));
        return new ResponseEntity<>(created, HttpStatus.CREATED);
    }

    /**
     * Lists the characters owned by the authenticated caller. Role requirement:
     * the caller must be authenticated.
     */
    @GetMapping
    public ResponseEntity<List<CharacterDto>> listCharacters(HttpServletRequest httpRequest) {
        return ResponseEntity.ok(characterService.findByOwner(requireActor(httpRequest)));
    }

    /**
     * Reads a single character sheet. Role requirement: the caller must be
     * authenticated and own the character.
     */
    @GetMapping("/{id}")
    public ResponseEntity<CharacterDto> getCharacter(@PathVariable String id,
                                                     HttpServletRequest httpRequest) {
        return ResponseEntity.ok(characterService.findById(id, requireActor(httpRequest)));
    }

    /**
     * Updates a character sheet. Role requirement: the caller must be
     * authenticated and own the character.
     */
    @PutMapping("/{id}")
    public ResponseEntity<CharacterDto> updateCharacter(@PathVariable String id,
                                                        @Valid @RequestBody CharacterSheetUpdateRequest request,
                                                        HttpServletRequest httpRequest) {
        return ResponseEntity.ok(characterService.updateCharacter(id, requireActor(httpRequest), request));
    }

    /**
     * Resolves the authenticated actor from the request context established by
     * the auth filter, or throws an authorization denial when the caller is not
     * authenticated.
     */
    private String requireActor(HttpServletRequest request) {
        Object attr = request.getAttribute(AuthFilter.AUTH_USER_ATTR);
        if (attr instanceof String s && !s.isBlank()) {
            return s;
        }
        throw new AuthorizationException(MembershipRole.OBSERVER,
                "Authentication required to manage a character");
    }

    /**
     * Maps the service's {@code IllegalArgumentException} for a missing character
     * to a {@code 404 Not Found} response in the consistent API error format.
     * This controller-level handler takes precedence over the global handler for
     * exceptions thrown within this controller.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    @Order(2)
    public ResponseEntity<ErrorResponse> handleNotFound(IllegalArgumentException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(buildNotFound(ex.getMessage(), request));
    }

    private static String getCorrelationId(HttpServletRequest request) {
        Object attr = request.getAttribute(CORRELATION_ID_ATTRIBUTE);
        if (attr instanceof String s && !s.isBlank()) {
            return s;
        }
        return UUID.randomUUID().toString();
    }

    private static ErrorResponse buildNotFound(String message, HttpServletRequest request) {
        List<com.gamemasterx.server.exception.FieldError> fieldErrors = new ArrayList<>();
        return new ErrorResponse("NOT_FOUND",
                message == null ? "The requested character was not found" : message,
                getCorrelationId(request), fieldErrors);
    }
}
