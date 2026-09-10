package com.gamemasterx.server.gameplay.service;

import com.gamemasterx.server.encounter.model.Encounter.Position;
import com.gamemasterx.server.gameplay.MovementRangeValidationException;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * Application service that owns the deterministic computation of grid distance
 * and the validation of two first-class combat concerns:
 *
 * <ul>
 *   <li><b>Movement distance</b> &ndash; how far a participant may move on its
 *       turn is bounded by its available movement. The distance travelled
 *       between the starting and ending grid squares must not exceed the
 *       participant's configured movement.</li>
 *   <li><b>Range</b> &ndash; an attack or effect can only reach a target that is
 *       within the attacker's reach (a maximum distance). The distance between
 *       the attacker and the target must fall within that reach.</li>
 * </ul>
 *
 * <p>This service is the single authority for movement-distance and range
 * validation. Every validation is a pure function of the two positions and the
 * movement/reach bound, so the same inputs always yield the same verdict. A
 * passing validation returns an immutable {@link ResolvedMovement} or
 * {@link ResolvedRange} carrying the computed distance, so the caller can record
 * it as an auditable accepted mutation. A failing validation throws an
 * {@link IllegalArgumentException} whose message is the clear diagnostic that
 * maps to a {@code 400 BAD_REQUEST}, so an out-of-range movement or unreachable
 * target is always rejected rather than applied.</p>
 */
@Service
public final class MovementRangeService {

    private MovementRangeService() {
    }

    /**
     * Computes the Manhattan (grid/orthogonal) distance between two squares.
     * This is the canonical measure for how many squares a participant must
     * traverse, so it is used to validate movement distance.
     *
     * @param from the starting square (must not be {@code null})
     * @param to   the ending square (must not be {@code null})
     * @return the Manhattan distance, a non-negative integer
     */
    public static int manhattan(Position from, Position to) {
        return Math.abs(from.getX() - to.getX()) + Math.abs(from.getY() - to.getY());
    }

    /**
     * Computes the Chebyshev (king-move) distance between two squares, in which
     * a diagonal step counts as a single square. This is the canonical measure
     * for reach, so it is used to validate attack and effect range.
     *
     * @param from the origin square (must not be {@code null})
     * @param to   the target square (must not be {@code null})
     * @return the Chebyshev distance, a non-negative integer
     */
    public static int chebyshev(Position from, Position to) {
        return Math.max(
                Math.abs(from.getX() - to.getX()),
                Math.abs(from.getY() - to.getY()));
    }

    /**
     * Validates that moving from {@code from} to {@code to} is within
     * {@code availableMovement}, returning the resolved movement when it is.
     *
     * <p>The Manhattan distance between the two squares must be less than or
     * equal to the actor's available movement. When the distance exceeds the
     * available movement the movement is rejected with a diagnostic that names
     * both squares, the distance travelled and the available movement.</p>
     *
     * @param from              the starting square (must not be {@code null})
     * @param to                the ending square (must not be {@code null})
     * @param availableMovement the actor's available movement in squares
     * @return the resolved movement, carrying the computed distance
     * @throws IllegalArgumentException when either square is missing, the
     *                                  available movement is negative, or the
     *                                  Manhattan distance exceeds the available
     *                                  movement
     */
    public ResolvedMovement resolveMovement(Position from, Position to, int availableMovement) {
        Objects.requireNonNull(from, "A starting position is required to resolve movement");
        Objects.requireNonNull(to, "A destination position is required to resolve movement");
        if (availableMovement < 0) {
            throw new IllegalArgumentException("Available movement must not be negative");
        }

        int distance = manhattan(from, to);
        boolean withinRange = distance <= availableMovement;
        if (!withinRange) {
            throw new MovementRangeValidationException(describeMovementOverflow(from, to, distance, availableMovement));
        }

        return new ResolvedMovement(from, to, distance, availableMovement, true);
    }

    /**
     * Validates that a target at {@code to} is within {@code reach} of the
     * attacker at {@code from}, returning the resolved range when it is.
     *
     * <p>The Chebyshev distance between the two squares must be less than or
     * equal to the attacker's reach. When the distance exceeds the reach the
     * target is out of range and the attack or effect is rejected with a
     * diagnostic that names both squares, the distance and the reach.</p>
     *
     * @param from  the attacker's square (must not be {@code null})
     * @param to    the target's square (must not be {@code null})
     * @param reach the attacker's reach in squares (must be non-negative)
     * @return the resolved range, carrying the computed distance
     * @throws IllegalArgumentException when either square is missing, the reach
     *                                  is negative, or the Chebyshev distance
     *                                  exceeds the reach
     */
    public ResolvedRange resolveReach(Position from, Position to, int reach) {
        Objects.requireNonNull(from, "An origin position is required to resolve reach");
        Objects.requireNonNull(to, "A target position is required to resolve reach");
        if (reach < 0) {
            throw new IllegalArgumentException("Reach must not be negative");
        }

        int distance = chebyshev(from, to);
        boolean inRange = distance <= reach;
        if (!inRange) {
            throw new MovementRangeValidationException(describeReachOverflow(from, to, distance, reach));
        }

        return new ResolvedRange(from, to, distance, reach, true);
    }

    private static String describeMovementOverflow(Position from, Position to, int distance, int availableMovement) {
        return "Participant cannot move from (" + from.getX() + "," + from.getY() + ") to ("
                + to.getX() + "," + to.getY() + "): distance " + distance
                + " exceeds available movement " + availableMovement;
    }

    private static String describeReachOverflow(Position from, Position to, int distance, int reach) {
        return "Target out of reach: distance " + distance
                + " between (" + from.getX() + "," + from.getY() + ") and ("
                + to.getX() + "," + to.getY() + ") exceeds reach " + reach;
    }

    /**
     * The auditable outcome of a validated movement.
     *
     * @param from              the starting square
     * @param to                the ending square
     * @param distance          the Manhattan distance travelled, in squares
     * @param availableMovement the actor's available movement, in squares
     * @param withinRange       always {@code true}; a {@code ResolvedMovement}
     *                          is only produced for a valid movement
     */
    public record ResolvedMovement(Position from, Position to, int distance,
                                   int availableMovement, boolean withinRange) {
    }

    /**
     * The auditable outcome of a validated range check.
     *
     * @param from     the attacker's square
     * @param to       the target's square
     * @param distance the Chebyshev distance, in squares
     * @param reach    the attacker's reach, in squares
     * @param inRange  always {@code true}; a {@code ResolvedRange} is only
     *                 produced for an in-range target
     */
    public record ResolvedRange(Position from, Position to, int distance,
                                int reach, boolean inRange) {
    }
}
