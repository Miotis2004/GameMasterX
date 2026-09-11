package com.gamemasterx.server.tactical.service;

import com.gamemasterx.server.campaign.membership.service.MembershipService;
import com.gamemasterx.server.campaign.membership.model.MembershipRole;
import com.gamemasterx.server.exception.AuthorizationException;
import com.gamemasterx.server.tactical.model.TacticalMap;
import com.gamemasterx.server.tactical.model.Token;
import com.gamemasterx.server.tactical.repository.TacticalMapRepository;
import com.gamemasterx.server.tactical.validation.TacticalMapValidator;
import com.gamemasterx.server.tactical.validation.TacticalMapValidationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Service for tactical map persistence with validation and ownership enforcement.
 */
@Service
public class TacticalMapService {

    private final TacticalMapRepository repository;
    private final TacticalMapValidator validator;
    private final MembershipService membershipService;

    @Autowired
    public TacticalMapService(TacticalMapRepository repository, TacticalMapValidator validator, MembershipService membershipService) {
        this.repository = repository;
        this.validator = validator;
        this.membershipService = membershipService;
    }

    public TacticalMap create(String campaignId, String name, TacticalMap map, String actorUserId) {
        // Authorization: must be member with at least GAME_MASTER
        membershipService.assertAuthorized(campaignId, actorUserId, MembershipRole.GAME_MASTER);
        map.setCampaignId(campaignId);
        map.setName(name);
        map.setSchemaVersion(TacticalMap.SCHEMA_VERSION);
        Instant now = Instant.now();
        map.setCreatedAt(now);
        map.setUpdatedAt(now);
        validator.validateMap(map);
        return repository.save(map);
    }

    public Optional<TacticalMap> findById(String id) {
        return repository.findById(id);
    }

    public TacticalMap update(String id, TacticalMap updated, String actorUserId) {
        TacticalMap existing = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Tactical map not found"));
        // Authorization check: actor must be GAME_MASTER for the campaign
        membershipService.assertAuthorized(existing.getCampaignId(), actorUserId, MembershipRole.GAME_MASTER);
        // Update fields
        existing.setName(updated.getName() != null ? updated.getName() : existing.getName());
        if (updated.getGridDefinition() != null) {
            existing.setGridDefinition(updated.getGridDefinition());
        }
        if (updated.getTokens() != null) {
            existing.setTokens(updated.getTokens());
        }
        existing.setUpdatedAt(Instant.now());
        validator.validateMap(existing);
        return repository.save(existing);
    }

    /**
     * Enforces token ownership: only the token owner or a GAME_MASTER can modify token.
     */
    public void assertTokenOwnership(Token token, String actorUserId, String campaignId) {
        boolean isOwner = token.getOwnerId() != null && token.getOwnerId().equals(actorUserId);
        boolean isGameMaster = false;
        try {
            membershipService.assertAuthorized(campaignId, actorUserId, MembershipRole.GAME_MASTER);
            isGameMaster = true;
        } catch (AuthorizationException e) {
            // not game master
        }
        if (!isOwner && !isGameMaster) {
            throw new AuthorizationException(MembershipRole.GAME_MASTER, "Token modification not authorized");
        }
    }

    private static final int MAX_MOVEMENT = 5;

    public Token updateToken(String mapId, String tokenId, Token updatedToken, String actorUserId) {
        TacticalMap map = repository.findById(mapId)
                .orElseThrow(() -> new IllegalArgumentException("Map not found"));
        Token existing = null;
        for (Token t : map.getTokens()) {
            if (t.getId().equals(tokenId)) {
                existing = t;
                break;
            }
        }
        if (existing == null) {
            throw new IllegalArgumentException("Token not found");
        }
        assertTokenOwnership(existing, actorUserId, map.getCampaignId());
        // Apply updates
        if (updatedToken.getName() != null) {
            existing.setName(updatedToken.getName());
        }
        // Validate movement distance if coordinates change
        boolean xChanged = updatedToken.getX() != existing.getX();
        boolean yChanged = updatedToken.getY() != existing.getY();
        if (xChanged || yChanged) {
            int newX = updatedToken.getX();
            int newY = updatedToken.getY();
            int distance = Math.abs(newX - existing.getX()) + Math.abs(newY - existing.getY());
            if (distance > MAX_MOVEMENT) {
                throw new IllegalArgumentException("Token movement distance " + distance + " exceeds maximum allowed " + MAX_MOVEMENT);
            }
            // Validate coordinates are within grid bounds
            Token temp = new Token();
            temp.setX(newX);
            temp.setY(newY);
            validator.validateTokenCoordinates(map, temp);
            existing.setX(newX);
            existing.setY(newY);
        }
        if (updatedToken.isHidden() != existing.isHidden()) {
            existing.setHidden(updatedToken.isHidden());
        }
        map.setUpdatedAt(Instant.now());
        validator.validateMap(map);
        repository.save(map);
        return existing;
    }

    public List<RangeCell> computeRangeOverlay(String mapId, String tokenId, int range) {
        TacticalMap map = repository.findById(mapId)
                .orElseThrow(() -> new IllegalArgumentException("Map not found"));
        Token token = null;
        for (Token t : map.getTokens()) {
            if (t.getId().equals(tokenId)) {
                token = t;
                break;
            }
        }
        if (token == null) {
            throw new IllegalArgumentException("Token not found");
        }
        if (range < 0) {
            throw new IllegalArgumentException("Range must not be negative");
        }
        List<RangeCell> cells = new ArrayList<>();
        int originX = token.getX();
        int originY = token.getY();
        int width = map.getGridDefinition().getWidth();
        int height = map.getGridDefinition().getHeight();
        for (int x = Math.max(0, originX - range); x <= Math.min(width - 1, originX + range); x++) {
            for (int y = Math.max(0, originY - range); y <= Math.min(height - 1, originY + range); y++) {
                int chebyshev = Math.max(Math.abs(x - originX), Math.abs(y - originY));
                if (chebyshev <= range) {
                    cells.add(new RangeCell(x, y, chebyshev));
                }
            }
        }
        return cells;
    }

    public TacticalMap filterForUser(TacticalMap map, String userId) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId must be provided");
        }
        boolean isGameMaster = membershipService.authorizeAs(map.getCampaignId(), userId, MembershipRole.GAME_MASTER);
        if (isGameMaster) {
            return map;
        }
        TacticalMap filtered = new TacticalMap();
        filtered.setId(map.getId());
        filtered.setSchemaVersion(map.getSchemaVersion());
        filtered.setRevision(map.getRevision());
        filtered.setCreatedAt(map.getCreatedAt());
        filtered.setUpdatedAt(map.getUpdatedAt());
        filtered.setCampaignId(map.getCampaignId());
        filtered.setName(map.getName());
        filtered.setGridDefinition(map.getGridDefinition());
        List<Token> filteredTokens = new ArrayList<>();
        for (Token t : map.getTokens()) {
            boolean visible = !t.isHidden() || t.getVisibleTo().contains(userId);
            if (visible) {
                filteredTokens.add(t);
            }
        }
        filtered.setTokens(filteredTokens);
        return filtered;
    }

    public static class RangeCell {
        private final int x;
        private final int y;
        private final int distance;

        public RangeCell(int x, int y, int distance) {
            this.x = x;
            this.y = y;
            this.distance = distance;
        }

        public int getX() { return x; }
        public int getY() { return y; }
        public int getDistance() { return distance; }
    }
}
