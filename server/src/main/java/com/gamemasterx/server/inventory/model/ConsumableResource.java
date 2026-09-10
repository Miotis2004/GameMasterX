package com.gamemasterx.server.inventory.model;

import java.util.ArrayList;
import java.util.List;

/**
 * A named, possibly limited, consumable resource tracked within an
 * {@link Inventory} aggregate, such as spell slots, action charges, hit dice
 * or potions.
 *
 * <p>This is an embedded value object of the {@link Inventory} aggregate; it is
 * never persisted on its own. The resource's {@link #current} amount is always
 * within {@code [0, max]}: the mutators enforce this invariant so a resource
 * can never be persisted out of range.</p>
 *
 * <p>Consumption of the resource is governed by an optional, ordered set of
 * {@link #requirements}. A consumable-resource change is applied only when every
 * requirement is met (and enough current amount is available); otherwise the
 * change is rejected with a clear reason and nothing is applied.</p>
 */
public class ConsumableResource {

    /** Logical identifier of the resource within its owning aggregate. */
    private String id;

    /** Human-readable name of the resource. */
    private String name;

    /** The kind of resource, for example {@code "spellSlot"} or {@code "charge"}. */
    private String resourceType;

    /**
     * The current amount on hand. Always within {@code [0, max]}.
     */
    private int current;

    /** The maximum amount the resource can hold. */
    private int max;

    /** Optional free-form description of the resource. */
    private String description;

    /** Ordered set of conditions that must all be met to consume the resource. */
    private List<Requirement> requirements;

    public ConsumableResource() {
        this.requirements = new ArrayList<>();
    }

    /**
     * @param id           the logical resource identifier
     * @param name         the resource name
     * @param resourceType the resource kind
     * @param current      the starting current amount (must be within {@code [0, max]})
     * @param max          the maximum amount (must not be negative)
     * @param requirements the consumption requirements (may be {@code null})
     */
    public ConsumableResource(String id, String name, String resourceType, int current, int max,
                              List<Requirement> requirements) {
        this.id = id;
        this.name = name;
        this.resourceType = (resourceType != null) ? resourceType : "charge";
        if (max < 0) {
            throw new IllegalArgumentException("Consumable resource maximum must not be negative");
        }
        this.max = max;
        setCurrent(current);
        this.requirements = (requirements != null) ? new ArrayList<>(requirements) : new ArrayList<>();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getResourceType() {
        return resourceType;
    }

    public void setResourceType(String resourceType) {
        this.resourceType = resourceType;
    }

    /**
     * @param current the new current amount; must be within {@code [0, max]}
     * @throws IllegalArgumentException when the amount is out of range
     */
    public void setCurrent(int current) {
        if (current < 0 || current > max) {
            throw new IllegalArgumentException(
                    "Consumable resource current must be within [0, max]");
        }
        this.current = current;
    }

    public int getCurrent() {
        return current;
    }

    public int getMax() {
        return max;
    }

    public void setMax(int max) {
        if (max < 0) {
            throw new IllegalArgumentException("Consumable resource maximum must not be negative");
        }
        this.max = max;
        if (this.current > max) {
            this.current = max;
        }
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<Requirement> getRequirements() {
        return requirements;
    }

    public void setRequirements(List<Requirement> requirements) {
        this.requirements = (requirements != null) ? new ArrayList<>(requirements) : new ArrayList<>();
    }

    /**
     * Adds a single consumption requirement.
     *
     * @param requirement the requirement to add
     */
    public void addRequirement(Requirement requirement) {
        if (requirement == null) {
            throw new IllegalArgumentException("A requirement must not be null");
        }
        this.requirements.add(requirement);
    }

    /**
     * A single consumption requirement. The {@link #type()} selects the test;
     * the {@link #value()} carries the numeric threshold and {@link
     * #stringValue()} carries an optional textual value used by requirements
     * that key off a string (for example the class a caller must belong to).
     */
    public record Requirement(RequirementType type, int value, String stringValue) {

        /**
         * The kinds of requirement that can gate consumption of a resource.
         */
        public enum RequirementType {
            /** The resource must have at least {@code value} current before it can be consumed. */
            MIN_CURRENT,
            /** A single consumption may not reduce the resource by more than {@code value}. */
            MAX_SINGLE_USE,
            /** The consuming actor must hold at least {@code value} character level. */
            MIN_LEVEL,
            /** The consuming actor must belong to the class named by {@link #stringValue()}. */
            CLASS_REQUIRED
        }

        /**
         * @param type         the requirement kind
         * @param value        the numeric threshold for the requirement
         * @param stringValue  an optional textual value, or {@code null}
         * @throws IllegalArgumentException when {@code type} is {@code null}
         */
        public Requirement {
            if (type == null) {
                throw new IllegalArgumentException("A requirement type must not be null");
            }
        }

        /**
         * Creates a numeric requirement of the given kind and threshold.
         *
         * @param type  the requirement kind
         * @param value the numeric threshold
         * @return the new requirement
         */
        public static Requirement numeric(RequirementType type, int value) {
            return new Requirement(type, value, null);
        }

        /**
         * Creates a class requirement naming the class an actor must belong to.
         *
         * @param className the required class name
         * @return the new requirement
         */
        public static Requirement classRequired(String className) {
            return new Requirement(RequirementType.CLASS_REQUIRED, 0, className);
        }

        /**
         * Evaluates this requirement against the given consumption context.
         *
         * @param current    the resource's current amount before consumption
         * @param requested  the amount being consumed
         * @param actorLevel the consuming actor's character level, or {@code null}
         * @param actorClass the consuming actor's class name, or {@code null}
         * @return {@code true} when the requirement is satisfied
         */
        public boolean isSatisfied(int current, int requested, Integer actorLevel, String actorClass) {
            return switch (type) {
                case MIN_CURRENT -> current >= value;
                case MAX_SINGLE_USE -> requested <= value;
                case MIN_LEVEL -> actorLevel != null && actorLevel >= value;
                case CLASS_REQUIRED -> actorClass != null
                        && actorClass.trim().equalsIgnoreCase(stringValue);
            };
        }

        /**
         * @return a human-readable description of this requirement
         */
        public String describe() {
            return switch (type) {
                case MIN_CURRENT -> "must retain at least " + value + " before use";
                case MAX_SINGLE_USE -> "a single use may consume at most " + value;
                case MIN_LEVEL -> "requires actor level >= " + value;
                case CLASS_REQUIRED -> "requires actor class '" + stringValue + "'";
            };
        }
    }
}
