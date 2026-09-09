package com.gamemasterx.server.character.repository;

import com.gamemasterx.server.character.model.Character;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CharacterRepository extends MongoRepository<Character, String> {

    List<Character> findByOwnerId(String ownerId);

    Optional<Character> findByOwnerIdAndId(String ownerId, String id);

    /**
     * Finds every character associated with the given campaign. Used to build
     * a campaign dashboard that aggregates all members' characters.
     *
     * @param campaignId the stable campaign identifier
     * @return every character linked to the campaign (never {@code null})
     */
    List<Character> findByCampaignId(String campaignId);
}
