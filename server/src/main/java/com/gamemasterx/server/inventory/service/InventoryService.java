package com.gamemasterx.server.inventory.service;

import com.gamemasterx.server.gameplay.model.Mutation;
import com.gamemasterx.server.gameplay.model.MutationDecision;
import com.gamemasterx.server.inventory.InventoryRuleViolationException;
import com.gamemasterx.server.inventory.model.ConsumableResource;
import com.gamemasterx.server.inventory.model.Inventory;
import com.gamemasterx.server.inventory.model.InventoryAudit;
import com.gamemasterx.server.inventory.model.InventoryItem;
import com.gamemasterx.server.inventory.repository.InventoryRepository;
import com.gamemasterx.server.inventory.controller.response.InventorySnapshotDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Application service that owns all mutations of the {@link Inventory}
 * aggregate.
 *
 * <p>This service is the single authority for inventory quantity decrements and
 * consumable-resource consumption. Every change is resolved deterministically
 * by {@link InventoryRulesService}, applied atomically to the single
 * {@link Inventory} document, and recorded in the immutable audit log with the
 * revision before and after.</p>
 *
 * <h2>Atomicity and no partial commit</h2>
 *
 * <p>Every item and consumable resource of a campaign lives inside one
 * {@link Inventory} document, so a batch of changes is a single-document
 * write. A change is only ever applied <em>after</em> its resolution is
 * accepted: if any change in a batch is rejected (a quantity would go negative,
 * or consumable-resource requirements are not met), this service throws a
 * {@link InventoryRuleViolationException} <em>before</em> persisting anything,
 * so a rejected batch produces <b>no partial commit</b> and a clear diagnostic.</p>
 *
 * <h2>Audit trail</h2>
 *
 * <p>Each accepted change is appended to the immutable audit log as one
 * {@link InventoryAudit} entry recording the subject's state <em>before</em>
 * and <em>after</em>, the acting actor, an optional rejection reason, and the
 * aggregate <em>revision before</em> and <em>revision after</em>.</p>
 */
@Service
public class InventoryService {

    private final InventoryRepository inventoryRepository;
    private final InventoryRulesService inventoryRulesService;
    private final InventoryAuditService inventoryAuditService;

    /** Maximum number of individual changes allowed in a single batch decrement. */
    private final int maxBatchChanges;

    /**
     * Creates the inventory service with its repositories and the shared rules
     * service.
     *
     * @param inventoryRepository    the inventory repository
     * @param inventoryRulesService  the single authority for quantity/resource rules
     * @param inventoryAuditService  the append-only audit-log service
     * @param maxBatchChanges        the maximum number of changes per batch decrement
     */
    public InventoryService(InventoryRepository inventoryRepository,
                            InventoryRulesService inventoryRulesService,
                            InventoryAuditService inventoryAuditService,
                            @Value("${game.master.x.inventory.max-batch-changes:100}") int maxBatchChanges) {
        this.inventoryRepository = inventoryRepository;
        this.inventoryRulesService = Objects.requireNonNull(inventoryRulesService, "A rules service is required");
        this.inventoryAuditService = Objects.requireNonNull(inventoryAuditService, "An audit service is required");
        this.maxBatchChanges = Math.max(1, maxBatchChanges);
    }

