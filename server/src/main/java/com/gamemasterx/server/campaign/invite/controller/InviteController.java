package com.gamemasterx.server.campaign.invite.controller;

import com.gamemasterx.server.campaign.invite.model.InviteCreationRequest;
import com.gamemasterx.server.campaign.invite.model.InviteDto;
import com.gamemasterx.server.campaign.invite.model.RedeemInviteRequest;
import com.gamemasterx.server.campaign.invite.service.InviteService;
import com.gamemasterx.server.campaign.membership.model.MembershipDto;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * REST endpoints for issuing invites and local join codes for campaigns and
 * for redeeming them to create pending or active memberships.
 *
 * <p>Issuing an invite or generating a join code requires the actor to hold at
 * least the {@code GAME_MASTER} role in the campaign, enforced server-side by
 * {@link InviteService}. Redeeming an authenticated actor's own code uses their
 * authenticated user identity unless an explicit {@code userId} is supplied.</p>
 */
@RestController
@RequestMapping("/api/invites")
public class InviteController {

    private static final String CORRELATION_ID_ATTRIBUTE = "correlationId";

    private final InviteService inviteService;

    public InviteController(InviteService inviteService) {
        this.inviteService = inviteService;
    }

    /**
     * Issues a targeted invite for a campaign. The authenticated user must hold
     * at least the GAME_MASTER role.
     */
    @PostMapping("/{campaignId}/invites")
    public ResponseEntity<InviteDto> issueInvite(@PathVariable String campaignId,
                                                 @RequestBody InviteCreationRequest request,
                                                 HttpServletRequest httpRequest) {
        InviteDto created = inviteService.issueInvite(campaignId, currentActor(httpRequest), request);
        return new ResponseEntity<>(created, HttpStatus.CREATED);
    }

    /**
     * Generates a local join code for a campaign. The authenticated user must
     * hold at least the GAME_MASTER role.
     */
    @PostMapping("/{campaignId}/join-codes")
    public ResponseEntity<InviteDto> generateJoinCode(@PathVariable String campaignId,
                                                      @RequestBody InviteCreationRequest request,
                                                      HttpServletRequest httpRequest) {
        InviteDto created = inviteService.generateJoinCode(campaignId, currentActor(httpRequest), request);
        return new ResponseEntity<>(created, HttpStatus.CREATED);
    }

    /**
     * Lists all invites issued for a campaign.
     */
    @GetMapping("/campaigns/{campaignId}")
    public ResponseEntity<List<InviteDto>> listInvites(@PathVariable String campaignId) {
        return ResponseEntity.ok(inviteService.listInvites(campaignId));
    }

    /**
     * Views a single invite by its code.
     */
    @GetMapping("/codes/{code}")
    public ResponseEntity<InviteDto> getInvite(@PathVariable String code) {
        return ResponseEntity.ok(inviteService.getInvite(code));
    }

    /**
     * Redeems an invite by its code, creating an active membership.
     */
    @PostMapping("/{code}/redeem")
    public ResponseEntity<MembershipDto> redeemInvite(@PathVariable String code,
                                                      @RequestBody(required = false) RedeemInviteRequest request,
                                                      HttpServletRequest httpRequest) {
        MembershipDto membership = inviteService.redeem(code, redeemerUserId(request, httpRequest));
        return new ResponseEntity<>(membership, HttpStatus.CREATED);
    }

    /**
     * Redeems a local join code by its code, creating a pending membership.
     */
    @RequestMapping(method = RequestMethod.POST, path = "/join-codes/{code}/redeem")
    public ResponseEntity<MembershipDto> redeemJoinCode(@PathVariable String code,
                                                        @RequestBody(required = false) RedeemInviteRequest request,
                                                        HttpServletRequest httpRequest) {
        MembershipDto membership = inviteService.redeem(code, redeemerUserId(request, httpRequest));
        return new ResponseEntity<>(membership, HttpStatus.CREATED);
    }

    /**
     * Revokes a pending invite by its code.
     */
    @RequestMapping(method = RequestMethod.DELETE, path = "/{code}")
    public ResponseEntity<Void> revokeInvite(@PathVariable String code,
                                             @RequestParam String campaignId,
                                             HttpServletRequest httpRequest) {
        inviteService.revokeInvite(campaignId, code, currentActor(httpRequest));
        return ResponseEntity.noContent().build();
    }

    /**
     * Resolves the user performing an action: the authenticated user when
     * available, otherwise an explicit identifier from the request body.
     */
    private String currentActor(HttpServletRequest request) {
        Object attr = request.getAttribute(AuthFilter.AUTH_USER_ATTR);
        if (attr instanceof String s && !s.isBlank()) {
            return s;
        }
        return null;
    }

    private String redeemerUserId(RedeemInviteRequest request, HttpServletRequest requestObj) {
        if (request != null && request.getUserId() != null && !request.getUserId().isBlank()) {
            return request.getUserId();
        }
        String actor = currentActor(requestObj);
        if (actor != null) {
            return actor;
        }
        throw new IllegalArgumentException("A redeeming userId is required");
    }

    /**
     * Maps the service's {@code IllegalArgumentException} for unknown/invalid
     * invites to a {@code 404 Not Found} response in the consistent API error
     * format. This controller-level handler takes precedence over the global
     * handler for exceptions thrown within this controller.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    @Order(2)
    public ResponseEntity<ErrorResponse> handleNotFound(IllegalArgumentException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(buildNotFound(ex.getMessage(), request));
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
                message == null ? "The requested invite was not found" : message,
                getCorrelationId(request), fieldErrors);
    }
}
