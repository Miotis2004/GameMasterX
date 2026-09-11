package com.gamemasterx.server.campaign.poll.service;

import com.gamemasterx.server.campaign.membership.model.MembershipRole;
import com.gamemasterx.server.campaign.membership.service.MembershipService;
import com.gamemasterx.server.campaign.poll.model.Poll;
import com.gamemasterx.server.campaign.poll.model.PollCreateRequest;
import com.gamemasterx.server.campaign.poll.model.PollDto;
import com.gamemasterx.server.campaign.poll.model.PollStatus;
import com.gamemasterx.server.campaign.poll.model.PollVoteRequest;
import com.gamemasterx.server.campaign.poll.repository.PollRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Application service for campaign polls.
 *
 * <p>Poll creation and closure require {@link MembershipRole#GAME_MASTER}
 * authority. Voting requires {@link MembershipRole#PLAYER} authority. All
 * operations are authorization-gated via {@link MembershipService}.</p>
 */
@Service
public class PollService {

    private static final int SCHEMA_VERSION = 1;

    private final PollRepository pollRepository;
    private final MembershipService membershipService;

    public PollService(PollRepository pollRepository, MembershipService membershipService) {
        this.pollRepository = pollRepository;
        this.membershipService = membershipService;
    }

    /**
     * Creates a new poll for a campaign. The caller must hold at least
     * {@link MembershipRole#GAME_MASTER} in the campaign.
     */
    public PollDto createPoll(String campaignId, String actor, PollCreateRequest request) {
        membershipService.assertAuthorized(campaignId, actor, MembershipRole.GAME_MASTER);

        if (request.getQuestion() == null || request.getQuestion().isBlank()) {
            throw new IllegalArgumentException("Poll question must not be empty");
        }
        if (request.getOptions() == null || request.getOptions().size() < 2) {
            throw new IllegalArgumentException("Poll must have at least two options");
        }

        Instant now = Instant.now();
        Poll poll = new Poll();
        poll.setId(UUID.randomUUID().toString());
        poll.setSchemaVersion(SCHEMA_VERSION);
        poll.setRevision(1);
        poll.setCreatedAt(now);
        poll.setUpdatedAt(now);
        poll.setCampaignId(campaignId);
        poll.setQuestion(request.getQuestion());
        poll.setOptions(request.getOptions());
        poll.setVotes(new HashMap<>());
        poll.setStatus(PollStatus.OPEN);
        poll.setCreatedBy(actor);

        Poll saved = pollRepository.save(poll);
        return PollDto.from(saved);
    }

    /**
     * Lists all polls for a campaign. The caller must be a member of the
     * campaign ({@link MembershipRole#OBSERVER} minimum).
     */
    public List<PollDto> listPolls(String campaignId, String actor) {
        membershipService.assertAuthorized(campaignId, actor, MembershipRole.OBSERVER);
        List<Poll> polls = pollRepository.findByCampaignId(campaignId);
        return polls.stream().map(PollDto::from).collect(Collectors.toList());
    }

    /**
     * Retrieves a single poll. The caller must be a member of the campaign.
     */
    public PollDto getPoll(String campaignId, String pollId, String actor) {
        membershipService.assertAuthorized(campaignId, actor, MembershipRole.OBSERVER);
        Poll poll = requirePoll(campaignId, pollId);
        return PollDto.from(poll);
    }

    /**
     * Records a vote for a poll. The caller must hold at least
     * {@link MembershipRole#PLAYER} in the campaign and the poll must be open.
     */
    public PollDto vote(String campaignId, String pollId, String actor, PollVoteRequest request) {
        membershipService.assertAuthorized(campaignId, actor, MembershipRole.PLAYER);
        Poll poll = requirePoll(campaignId, pollId);

        if (poll.getStatus() != PollStatus.OPEN) {
            throw new IllegalArgumentException("Poll is not open for voting");
        }
        int optionIndex = request.getOptionIndex();
        if (optionIndex < 0 || optionIndex >= poll.getOptions().size()) {
            throw new IllegalArgumentException("Invalid option index");
        }

        Map<String, Integer> votes = poll.getVotes();
        if (votes == null) {
            votes = new HashMap<>();
        }
        votes.put(actor, optionIndex);
        poll.setVotes(votes);
        poll.setRevision(poll.getRevision() + 1);
        poll.setUpdatedAt(Instant.now());

        Poll saved = pollRepository.save(poll);
        return PollDto.from(saved);
    }

    /**
     * Closes a poll. The caller must hold at least
     * {@link MembershipRole#GAME_MASTER} in the campaign.
     */
    public PollDto closePoll(String campaignId, String pollId, String actor) {
        membershipService.assertAuthorized(campaignId, actor, MembershipRole.GAME_MASTER);
        Poll poll = requirePoll(campaignId, pollId);

        if (poll.getStatus() == PollStatus.CLOSED) {
            throw new IllegalArgumentException("Poll is already closed");
        }
        poll.setStatus(PollStatus.CLOSED);
        poll.setRevision(poll.getRevision() + 1);
        poll.setUpdatedAt(Instant.now());

        Poll saved = pollRepository.save(poll);
        return PollDto.from(saved);
    }

    private Poll requirePoll(String campaignId, String pollId) {
        Poll poll = pollRepository.findByIdAndCampaignId(pollId, campaignId);
        if (poll == null) {
            throw new IllegalArgumentException("Poll not found: " + pollId);
        }
        return poll;
    }
}
