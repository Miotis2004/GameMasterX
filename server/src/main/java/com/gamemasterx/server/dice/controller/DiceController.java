package com.gamemasterx.server.dice.controller;

import com.gamemasterx.server.campaign.membership.model.MembershipRole;
import com.gamemasterx.server.dice.DiceExpression;
import com.gamemasterx.server.dice.DiceExpressionException;
import com.gamemasterx.server.dice.DiceRollRequest;
import com.gamemasterx.server.dice.DiceRollResponse;
import com.gamemasterx.server.dice.DiceRoller;
import com.gamemasterx.server.dice.RollMode;
import com.gamemasterx.server.exception.AuthorizationException;
import com.gamemasterx.server.exception.ErrorResponse;
import com.gamemasterx.server.filter.AuthFilter;
import com.gamemasterx.server.gameplay.model.DiceResult;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * REST controller exposing auditable, seeded-deterministic dice rolling.
 *
 * <p>All endpoints run on the backend API port (5172). A roll resolves a parsed
 * {@link DiceExpression} into one auditable {@link DiceResult} per group via the
 * {@link DiceRoller}. The {@link DiceRollRequest#mode()} selects between
 * unpredictable {@link RollMode#RANDOM} rolls (seeded deterministically from a
 * {@link java.security.SecureRandom}) and reproducible
 * {@link RollMode#SEEDED} rolls (seeded deterministically from a caller-supplied
 * seed). Every result records the individual die values that were drawn, so any
 * roll can be verified and, in seeded mode, replayed.</p>
 *
 * <p>The roll requires an authenticated actor (see {@link #requireActor(HttpServletRequest)}),
 * matching the role-gated conventions of the rest of the API.</p>
 */
@RestController
@RequestMapping("/api/dice")
public class DiceController {

    private static final String CORRELATION_ID_ATTRIBUTE = "correlationId";

    private final DiceRoller diceRoller;

    public DiceController(DiceRoller diceRoller) {
        this.diceRoller = diceRoller;
    }

    /**
     * Rolls a dice expression and returns auditable, structured results.
     *
     * <p>Supported request body (see {@link DiceRollRequest}):</p>
     * <ul>
     *   <li>{@code expression} &ndash; the expression to resolve, e.g.
     *       {@code "2d6+3"} or {@code "1d20dis"}.</li>
     *   <li>{@code mode} &ndash; {@code "random"} (default) or
     *       {@code "seeded"}.</li>
     *   <li>{@code seed} &ndash; required when {@code mode} is {@code "seeded"};
     *       the seed derives the deterministic generator.</li>
     *   <li>{@code label} &ndash; optional human-readable label.</li>
     * </ul>
     *
     * <p>When {@code mode} is {@code "seeded"} and {@code seed} is missing or
     * blank, the request is rejected with a {@code 400 VALIDATION_ERROR}. When
     * the {@code expression} is malformed it is likewise rejected with a
     * {@code 400 VALIDATION_ERROR}.</p>
     *
     * @param request the roll request body
     * @param httpRequest the incoming request, used for the authenticated actor
     * @return the 200 response carrying the {@link DiceRollResponse}
     * @throws AuthorizationException when the caller is not authenticated
     */
    @PostMapping("/roll")
    public ResponseEntity<DiceRollResponse> roll(@RequestBody DiceRollRequest request,
                                                 HttpServletRequest httpRequest) {
        requireActor(httpRequest);
        RollMode mode = resolveMode(request);
        DiceExpression expression = DiceExpression.parse(request.expression());
        DiceRoller roller = new DiceRoller(mode, request.seed());

        List<DiceResult> results = roller.roll(expression, request.label());
        DiceRollResponse response = DiceRollResponse.of(roller, request.expression(), results);

        return ResponseEntity.ok(response);
    }

    /**
     * Resolves the requested {@link RollMode}, defaulting to
     * {@link RollMode#RANDOM} when {@link DiceRollRequest#mode()} is omitted.
     *
     * @param request the roll request
     * @return the effective {@link RollMode}
     * @throws DiceExpressionException when the mode is present but unrecognized
     */
    private RollMode resolveMode(DiceRollRequest request) {
        String mode = request.mode();
        if (mode == null || mode.isBlank()) {
            return RollMode.RANDOM;
        }
        return RollMode.fromWire(mode);
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
                "Authentication required to roll dice");
    }

    /**
     * Maps a malformed {@link DiceExpression} to a {@code 400 VALIDATION_ERROR}
     * response tagged with the offending field. This controller-level handler
     * takes precedence over the global handler.
     */
    @ExceptionHandler(DiceExpressionException.class)
    @Order(2)
    public ResponseEntity<ErrorResponse> handleDiceExpressionException(DiceExpressionException ex,
                                                                        HttpServletRequest request) {
        String correlationId = getCorrelationId(request);
        List<com.gamemasterx.server.exception.FieldError> fieldErrors = new java.util.ArrayList<>();
        String field = ex.getField();
        if (field != null && !field.isBlank()) {
            fieldErrors.add(new com.gamemasterx.server.exception.FieldError(field, ex.getMessage()));
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("VALIDATION_ERROR",
                        ex.getMessage() == null ? "Dice expression is invalid" : ex.getMessage(),
                        correlationId, fieldErrors));
    }

    /**
     * Maps a generic {@link IllegalArgumentException} (for example a missing
     * seed for a seeded roll) to a {@code 400 BAD_REQUEST} response.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    @Order(3)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(IllegalArgumentException ex,
                                                                        HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(buildBadRequest(ex.getMessage(), request));
    }

    private static String getCorrelationId(HttpServletRequest request) {
        Object attr = request.getAttribute(CORRELATION_ID_ATTRIBUTE);
        if (attr instanceof String s && !s.isBlank()) {
            return s;
        }
        return UUID.randomUUID().toString();
    }

    private static ErrorResponse buildBadRequest(String message, HttpServletRequest request) {
        List<com.gamemasterx.server.exception.FieldError> fieldErrors = new java.util.ArrayList<>();
        return new ErrorResponse("BAD_REQUEST",
                message == null ? "The dice roll request was invalid" : message,
                getCorrelationId(request), fieldErrors);
    }
}
