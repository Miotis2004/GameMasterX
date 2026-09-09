package com.gamemasterx.server.campaign.controller;

import com.gamemasterx.server.campaign.model.CampaignDashboardDto;
import com.gamemasterx.server.campaign.service.CampaignDashboardService;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * REST endpoints that expose the campaign dashboard: a single aggregation over a
 * campaign's members (with roles), characters, and selected adventure.
 *
 * <p>Dashboard access is authorized per campaign role. The caller must hold at
 * least the lowest membership role ({@code OBSERVER}) in the campaign, matching
 * the access rules of the campaign and membership read endpoints. Role
 * enforcement is delegated to {@code CampaignDashboardService}, which consults
 * the authenticated actor's {@code MembershipRole} and throws an
 * {@code AuthorizationException} (mapped to {@code 403 Forbidden}) for callers
 * that are not members of the campaign.</p>
 */
@RestController
@RequestMapping("/api/campaigns")
public class CampaignDashboardController {

    private static final String CORRELATION_ID_ATTRIBUTE = "correlationId";

    private final CampaignDashboardService campaignDashboardService;

    public CampaignDashboardController(CampaignDashboardService campaignDashboardService) {
        this.campaignDashboardService = campaignDashboardService;
    }

    /**
     * Reads the dashboard for a single campaign: the campaign itself, the
     * caller's role, the members with their roles and status, the characters
     * associated with the campaign, and the selected adventure when one has been
     * chosen.
     *
     * <p>Role requirement: the caller must hold at least the {@code OBSERVER}
     * role (membership) in the campaign.</p>
     */
    @GetMapping("/{id}/dashboard")
    public ResponseEntity<CampaignDashboardDto> getDashboard(@PathVariable String id,
                                                             HttpServletRequest httpRequest) {
        String actor = requireActor(httpRequest);
        return ResponseEntity.ok(campaignDashboardService.getCampaignDashboard(id, actor));
    }

    /**
     * Maps the service's {@code IllegalArgumentException} for a missing campaign
     * to a {@code 404 Not Found} response in the consistent API error format.
     * This controller-level handler takes precedence over the global handler for
     * exceptions thrown within this controller.
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
        throw new com.gamemasterx.server.exception.AuthorizationException(
                com.gamemasterx.server.campaign.membership.model.MembershipRole.OBSERVER,
                "Authentication required to view this campaign");
    }
}
