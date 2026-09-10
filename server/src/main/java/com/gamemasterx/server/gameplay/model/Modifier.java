package com.gamemasterx.server.gameplay.model;

/**
 * A single named modifier applied to an {@link Action}, such as a ability
 * modifier, a proficiency bonus, a circumstance bonus or a disadvantage
 * penalty. Modifiers are immutable value records and are embedded within the
 * action that produced them.
 *
 * @param name  the name of the modifier, for example {@code "Strength mod"} or
 *              {@code "Advantage"}
 * @param value the signed value contributed by this modifier
 */
public record Modifier(String name, int value) {

    /**
     * @param name  the name of the modifier; must not be blank
     * @param value the signed value contributed by this modifier
     * @throws IllegalArgumentException if {@code name} is null or blank
     */
    public Modifier {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Modifier name must not be blank");
        }
    }
}