    /**
     * Decrements the quantities of the given inventory items in a single,
     * atomic operation.
     *
     * <p>Each requested change is resolved by {@link InventoryRulesService}.
     * If any requested quantity would drive the item below {@code 0} the whole
     * batch is rejected with a {@link InventoryRuleViolationException} before
     * anything is persisted, so no partial commit occurs. When every change is
     * acceptable, all quantities are decremented, the inventory is saved as a
     * single atomic document, and one accepted {@link InventoryAudit} entry is
     * appended per change recording the revision before and after.</p>
     *
     * @param campaignId the campaign whose inventory is being changed
     * @param changes    the items to decrement (must be non-empty and within the batch limit)
     * @param actor      the authenticated actor making the change
     * @param note       a free-form note on the batch, or {@code null}
     * @param correlationId the correlation id, or {@code null}
     * @return the updated inventory snapshot
     * @throws IllegalArgumentException when {@code campaignId} is blank, the
     *                                  changes are empty or over the batch limit,
     *                                  any item is missing, or any requested
     *                                  quantity is negative
     * @throws InventoryRuleViolationException when any requested quantity would
     *                                         drive an item below {@code 0}
     */
    @Transactional
    public InventorySnapshotDto decreaseItems(String campaignId, List<DecreaseCommand> changes,
                                              String actor, String note, String correlationId) {
        String resolvedCampaign = requireNonBlank(campaignId, "campaignId");
        List<DecreaseCommand> resolvedChanges = (changes != null) ? new ArrayList<>(changes) : List.of();
        if (resolvedChanges.isEmpty()) {
            throw new IllegalArgumentException("At least one item must be decremented");
        }
        if (resolvedChanges.size() > maxBatchChanges) {
            throw new IllegalArgumentException(
                    "Batch decrement is limited to " + maxBatchChanges + " items (requested "
                            + resolvedChanges.size() + ")");
        }
        Objects.requireNonNull(actor, "An actor is required");

        Inventory inventory = loadOrCreate(resolvedCampaign);

        int revisionBefore = inventory.getRevision();

        // Resolve every change up front. This is where the rules decide
        // accepted/rejected; nothing is applied or persisted yet, so a single
        // rejection below leaves the document untouched (no partial commit).
        int size = resolvedChanges.size();
        ResolvedChange[] resolved = new ResolvedChange[size];
        for (int i = 0; i < size; i++) {
            DecreaseCommand command = resolvedChanges.get(i);
            InventoryItem item = requireItem(inventory, command);
            InventoryRulesService.ResolvedDecrease resolvedDecrease = inventoryRulesService.resolveDecrease(
                    new InventoryRulesService.DecreaseInput(
                            item.getId(), item.getName(), item.getQuantity(), command.amount()));
            resolved[i] = new ResolvedChange(item, resolvedDecrease);
        }

        // If any change was rejected, fail the whole batch before persisting.
        StringBuilder failures = new StringBuilder();
        for (ResolvedChange rc : resolved) {
            if (!rc.decrease().accepted()) {
                if (failures.length() > 0) {
                    failures.append("\n");
                }
                failures.append(rc.decrease().reason());
            }
        }
        if (failures.length() > 0) {
            throw new InventoryRuleViolationException(
                    "Batch decrement rejected:\n" + failures, "INSUFFICIENT_QUANTITY");
        }

        // All changes accepted: apply, save atomically, then audit.
        for (ResolvedChange rc : resolved) {
            rc.item().setQuantity(rc.decrease().newQuantity());
        }
        Inventory saved = save(inventory, note);

        List<InventoryAudit> entries = new ArrayList<>();
        for (int i = 0; i < resolved.length; i++) {
            ResolvedChange rc = resolved[i];
            InventoryRulesService.ResolvedDecrease d = rc.decrease();
            Map<String, Object> before = quantitySnapshot(rc.item().getId(), rc.item().getName(), d.available());
            Map<String, Object> after = quantitySnapshot(rc.item().getId(), rc.item().getName(), d.newQuantity());
            String description = "Decreased " + rc.item().getName() + " by " + d.requested()
                    + " (" + d.available() + " -> " + d.newQuantity() + ")" + noteSuffix(note);
            Mutation mutation = new Mutation(
                    null, "inventoryItem", rc.item().getId(), description, before, after, Instant.now());
            entries.add(new InventoryAudit(
                    null, 0, resolvedCampaign, saved.getId(),
                    "inventoryItem", rc.item().getId(),
                    mutation.beforeJson(), mutation.afterJson(),
                    MutationDecision.ACCEPTED, actor, null,
                    revisionBefore, saved.getRevision(), Instant.now(), correlationId));
        }
        inventoryAuditService.appendAuditEntries(entries);
        return InventorySnapshotDto.from(saved);
    }

