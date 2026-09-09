package com.gamemasterx.server.campaign.membership.controller;

import com.gamemasterx.server.campaign.membership.model.MembershipCreateRequest;
import com.gamemasterx.server.campaign.membership.model.MembershipDto;
import com.gamemasterx.server.campaign.membership.model.MembershipRole;
import com.gamemasterx.server.campaign.membership.service.MembershipService;
import com.gamemasterx.server.exception.ErrorResponse;
import com.gamemasterx.server.exception.FieldError;
import com.gamemasterx.server.filter.AuthFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * REST endpoints for managing Campaign memberships and enforcing role-based
 * authorization through the {@link MembershipRole} hierarchy.
 */
@RestController
@RequestMapping("/api/campaigns")
public class MembershipController {

    private static final String CORRELATION_ID_ATTRIBUTE = "correlationId";

    private final MembershipService membershipService;

    public MembershipController(MembershipService membershipService) {
        this.membershipService = membershipService;
    }

    /**
     * Adds a user to a campaign with a specific role. Role requirement: the
     * authenticated caller must hold at least the {@code GAME_MASTER} role in
     * the campaign.
     */
    @PostMapping("/{id}/members")
    public ResponseEntity<MembershipDto> addMember(@PathVariable String id,
                                                   @RequestBody MembershipCreateRequest request,
                                                   HttpServletRequest httpRequest) {
        membershipService.assertAuthorized(id, requireActor(httpRequest), MembershipRole.GAME_MASTER);
        MembershipDto created = membershipService.addMember(id, request);
        return new ResponseEntity<>(created, HttpStatus.CREATED);
    }

    /**
     * Updates the role a user holds in a campaign. The authenticated caller
     * must be authorized to change roles in the campaign.
     */
    @PutMapping("/{id}/members/{userId}/role")
    public ResponseEntity<MembershipDto> updateRole(@PathVariable String id,
                                                    @PathVariable String userId,
                                                    @RequestParam String role,
                                                    HttpServletRequest httpRequest) {
        return ResponseEntity.ok(
                membershipService.updateRole(id, currentActor(httpRequest), userId, role));
    }

    /**
     * Accepts a pending membership, transitioning it to active. The caller must
     * be the member themselves.
     */
    @PostMapping("/{id}/members/{userId}/accept")
    public ResponseEntity<MembershipDto> acceptMembership(@PathVariable String id,
                                                         @PathVariable String userId,
                                                         HttpServletRequest httpRequest) {
        return ResponseEntity.ok(
                membershipService.acceptMembership(id, userId, currentActor(httpRequest)));
    }

    /**
     * Revokes the caller's own membership in the campaign. A member may only
     * revoke their own membership.
     */
    @PostMapping("/{id}/members/{userId}/revoke")
    public ResponseEntity<MembershipDto> revokeMembership(@PathVariable String id,
                                                          @PathVariable String userId,
                                                          HttpServletRequest httpRequest) {
        return ResponseEntity.ok(
                membershipService.revokeMembership(id, userId, currentActor(httpRequest)));
    }

    /**
     * Removes a user's membership from a campaign. The authenticated caller
     * must hold at least the GAME_MASTER role.
     */
    @RequestMapping(method = RequestMethod.DELETE, path = "/{id}/members/{userId}")
    public ResponseEntity<Void> removeMember(@PathVariable String id,
                                             @PathVariable String userId,
                                             HttpServletRequest httpRequest) {
        membershipService.removeMember(id, currentActor(httpRequest), userId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Returns the role a user holds in a campaign. Role requirement: the
     * authenticated caller must hold at least the {@code OBSERVER} role
     * (membership) in the campaign.
     */
    @GetMapping("/{id}/members/{userId}/role")
    public ResponseEntity<MembershipRole> getRole(@PathVariable String id,
                                                  @PathVariable String userId,
                                                   HttpServletRequest httpRequest) {
        membershipService.assertAuthorized(id, requireActor(httpRequest), MembershipRole.OBSERVER);
        return ResponseEntity.ok(membershipService.getRoleForUser(id, userId));
    }

    /**
     * Returns the highest-authority role a user holds across all campaigns.
     * Role requirement: the caller must be authenticated.
     */
    @GetMapping("/members/{userId}/role")
    public ResponseEntity<MembershipRole> getHighestRole(@PathVariable String userId,
                                                       HttpServletRequest httpRequest) {
        requireActor(httpRequest);
        return ResponseEntity.ok(membershipService.getHighestRole(userId));
    }

    /**
     * Authorization check endpoint: returns {@code true} when the given user may
     * perform an action requiring {@code requiredRole} in the campaign.
     * Role requirement: the caller must be authenticated.
     */
    @GetMapping("/{id}/members/{userId}/authorize")
    public ResponseEntity<Boolean> authorize(@PathVariable String id,
                                             @PathVariable String userId,
                                             @RequestParam String requiredRole,
                                             HttpServletRequest httpRequest) {
        requireActor(httpRequest);
        boolean authorized = membershipService.authorizeAs(id, userId, MembershipRole.parse(requiredRole));
        return ResponseEntity.ok(authorized);
    }

    /**
     * Lists the members of a campaign. Role requirement: the authenticated     * caller must hold at least the {@code OBSERVER} role (membership) in the     * campaign.
     */
    @GetMapping("/{id}/members")
    public ResponseEntity<List<MembershipDto>> listMembers(@PathVariable String id,
                                                       HttpServletRequest httpRequest) {
        membershipService.assertAuthorized(id, requireActor(httpRequest), MembershipRole.OBSERVER);
        return ResponseEntity.ok(membershipService.listMembers(id));
    }

    /**
     * Maps {@code IllegalArgumentException} for missing memberships to a
     * {@code 404 Not Found} response in the consistent API error format. This
     * controller-level handler takes precedence over the global handler for
     * exceptions thrown within this controller.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    @Order(2)
    public ResponseEntity<ErrorResponse> handleNotFound(IllegalArgumentException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(buildNotFound(ex.getMessage(), request));
    }

    /**
     * Resolves the user performing an action from the authenticated session
     * context established by the auth filter.
     */
    private String currentActor(HttpServletRequest request) {
        Object attr = request.getAttribute(AuthFilter.AUTH_USER_ATTR);
        if (attr instanceof String s && !s.isBlank()) {
            return s;
        }
        return null;
    }

    /**
     * Resolves the authenticated actor or throws an authorization denial.
     * Used by operations that only require an authenticated caller.
     */
    private String requireActor(HttpServletRequest request) {
        String actor = currentActor(request);
        if (actor != null) {
            return actor;
        }
        throw new com.gamemasterx.server.exception.AuthorizationException(
                MembershipRole.OBSERVER, "Authentication required to perform this action");
    }

    private static String getCorrelationId(HttpServletRequest request) {
        Object attr = request.getAttribute(CORRELATION_ID_ATTRIBUTE);
        if (attr instanceof String s && !s.isBlank()) {
            return s;
        }
        return UUID.randomUUID().toString();
    }

    private static ErrorResponse buildNotFound(String message, HttpServletRequest request) {
        List<FieldError> fieldErrors = new ArrayList<>();
        return new ErrorResponse("NOT_FOUND",
                message == null ? "The requested membership was not found" : message,
                getCorrelationId(request), fieldErrors);
    }
}
