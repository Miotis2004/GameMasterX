package com.gamemasterx.server.gameplay.controller;

/**
 * Request body for a long rest ({@code POST /api/encounters/{id}/rest/long'}).
 *
 * <p>A long rest recovers all hit points, spent Hit Dice up to half the
 * participant's total, and every other spent resource to its maximum, for every
 * participant in the owning encounter.</p>
 */
public record LongRestRequest(

        /**
         * A free-form note recorded on the audited long-rest action, or
         * {@code null}.
         */
        String note) {
}