    /**
     * Consumes {@code amount} units of a single consumable resource.
     *
     * <p>The consumption is resolved by {@link InventoryRulesService}. It is
     * applied <b>only when</b> enough of the resource is available and every
     * {@link ConsumableResource.Requirement} is satisfied. Otherwise a
     * {@link InventoryRuleViolationException} is thrown before anything is
     * persisted, so nothing is applied (no partial commit) and the unmet
     * requirements are reported.</p>
     *
     * @param campaignId   the campaign whose inventory is being changed
     * @param resourceId   the logical id of the resource to consume
     * @param amount       the amount to consume (must be positive)
     * @param actorLevel   the consuming actor's character level, or {@code null}
     * @param actorClass   the consuming actor's class, or {@code null}
     * @param actor        the authenticated actor making the change
     * @param note         a free-form note on the consumption, or {@code null}
     * @param correlationId the correlation id, or {@code null}
     * @return the updated inventory snapshot
     * @throws IllegalArgumentException when {@code campaignId} or {@code resourceId}
     *                                  is blank, the amount is not positive, or
     *                                  the resource is missing
     * @throws InventoryRuleViolationException when the consumption is rejected
     *                                         because requirements are unmet or the
     *                                         resource would go below {@code 0}
     */
    @Transactional
    public InventorySnapshotDto consumeResource(String campaignId, String resourceId, int amount,
                                                Integer actorLevel, String actorClass,
                                                String actor, String note, String correlationId) {
        String resolvedCampaign = requireNonBlank(campaignId, "campaignId");
        String resolvedResource = requireNonBlank(resourceId, "resourceId");
        if (amount <= 0) {
            throw new IllegalArgumentException("Consumption amount must be positive");
        }
        Objects.requireNonNull(actor, "An actor is required");

        Inventory inventory = loadOrCreate(resolvedCampaign);
        ConsumableResource resource = requireResource(inventory, resolvedResource);

        int revisionBefore = inventory.getRevision();
        InventoryRulesService.ResolvedConsumption resolved = inventoryRulesService.resolveConsumption(
                new InventoryRulesService.ConsumptionInput(
                        resource.getName(), resource.getCurrent(), resource.getMax(), amount,
                        resource.getRequirements(), actorLevel, actorClass));

        if (!resolved.accepted()) {
            throw new InventoryRuleViolationException(
                    resolved.reason() == null
                            ? "Cannot consume resource " + resource.getName()
                            : resolved.reason(),
                    resolved.unmetRequirements().isEmpty() ? "INSUFFICIENT_QUANTITY" : "REQUIREMENTS_NOT_MET");
        }

        resource.setCurrent(resolved.newCurrent());
        Inventory saved = save(inventory, note);

        Map<String, Object> before = resourceSnapshot(resource.getName(), resolved.current());
        Map<String, Object> after = resourceSnapshot(resource.getName(), resolved.newCurrent());
        String description = "Consumed " + amount + " " + resource.getName()
                + " (" + resolved.current() + " -> " + resolved.newCurrent() + ")" + noteSuffix(note);
        Mutation mutation = new Mutation(
                null, "consumableResource", resource.getId(), description, before, after, Instant.now());
        inventoryAuditService.appendAuditEntries(List.of(new InventoryAudit(
                null, 0, resolvedCampaign, saved.getId(),
                "consumableResource", resource.getId(),
                mutation.beforeJson(), mutation.afterJson(),
                MutationDecision.ACCEPTED, actor, null,
                revisionBefore, saved.getRevision(), Instant.now(), correlationId)));
        return InventorySnapshotDto.from(saved);
    }

