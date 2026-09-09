package com.gamemasterx.server.campaign.membership.model;

import java.util.Arrays;

/**
 * Membership role within a Campaign aggregate.
 *
 * <p>There are exactly four roles, listed from highest to lowest authority:
 * <ul>
 *     <li>{@link #OWNER} - the campaign owner with full authority.</li>
 *     <li>{@link #GAME_MASTER} - the game master who runs sessions.</li>
 *     <li>{@link #PLAYER} - a participant in the campaign.</li>
 *     <li>{@link #OBSERVER} - a passive viewer.</li>
 * </ul>
 *
 * <p>The {@link #level} of each role defines a linear {@linkplain #hierarchy role
 * hierarchy} that backs authorization checks: a role is authorized for an action
 * when its level is greater than or equal to the level of the role the action
 * requires. This means authority flows downward - an owner is implicitly a game
 * master, a player, and an observer.</p>
 */
public enum MembershipRole {

    OBSERVER(0),
    PLAYER(1),
    GAME_MASTER(2),
    OWNER(3);

    private static final int ORDER = 4;

    private final int level;

    MembershipRole(int level) {
        this.level = level;
    }

    /**
     * @return the position of this role in the hierarchy. Higher values denote
     * greater authority.
     */
    public int getLevel() {
        return level;
    }

    /**
     * @return the total number of distinct roles in the hierarchy.
     */
    public static int order() {
        return ORDER;
    }

    /**
     * <p>Returns {@code true} when this role sits at or above {@code required} in
     * the hierarchy, and is therefore authorized for actions that require
     * {@code required}.</p>
     *
     * <p>Example: an {@link #OWNER} is at or above every role, so it is
     * {@code isAtLeast(GAME_MASTER)}, {@code isAtLeast(PLAYER)} and
     * {@code isAtLeast(OBSERVER)} all {@code true}.</p>
     *
     * @param required the minimum role required for an action
     * @return {@code true} if this role authorizes the action
     */
    public boolean isAtLeast(MembershipRole required) {
        return this.level >= required.level;
    }

    /**
     * <p>Returns the role that is one step below this role in the hierarchy, or
     * {@code null} when this role is already the lowest (an observer).</p>
     *
     * @return the next-lower role, or {@code null}
     */
    public MembershipRole getRoleBelow() {
        MembershipRole[] values = values();
        int index = this.ordinal();
        return index > 0 ? values[index - 1] : null;
    }

    /**
     * Parses a role from its name, case-insensitively.
     *
     * @param raw the role name (for example {@code "OWNER"} or {@code "player"})
     * @return the matching role
     * @throws IllegalArgumentException if {@code raw} does not match a role
     */
    public static MembershipRole parse(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("Role must not be null");
        }
        return Arrays.stream(values())
                .filter(role -> role.name().equalsIgnoreCase(raw))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown role: " + raw + ". Expected one of "
                                + Arrays.toString(values())));
    }
}
