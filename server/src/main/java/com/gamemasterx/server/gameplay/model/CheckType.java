package com.gamemasterx.server.gameplay.model;

/**
 * The three backend-owned check kinds resolved by {@link
 * com.gamemasterx.server.gameplay.service.GameplayRulesService}.
 *
 * <p>Each kind is resolved by rolling a {@code 1d20} through the dice subsystem
 * and combining the die total with the deterministic modifiers (ability
 * modifier and, when applicable, proficiency bonus). The kinds differ only in
 * how proficiency is applied:</p>
 *
 * <ul>
 *   <li>{@link #ABILITY_CHECK} &ndash; a general ability check. Proficiency is
 *       applied only when the actor is explicitly proficient.</li>
 *   <li>{@link #SKILL_CHECK} &ndash; a skill check. Skills are, by definition,
 *       professed, so proficiency is always applied.</li>
 *   <li>{@link #SAVING_THROW} &ndash; a saving throw. Proficiency is applied
 *       only when the actor is explicitly proficient.</li>
 * </ul>
 *
 * <p>Every kind maps onto a single {@link ActionType} so the resolved {@link
 * Action} carries a typed, unambiguous category.</p>
 */
public enum CheckType {

    /** A general ability check. */
    ABILITY_CHECK(ActionType.ABILITY_CHECK),

    /** A skill check (proficiency always applies). */
    SKILL_CHECK(ActionType.SKILL_CHECK),

    /** A saving throw. */
    SAVING_THROW(ActionType.SAVING_THROW);

    private final ActionType actionType;

    CheckType(ActionType actionType) {
        this.actionType = actionType;
    }

    /**
     * @return the {@link ActionType} this check kind resolves to on the
     *         resulting {@link Action}
     */
    public ActionType toActionType() {
        return actionType;
    }
}
