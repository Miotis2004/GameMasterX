package com.gamemasterx.server.inventory.controller;

import com.gamemasterx.server.inventory.InventoryRuleViolationException;
import com.gamemasterx.server.inventory.controller.request.ConsumeResourceRequest;
import com.gamemasterx.server.inventory.controller.request.DecreaseRequest;
import com.gamemasterx.server.inventory.controller.response.InventorySnapshotDto;
import com.gamemasterx.server.inventory.model.InventoryAudit;
import com.gamemasterx.server.inventory.repository.InventoryAuditRepository;
import com.gamemasterx.server.inventory.service.InventoryService;
import com.gamemasterx.server.exception.AuthorizationException;
import com.gamemasterx.server.exception.ErrorResponse;
import com.gamemasterx.server.filter.AuthFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * REST controller exposing the {@link com.gamemasterx.server.inventory.model.Inventory}
 * aggregate.
 *
 * <p>All endpoints run on the backend API port (5172). State-changing endpoints
 * require an authenticated actor; the quantity and consumable-resource rules
 * themselves are enforced server-side by the backend-owned {@link
 * com.gamemasterx.server.inventory.service.InventoryRulesService}. A rejected
 * change is reported with a {@code 400 BAD_REQUEST} response carrying the clear
 * diagnostic produced by those rules (see {@link
 * #handleInventoryRuleViolation}).</p>
 */
@RestController
@RequestMapping("/api/inventory")
public class InventoryController {

    private static final String CORRELATION_ID_ATTRIBUTE = "correlationId";
    private static final String INVENTORY_RULE_VIOLATION = "INVENTORY_RULE_VIOLATION";

    private final InventoryService inventoryService;
    private final InventoryAuditRepository inventoryAuditRepository;

    public InventoryController(InventoryService inventoryService,
                               InventoryAuditRepository inventoryAuditRepository) {
        this.inventoryService = inventoryService;
        this.inventoryAuditRepository = inventoryAuditRepository;
    }

    /**
     * Decrements the quantities of one or more inventory items in a single,
     * atomic batch. If any requested quantity would drive an item below
     * {@code 0} the whole batch is rejected with a {@code 400} response and
     * nothing is committed.
     *
     * @param campaignId the campaign whose inventory is being changed
     * @param request the batch of decrements
     * @param httpRequest the incoming request (for the authenticated actor)
     * @return the updated inventory snapshot
     */
    @PostMapping("/{campaignId}/decrease")
    public ResponseEntity<InventorySnapshotDto> decreaseItems(
            @PathVariable String campaignId,
            @Valid @RequestBody DecreaseRequest request,
            HttpServletRequest httpRequest) {
        List<InventoryService.DecreaseCommand> changes = new ArrayList<>();
        for (DecreaseRequest.Line line : request.changes()) {
            changes.add(new InventoryService.DecreaseCommand(line.itemId(), line.amount()));
        }
        return ResponseEntity.ok(inventoryService.decreaseItems(
                campaignId,
                changes,
                requireActor(httpRequest),
                request.note(),
                getCorrelationId(httpRequest)));
    }

    /**
     * Consumes a single consumable resource. The consumption is applied only
     * when the resource has enough available and every requirement is met;
     * otherwise a {@code 400} response reports the unmet requirements.
     *
     * @param campaignId the campaign whose inventory is being changed
     * @param resourceId the logical id of the resource to consume
     * @param request    the consumption request
     * @param httpRequest the incoming request (for the authenticated actor)
     * @return the updated inventory snapshot
     */
    @PostMapping("/{campaignId}/resources/{resourceId}/consume")
    public ResponseEntity<InventorySnapshotDto> consumeResource(
            @PathVariable String campaignId,
            @PathVariable String resourceId,
            @Valid @RequestBody ConsumeResourceRequest request,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(inventoryService.consumeResource(
                campaignId, resourceId, request.amount(),
                request.actorLevel(), request.actorClass(),
                requireActor(httpRequest), request.note(),
                getCorrelationId(httpRequest)));
    }

    /**
     * Returns the current inventory snapshot (items and consumable resources)
     * for a campaign.
     *
     * @param campaignId the campaign identifier
     * @param httpRequest the incoming request (for the authenticated actor)
     * @return the inventory snapshot
     */
    @GetMapping("/{campaignId}")
    public ResponseEntity<InventorySnapshotDto> getInventory(
            @PathVariable String campaignId,
            HttpServletRequest httpRequest) {
        requireActor(httpRequest);
        return ResponseEntity.ok(inventoryService.snapshot(campaignId));
    }

    /**
     * Returns the immutable inventory audit log for a campaign, in append
     * (sequence) order.
     *
     * @param campaignId the campaign identifier
     * @param httpRequest the incoming request (for the authenticated actor)
     * @return the immutable audit log entries
     */
    @GetMapping("/{campaignId}/audit")
    public ResponseEntity<List<InventoryAudit>> auditLog(
            @PathVariable String campaignId,
            HttpServletRequest httpRequest) {
        requireActor(httpRequest);
        return ResponseEntity.ok(inventoryAuditRepository
                .findByCampaignIdOrderByAuditSequenceAsc(campaignId));
    }

    /**
     * Resolves the authenticated actor from the request context established by
     * the auth filter, or throws an authorization denial when the caller is not
     * authenticated.
     */
    private String requireActor(HttpServletRequest request) {
        Object attr = request.getAttribute(AuthFilter.AUTH_USER_ATTR);
        if (attr instanceof String s && !s.isBlank()) {
            return s;
        }
        throw new AuthorizationException(
                com.gamemasterx.server.campaign.membership.model.MembershipRole.OBSERVER,
                "Authentication required to manage inventory");
    }

    private static String getCorrelationId(HttpServletRequest request) {
        Object attr = request.getAttribute(CORRELATION_ID_ATTRIBUTE);
        if (attr instanceof String s && !s.isBlank()) {
            return s;
        }
        return UUID.randomUUID().toString();
    }

    /**
     * Maps a rejected inventory change (a quantity that would go negative, or
     * unmet consumable-resource requirements) to a {@code 400 BAD_REQUEST}
     * response carrying the clear diagnostic and machine-readable violation
     * type produced by the backend-owned rules.
     */
    @ExceptionHandler(InventoryRuleViolationException.class)
    public ResponseEntity<ErrorResponse> handleInventoryRuleViolation(
            InventoryRuleViolationException ex, HttpServletRequest request) {
        List<com.gamemasterx.server.exception.FieldError> fieldErrors = new ArrayList<>();
        ErrorResponse errorResponse = new ErrorResponse(
                INVENTORY_RULE_VIOLATION,
                ex.getMessage() == null ? INVENTORY_RULE_VIOLATION : ex.getMessage(),
                getCorrelationId(request), fieldErrors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }
}