    /**
     * Returns the current snapshot of a campaign's inventory. No inventory is
     * created by a read; a campaign with no inventory yet is reported as not
     * found.
     *
     * @param campaignId the campaign identifier
     * @return the inventory snapshot
     * @throws IllegalArgumentException when no inventory exists for the campaign
     */
    @Transactional(readOnly = true)
    public InventorySnapshotDto snapshot(String campaignId) {
        String resolvedCampaign = requireNonBlank(campaignId, "campaignId");
        Inventory inventory = inventoryRepository.findByCampaignId(resolvedCampaign)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No inventory found for campaign " + resolvedCampaign));
        return InventorySnapshotDto.from(inventory);
    }

    /**
     * Loads the inventory for a campaign, creating a fresh aggregate when none
     * exists yet so the first change can bootstrap the inventory.
     *
     * @param campaignId the campaign identifier
     * @return the existing or freshly-created inventory (not yet persisted when new)
     */
    private Inventory loadOrCreate(String campaignId) {
        Optional<Inventory> existing = inventoryRepository.findByCampaignId(campaignId);
        if (existing.isPresent()) {
            return existing.get();
        }
        Inventory created = new Inventory();
        created.setId(UUID.randomUUID().toString());
        created.setCampaignId(campaignId);
        created.setName("Campaign inventory");
        Instant now = Instant.now();
        created.setCreatedAt(now);
        created.setUpdatedAt(now);
        return created;
    }

    /**
     * Saves the inventory and returns the persisted document.
     *
     * @param inventory the inventory to persist
     * @param note      a free-form note, ignored for persistence (audit only)
     * @return the saved inventory
     */
    private Inventory save(Inventory inventory, String note) {
        // The @Version-annotated revision is managed by Spring Data MongoDB on
        // save; we only refresh the updatedAt timestamp. The revision before and
        // after are read from the document before and after this save for the
        // audit trail.
        inventory.setUpdatedAt(Instant.now());
        return inventoryRepository.save(inventory);
    }

    private InventoryItem requireItem(Inventory inventory, DecreaseCommand command) {
        String itemId = requireNonBlank(command.itemId(), "itemId");
        Optional<InventoryItem> found = inventory.findItem(itemId);
        if (!found.isPresent()) {
            throw new IllegalArgumentException("Inventory item not found: " + itemId);
        }
        return found.get();
    }

    private ConsumableResource requireResource(Inventory inventory, String resourceId) {
        Optional<ConsumableResource> found = inventory.findResource(resourceId);
        if (!found.isPresent()) {
            throw new IllegalArgumentException("Consumable resource not found: " + resourceId);
        }
        return found.get();
    }

    private static Map<String, Object> quantitySnapshot(String itemId, String name, int quantity) {
        Map<String, Object> map = new HashMap<>();
        map.put("itemId", itemId);
        map.put("name", name);
        map.put("quantity", quantity);
        return map;
    }

    private static Map<String, Object> resourceSnapshot(String name, int current) {
        Map<String, Object> map = new HashMap<>();
        map.put("name", name);
        map.put("current", current);
        return map;
    }

    private static String noteSuffix(String note) {
        return (note != null && !note.isBlank()) ? " (" + note + ")" : "";
    }

    private static String requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }

    /**
     * A single requested decrement in a batch decrement.
     *
     * @param itemId the logical item id to decrement
     * @param amount the amount to decrement (validated to be positive by the
     *               rules layer; a value of {@code 0} is a no-op)
     */
    public record DecreaseCommand(String itemId, int amount) {
        /**
         * Creates a decrement command, validating the amount is not negative.
         *
         * @param itemId the logical item id to decrement
         * @param amount the amount to decrement (must not be negative)
         * @throws IllegalArgumentException when {@code amount} is negative
         */
        public DecreaseCommand {
            if (amount < 0) {
                throw new IllegalArgumentException("Amount must not be negative");
            }
        }
    }

    /**
     * The paired item and its resolved decrement used while applying a batch.
     */
    private record ResolvedChange(InventoryItem item, InventoryRulesService.ResolvedDecrease decrease) {
    }
}
