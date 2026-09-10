package com.gamemasterx.server.inventory.service;

import com.gamemasterx.server.inventory.model.ConsumableResource;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Application service that owns the deterministic rules governing inventory
 * quantity and consumable-resource changes.
 *
 * <p>This service is the single authority for two kinds of change:</p>
 *
 * <ol>
 *   <li><b>Inventory quantity decreases</b> &ndash; {@link #resolveDecrease(DecreaseInput)}.
 *       A quantity is decremented against the quantity currently on hand and is
 *       <b>never</b> allowed to go negative: a request that would drive the
 *       quantity below {@code 0} is rejected with a clear shortfall diagnostic
 *       and the quantity is left unchanged.</li>
 *   <li><b>Consumable-resource consumption</b> &ndash; {@link #resolveConsumption(ConsumptionInput)}.
 *       A consumable-resource change is applied <b>only when every requirement
 *       is met</b> and enough of the resource is available; otherwise the change
 *       is rejected with the unmet requirements and nothing is applied.</li>
 * </ol>
 *
 * <p>Every method here is a pure, deterministic function of its inputs: it
 * never touches storage and never mutates the state it is given. The caller
 * ({@link InventoryService}) resolves the change through this service, applies
 * the accepted result atomically, and records it in the immutable audit trail
 * with the revision before and after.</p>
 *
 * <p>Negative quantities are prevented entirely by this service: a resolved
 * {@link ResolvedDecrease#newQuantity()} and {@link ResolvedConsumption#newCurrent()}
 * are always non-negative, and the aggregate's own mutators enforce the same
 * invariant at persistence time.</p>
 */
@Service
public final class InventoryRulesService {

    /**
     * Resolves a decrement of {@link DecreaseInput#requested()} units from an
     * item that currently holds {@link DecreaseInput#available()} on hand.
     *
     * <p>The deterministic rule is: the item may be decremented by at most the
     * quantity currently on hand. When the request exceeds the available
     * quantity the request is <b>rejected</b> &ndash; the quantity is left
     * unchanged, the {@link ResolvedDecrease#shortfall()} is reported, and a
     * reason is produced &ndash; so a quantity can never become negative. When
     * the request is satisfiable the quantity is reduced to {@code available -
     * requested}.</p>
     *
     * @param input the decrement input
     * @return the resolved decrement and its effect on the item's quantity
     * @throws IllegalArgumentException when {@code input} is {@code null}, the
     *                                  requested amount is negative, or the
     *                                  available quantity is negative
     */
    public ResolvedDecrease resolveDecrease(DecreaseInput input) {
        Objects.requireNonNull(input, "A decrement input is required");
        int requested = input.requested();
        if (requested < 0) {
            throw new IllegalArgumentException("Quantity decrement must not be negative");
        }
        int available = input.available();
        if (available < 0) {
            throw new IllegalArgumentException(
                    "Available quantity must not be negative (available=" + available + ")");
        }

        if (requested > available) {
            return new ResolvedDecrease(
                    available,
                    requested,
                    available,
                    requested - available,
                    false,
                    "Insufficient quantity for " + describe(input)
                            + ": requested " + requested
                            + ", but only " + available + " on hand");
        }

        return new ResolvedDecrease(
                available,
                requested,
                available - requested,
                0,
                true,
                null);
    }

    /**
     * Resolves the consumption of {@link ConsumptionInput#requested()} units of
     * a consumable resource that currently holds {@link ConsumptionInput#current()}
     * on hand.
     *
     * <p>A consumption is applied <b>only when both</b> of the following hold:</p>
     *
     * <ul>
     *   <li>enough of the resource is available, that is
     *       {@code requested <= current}; and</li>
     *   <li><b>every</b> {@link ConsumableResource.Requirement} on the resource
     *       is satisfied by the consumption context.</li>
     * </ul>
     *
     * <p>When either condition fails the consumption is <b>rejected</b> with a
     * clear diagnostic &ndash; the unmet requirements are reported (and, when
     * applicable, the shortfall) &ndash; the current amount is left unchanged,
     * and nothing is applied. When both conditions hold the current amount is
     * reduced to {@code current - requested}.</p>
     *
     * @param input the consumption input
     * @return the resolved consumption and its effect on the resource
     * @throws IllegalArgumentException when {@code input} is {@code null}, the
     *                                  requested amount is not positive, or the
     *                                  current amount is out of range
     */
    public ResolvedConsumption resolveConsumption(ConsumptionInput input) {
        Objects.requireNonNull(input, "A consumption input is required");
        int requested = input.requested();
        if (requested <= 0) {
            throw new IllegalArgumentException("Consumption amount must be positive");
        }
        int current = input.current();
        int max = input.max();
        if (current < 0 || current > max) {
            throw new IllegalArgumentException(
                    "Consumable resource current must be within [0, max] "
                            + "(current=" + current + ", max=" + max + ")");
        }

        List<ConsumableResource.Requirement> requirements = input.requirements();
        List<String> unmet = new ArrayList<>();
        if (requirements != null) {
            for (ConsumableResource.Requirement requirement : requirements) {
                if (!requirement.isSatisfied(current, requested, input.actorLevel(), input.actorClass())) {
                    unmet.add(requirement.describe());
                }
            }
        }

        boolean enough = requested <= current;
        boolean accepted = enough && unmet.isEmpty();

        String reason = null;
        if (!accepted) {
            StringBuilder sb = new StringBuilder();
            if (!enough) {
                sb.append("Insufficient ").append(input.name())
                        .append(": requested ").append(requested)
                        .append(", but only ").append(current).append(" available");
            }
            if (!unmet.isEmpty()) {
                if (sb.length() > 0) {
                    sb.append("; ");
                }
                sb.append("Requirements not met for ").append(input.name()).append(": ")
                        .append(String.join("; ", unmet));
            }
            if (sb.length() == 0) {
                sb.append("Cannot consume ").append(input.name());
            }
            reason = sb.toString();
        }

        return new ResolvedConsumption(
                current,
                requested,
                accepted ? current - requested : current,
                accepted,
                List.copyOf(unmet),
                reason);
    }

    private static String describe(DecreaseInput input) {
        String label = input.label();
        if (label != null && !label.isBlank()) {
            return label;
        }
        String itemId = input.itemId();
        return (itemId != null) ? "item " + itemId : "inventory item";
    }

    /**
     * Input for {@link #resolveDecrease(DecreaseInput)}. The {@code available}
     * figure is the quantity currently on hand; it is not mutated by the rules
     * service.
     *
     * @param itemId    the logical item identifier
     * @param label     a human-readable label for the item, or {@code null}
     * @param available the quantity currently on hand (must not be negative)
     * @param requested the amount to decrement (must not be negative)
     */
    public record DecreaseInput(String itemId, String label, int available, int requested) {
    }

    /**
     * The auditable outcome of a resolved decrement.
     *
     * @param available    the quantity on hand before the decrement
     * @param requested    the amount requested to decrement
     * @param newQuantity  the quantity after the decrement (never negative)
     * @param shortfall    the amount that could not be decremented (0 when accepted)
     * @param accepted     {@code true} when the decrement was applied
     * @param reason       the rejection reason, or {@code null} when accepted
     */
    public record ResolvedDecrease(
            int available,
            int requested,
            int newQuantity,
            int shortfall,
            boolean accepted,
            String reason) {
    }

    /**
     * Input for {@link #resolveConsumption(ConsumptionInput)}. The
     * {@code current}/{@code max} figures describe the resource before
     * consumption and are not mutated.
     *
     * @param name         the resource name (for diagnostics)
     * @param current      the current amount on hand (within {@code [0, max]})
     * @param max          the resource maximum
     * @param requested    the amount to consume (must be positive)
     * @param requirements the consumption requirements, or {@code null}
     * @param actorLevel   the consuming actor's level, or {@code null}
     * @param actorClass   the consuming actor's class, or {@code null}
     */
    public record ConsumptionInput(
            String name,
            int current,
            int max,
            int requested,
            List<ConsumableResource.Requirement> requirements,
            Integer actorLevel,
            String actorClass) {
    }

    /**
     * The auditable outcome of a resolved consumption.
     *
     * @param current           the current amount before consumption
     * @param requested         the amount requested to consume
     * @param newCurrent        the current amount after consumption (never negative)
     * @param accepted          {@code true} when the consumption was applied
     * @param unmetRequirements the requirements that were not met (empty when accepted)
     * @param reason            the rejection reason, or {@code null} when accepted
     */
    public record ResolvedConsumption(
            int current,
            int requested,
            int newCurrent,
            boolean accepted,
            List<String> unmetRequirements,
            String reason) {

        /**
         * @return {@code true} when the consumption was accepted; the amount
         *         actually consumed is {@link #newCurrent()} reduced from
         *         {@link #current()}
         */
        public int consumed() {
            return accepted ? current - newCurrent : 0;
        }
    }
}
