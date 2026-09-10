package com.gamemasterx.server.inventory.model;

/**
 * A single inventory item tracked within an {@link Inventory} aggregate.
 *
 * <p>This is an embedded value object of the {@link Inventory} aggregate; it is
 * never persisted on its own. The {@link #quantity} of an item is always
 * non-negative: the {@link #setQuantity(int)} mutator rejects any negative
 * value defensively, so an item can never be persisted with a negative
 * quantity even if the rules layer is bypassed.</p>
 */
public class InventoryItem {

    /** Logical identifier of the item within its owning aggregate. */
    private String id;

    /** Canonical item identifier, shared across campaigns for the same item. */
    private String itemId;

    /** Human-readable name of the item. */
    private String name;

    /**
     * The current quantity on hand. Always {@code >= 0}; the setter enforces
     * this invariant.
     */
    private int quantity;

    /**
     * Optional upper bound on how many of this item may be held, or
     * {@code -1} when unbounded.
     */
    private int capacity;

    public InventoryItem() {
    }

    /**
     * @param id       the logical item identifier
     * @param itemId   the canonical item identifier
     * @param name     the item name
     * @param quantity the starting quantity (must not be negative)
     */
    public InventoryItem(String id, String itemId, String name, int quantity) {
        this.id = id;
        this.itemId = itemId;
        this.name = name;
        setQuantity(quantity);
        this.capacity = -1;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getItemId() {
        return itemId;
    }

    public void setItemId(String itemId) {
        this.itemId = itemId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    /**
     * @param quantity the new quantity; must not be negative
     * @throws IllegalArgumentException when {@code quantity} is negative
     */
    public void setQuantity(int quantity) {
        if (quantity < 0) {
            throw new IllegalArgumentException("Inventory quantity must not be negative");
        }
        this.quantity = quantity;
    }

    public int getQuantity() {
        return quantity;
    }

    public int getCapacity() {
        return capacity;
    }

    public void setCapacity(int capacity) {
        this.capacity = capacity;
    }
}
