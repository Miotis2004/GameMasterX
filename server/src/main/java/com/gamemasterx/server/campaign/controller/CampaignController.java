package com.gamemasterx.server.campaign.controller;

import com.gamemasterx.server.campaign.model.CampaignCreateRequest;
import com.gamemasterx.server.campaign.model.CampaignDto;
import com.gamemasterx.server.campaign.model.CampaignStatus;
import com.gamemasterx.server.campaign.model.CampaignUpdateRequest;
import com.gamemasterx.server.campaign.service.CampaignService;
import com.gamemasterx.server.exception.ErrorResponse;
import com.gamemasterx.server.exception.FieldError;
import com.gamemasterx.server.filter.AuthFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.gamemasterx.server.campaign.membership.model.MembershipRole;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/campaigns")
public class CampaignController {

    private static final String CORRELATION_ID_ATTRIBUTE = "correlationId";

    private final CampaignService campaignService;

    public CampaignController(CampaignService campaignService) {
        this.campaignService = campaignService;
    }

    /**
     * Creates a new campaign. The authenticated caller becomes the campaign
     * {@code OWNER}. Role requirement: the caller must be authenticated
     * (and is granted OWNER on creation).
     */
    @PostMapping
    public ResponseEntity<CampaignDto> createCampaign(@Valid @RequestBody CampaignCreateRequest request,
                                                      HttpServletRequest httpRequest) {
        CampaignDto created = campaignService.createCampaign(request, requireActor(httpRequest));
        return new ResponseEntity<>(created, HttpStatus.CREATED);
    }

    /**
     * Reads a single campaign. Role requirement: at least {@code OBSERVER}
     * (membership) in the campaign.
     */
    @GetMapping("/{id}")
    public ResponseEntity<CampaignDto> getCampaign(@PathVariable String id,
                                                   HttpServletRequest httpRequest) {
        return ResponseEntity.ok(campaignService.findById(id, requireActor(httpRequest)));
    }

    /**
     * Lists campaigns visible to the authenticated caller. Role requirement:
     * the caller must be authenticated.
     */
    @GetMapping
    public ResponseEntity<List<CampaignDto>> listCampaigns(
            @RequestParam(value = "status", required = false) CampaignStatus status,
            HttpServletRequest httpRequest) {
        requireActor(httpRequest);
        return ResponseEntity.ok(campaignService.findByStatus(status));
    }

    /**
     * Updates a campaign. Role requirement: at least {@code GAME_MASTER}
     * in the campaign.
     */
    @PutMapping("/{id}")
    public ResponseEntity<CampaignDto> updateCampaign(@PathVariable String id,
                                                      @Valid @RequestBody CampaignUpdateRequest request,
                                                      HttpServletRequest httpRequest) {
        return ResponseEntity.ok(campaignService.updateCampaign(id, requireActor(httpRequest), request));
    }

    /**
     * Archives a campaign. Role requirement: at least {@code GAME_MASTER}
     * in the campaign.
     */
    @PostMapping("/{id}/archive")
    public ResponseEntity<CampaignDto> archiveCampaign(@PathVariable String id,
                                                       HttpServletRequest httpRequest) {
        return ResponseEntity.ok(campaignService.archiveCampaign(id, requireActor(httpRequest)));
    }

    /**
     * Resolves the authenticated actor from the request context established by     * the auth filter, or throws an authorization denial when the caller is not     * authenticated.
     */
    private String requireActor(HttpServletRequest request) {
        Object attr = request.getAttribute(AuthFilter.AUTH_USER_ATTR);
        if (attr instanceof String s && !s.isBlank()) {
            return s;
        }
        throw new com.gamemasterx.server.exception.AuthorizationException(
                MembershipRole.OBSERVER, "Authentication required to perform this action");
    }

    /**
     * Maps the service's {@code IllegalArgumentException} for a missing
     * campaign to a {@code 404 Not Found} response in the consistent API error
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
                message == null ? "The requested campaign was not found" : message,
                getCorrelationId(request), fieldErrors);
    }
}
