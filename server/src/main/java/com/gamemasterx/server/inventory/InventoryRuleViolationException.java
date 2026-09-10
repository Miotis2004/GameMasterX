package com.gamemasterx.server.inventory;

/**
 * Raised when an inventory quantity or consumable-resource change is rejected
 * by the backend-owned {@link
 * com.gamemasterx.server.inventory.service.InventoryRulesService} rules.
 *
 * <p>A violation means the requested change cannot be applied: the quantity
 * would go negative, or the consumable-resource requirements were not met. The
 * {@link #message()} carries the clear, human-readable diagnostic produced by
 * the rules layer so the caller can surface it. Crucially, a violation is
 * raised <em>before</em> anything is persisted, so a rejected change produces
 * no partial commit.</p>
 */
public class InventoryRuleViolationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** Discriminator for the class of violation: {@code INSUFFICIENT_QUANTITY} or {@code REQUIREMENTS_NOT_MET}. */
    private final String violationType;

    /**
     * @param message       the clear diagnostic describing the rejected change
     * @param violationType a machine-readable violation type
     */
    public InventoryRuleViolationException(String message, String violationType) {
        super(message);
        this.violationType = (violationType != null) ? violationType : "INVENTORY_RULE_VIOLATION";
    }

    /**
     * @return the machine-readable violation type
     */
    public String getViolationType() {
        return violationType;
    }
}
