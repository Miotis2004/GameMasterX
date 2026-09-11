package com.gamemasterx.server.campaign.poll.controller;

import com.gamemasterx.server.campaign.poll.model.PollCreateRequest;
import com.gamemasterx.server.campaign.poll.model.PollDto;
import com.gamemasterx.server.campaign.poll.model.PollVoteRequest;
import com.gamemasterx.server.campaign.poll.service.PollService;
import com.gamemasterx.server.filter.AuthFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.gamemasterx.server.campaign.membership.model.MembershipRole;

import java.util.List;

/**
 * REST controller for campaign polls.
 *
 * <p>Poll endpoints are authorization-gated. Creation and closure require
 * {@link MembershipRole#GAME_MASTER}; voting requires
 * {@link MembershipRole#PLAYER}; reading requires
 * {@link MembershipRole#OBSERVER}.</p>
 */
@RestController
@RequestMapping("/api/campaigns/{campaignId}/polls")
public class PollController {

    private final PollService pollService;

    public PollController(PollService pollService) {
        this.pollService = pollService;
    }

    /**
     * Creates a new poll for the campaign. Requires GAME_MASTER.
     */
    @PostMapping
    public ResponseEntity<PollDto> createPoll(@PathVariable String campaignId,
                                              @Valid @RequestBody PollCreateRequest request,
                                              HttpServletRequest httpRequest) {
        String actor = requireActor(httpRequest);
        PollDto created = pollService.createPoll(campaignId, actor, request);
        return new ResponseEntity<>(created, HttpStatus.CREATED);
    }

    /**
     * Lists polls for the campaign. Requires OBSERVER.
     */
    @GetMapping
    public ResponseEntity<List<PollDto>> listPolls(@PathVariable String campaignId,
                                                   HttpServletRequest httpRequest) {
        String actor = requireActor(httpRequest);
        return ResponseEntity.ok(pollService.listPolls(campaignId, actor));
    }

    /**
     * Retrieves a single poll. Requires OBSERVER.
     */
    @GetMapping("/{pollId}")
    public ResponseEntity<PollDto> getPoll(@PathVariable String campaignId,
                                           @PathVariable String pollId,
                                           HttpServletRequest httpRequest) {
        String actor = requireActor(httpRequest);
        return ResponseEntity.ok(pollService.getPoll(campaignId, pollId, actor));
    }

    /**
     * Records a vote on a poll. Requires PLAYER.
     */
    @PostMapping("/{pollId}/vote")
    public ResponseEntity<PollDto> vote(@PathVariable String campaignId,
                                         @PathVariable String pollId,
                                         @Valid @RequestBody PollVoteRequest request,
                                         HttpServletRequest httpRequest) {
        String actor = requireActor(httpRequest);
        PollDto updated = pollService.vote(campaignId, pollId, actor, request);
        return ResponseEntity.ok(updated);
    }

    /**
     * Closes a poll. Requires GAME_MASTER.
     */
    @PostMapping("/{pollId}/close")
    public ResponseEntity<PollDto> closePoll(@PathVariable String campaignId,
                                             @PathVariable String pollId,
                                             HttpServletRequest httpRequest) {
        String actor = requireActor(httpRequest);
        PollDto closed = pollService.closePoll(campaignId, pollId, actor);
        return ResponseEntity.ok(closed);
    }

    private String requireActor(HttpServletRequest request) {
        Object attr = request.getAttribute(AuthFilter.AUTH_USER_ATTR);
        if (attr instanceof String s && !s.isBlank()) {
            return s;
        }
        throw new com.gamemasterx.server.exception.AuthorizationException(
                MembershipRole.OBSERVER, "Authentication required to access polls");
    }
}
