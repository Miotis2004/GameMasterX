package com.gamemasterx.server.character.service;

import com.gamemasterx.server.character.model.Character;
import com.gamemasterx.server.character.model.CharacterDto;
import com.gamemasterx.server.character.model.CharacterSheetCreateRequest;
import com.gamemasterx.server.character.model.CharacterSheetUpdateRequest;
import com.gamemasterx.server.character.CharacterValidator;
import com.gamemasterx.server.character.repository.CharacterRepository;
import com.gamemasterx.server.exception.AuthorizationException;
import com.gamemasterx.server.campaign.membership.model.MembershipRole;
import com.gamemasterx.server.campaign.membership.model.MembershipStatus;
import com.gamemasterx.server.campaign.membership.repository.MembershipRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class CharacterService {

    /**
     * Current schema version for the Character document shape. When the
     * persisted shape changes in a backwards-incompatible way this number is
     * bumped so that stored documents can be migrated or rejected.
     */
    private static final int SCHEMA_VERSION = 1;

    private final CharacterRepository characterRepository;
    private final MembershipRepository membershipRepository;

    public CharacterService(CharacterRepository characterRepository, MembershipRepository membershipRepository) {
        this.characterRepository = characterRepository;
        this.membershipRepository = membershipRepository;
    }

    /**
     * Creates a new Character aggregate. The {@code ownerId} is recorded as the
     * character's owner and the ability modifiers are derived from the ability
     * scores on creation. The caller must be authenticated.
     */
    public CharacterDto createCharacter(CharacterSheetCreateRequest request, String ownerId) {
        String name = request.getName();
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Character name must not be blank");
        }
        String owner = requireOwner(ownerId);
        String campaignId = resolveAndValidateCampaign(owner, request.getCampaignId());

        Character character = new Character();
        character.setId(UUID.randomUUID().toString());
        character.setSchemaVersion(SCHEMA_VERSION);
        character.setRevision(1);
        Instant now = Instant.now();
        character.setCreatedAt(now);
        character.setUpdatedAt(now);
        character.setName(name);
        character.setOwnerId(owner);
        character.setCampaignId(campaignId);
        character.setGameSystem(request.getGameSystem());
        character.setLevel(request.getLevel());
        character.setAbilityScores(toAbilityScores(request.getAbilityScores()));
        character.setProficiency(toProficiency(request.getProficiency()));
        character.setHitPoints(toHitPoints(request.getHitPoints()));
        character.setArmorClass(toArmorClass(request.getArmorClass()));
        character.setResources(toResources(request.getResources()));
        character.setInventory(toInventory(request.getInventory()));

        // Keep stored modifiers in sync with the scores.
        character.recomputeAbilityModifiers();

        // Deterministic SRD 5.x validation of the assembled sheet.
        CharacterValidator.validate(character);

        characterRepository.save(character);
        return toDto(character);
    }

    /**
     * Reads a single character the given actor is permitted to access. Access is
     * granted when the actor owns the character, or when the character belongs to
     * a campaign the actor can access (an ACTIVE membership, at least the lowest
     * {@link MembershipRole#OBSERVER} role).
     */
    public CharacterDto findById(String characterId, String actor) {
        requireOwner(actor);
        return toDto(requireAccessibleCharacter(actor, characterId));
    }

    /**
     * Lists every character owned by the given owner. The caller must be
     * authenticated.
     */
    public List<CharacterDto> findByOwner(String actor) {
        requireOwner(actor);
        return characterRepository.findByOwnerId(actor).stream()
                .map(this::toDto)
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * Updates a character sheet. Only non-null fields supplied in the request
     * are changed, allowing partial updates. The ability modifiers are
     * recomputed whenever the ability scores change.
     *
     * <p>The {@link Character#getRevision()} counter is managed automatically by
     * Spring Data MongoDB ({@link org.springframework.data.annotation.Version})
     * for optimistic concurrency control, so it is deliberately left to the
     * persistence layer here.</p>
     */
    public CharacterDto updateCharacter(String id, String actor, CharacterSheetUpdateRequest request) {
        requireOwner(actor);
        Character existing = requireAccessibleCharacter(actor, id);

        if (request.getName() != null && !request.getName().isBlank()) {
            existing.setName(request.getName());
        }
        if (request.getCampaignId() != null) {
            String campaignId = resolveAndValidateCampaign(actor, request.getCampaignId());
            existing.setCampaignId(campaignId);
        }
        if (request.getGameSystem() != null) {
            existing.setGameSystem(request.getGameSystem());
        }
        if (request.getLevel() != null) {
            existing.setLevel(request.getLevel());
        }
        if (request.getAbilityScores() != null) {
            existing.setAbilityScores(toAbilityScores(request.getAbilityScores()));
            existing.recomputeAbilityModifiers();
        }
        if (request.getProficiency() != null) {
            existing.setProficiency(toProficiency(request.getProficiency()));
        }
        if (request.getHitPoints() != null) {
            existing.setHitPoints(toHitPoints(request.getHitPoints()));
        }
        if (request.getArmorClass() != null) {
            existing.setArmorClass(toArmorClass(request.getArmorClass()));
        }
        if (request.getResources() != null) {
            existing.setResources(toResources(request.getResources()));
        }
        if (request.getInventory() != null) {
            existing.setInventory(toInventory(request.getInventory()));
        }

        existing.setUpdatedAt(Instant.now());

        // Deterministic SRD 5.x validation of the assembled sheet. Only the
        // sections actually present in the persisted aggregate are validated, so
        // partial updates that omit a section do not trigger spurious failures.
        CharacterValidator.validate(existing);

        return toDto(characterRepository.save(existing));
    }

    /**
     * Loads a character the given actor is permitted to access: the character's
     * owner, or an authenticated actor who holds an ACTIVE membership in the
     * campaign the character belongs to. Throws {@link IllegalArgumentException}
     * for a missing character and {@link AuthorizationException} when the actor
     * lacks access.
     */
    private Character requireAccessibleCharacter(String actor, String id) {
        Character character = characterRepository.findById(id).orElseThrow(
                () -> new IllegalArgumentException("Character not found: " + id));
        ensureAccess(actor, character);
        return character;
    }

    /**
     * Grants access when the actor owns the character, or when the character is
     * associated with a campaign the actor can access (an ACTIVE membership,
     * at least the lowest {@link MembershipRole#OBSERVER} role). Access is denied
     * otherwise with a consistent {@link AuthorizationException} mapped to a
     * {@code 403 Forbidden} response.
     */
    private void ensureAccess(String actor, Character character) {
        if (actor.equals(character.getOwnerId())) {
            return;
        }
        String campaignId = character.getCampaignId();
        if (campaignId != null && !campaignId.isBlank()
                && membershipRepository
                    .findByCampaignIdAndUserIdAndStatus(campaignId, actor, MembershipStatus.ACTIVE)
                    .isPresent()) {
            return;
        }
        throw new AuthorizationException(MembershipRole.OBSERVER,
                "You do not have access to this character");
    }

    private static String requireOwner(String ownerId) {
        if (ownerId == null || ownerId.isBlank()) {
            throw new AuthorizationException(MembershipRole.OBSERVER,
                    "Authentication required to manage a character");
        }
        return ownerId;
    }

    /**
     * Validates that the supplied campaign identifier is present and that the
     * owner genuinely belongs to that campaign.
     *
     * @param ownerId    the owner identifier, resolved by the caller
     * @param campaignId the campaign identifier supplied by the caller
     * @return the validated, non-blank campaign identifier
     * @throws IllegalArgumentException if the campaign identifier is blank, or if
     *                                the owner is not a member of the campaign
     */
    private String resolveAndValidateCampaign(String ownerId, String campaignId) {
        if (campaignId == null || campaignId.isBlank()) {
            throw new IllegalArgumentException("Campaign identifier must not be blank");
        }
        ensureOwnerBelongsToCampaign(ownerId, campaignId);
        return campaignId;
    }

    /**
     * Asserts that the owner holds an ACTIVE membership in the given campaign.
     * Characters may only be associated with campaigns the owner belongs to.
     *
     * @param ownerId    the owner identifier
     * @param campaignId the campaign identifier
     * @throws IllegalArgumentException if the owner is not a member of the campaign
     */
    private void ensureOwnerBelongsToCampaign(String ownerId, String campaignId) {
        boolean belongs = membershipRepository
                .findByCampaignIdAndUserIdAndStatus(campaignId, ownerId, MembershipStatus.ACTIVE)
                .isPresent();
        if (!belongs) {
            throw new IllegalArgumentException(
                    "Owner " + ownerId + " does not belong to campaign " + campaignId);
        }
    }

    private Character.AbilityScores toAbilityScores(CharacterSheetCreateRequest.AbilityScores s) {
        if (s == null) {
            return null;
        }
        return new Character.AbilityScores(
                s.getStrength(), s.getDexterity(), s.getConstitution(),
                s.getIntelligence(), s.getWisdom(), s.getCharisma());
    }

    private Character.Proficiency toProficiency(CharacterSheetCreateRequest.Proficiency p) {
        if (p == null) {
            return null;
        }
        return new Character.Proficiency(p.getLevel(), p.getProficiencyBonus(), p.getProficiencies());
    }

    private Character.HitPoints toHitPoints(CharacterSheetCreateRequest.HitPoints hp) {
        if (hp == null) {
            return null;
        }
        return new Character.HitPoints(hp.getMax(), hp.getCurrent(), hp.getTemporary());
    }

    private Character.ArmorClass toArmorClass(CharacterSheetCreateRequest.ArmorClass ac) {
        if (ac == null) {
            return null;
        }
        return new Character.ArmorClass(ac.getValue(), ac.getType());
    }

    private List<Character.Resource> toResources(List<CharacterSheetCreateRequest.Resource> rs) {
        if (rs == null) {
            return null;
        }
        List<Character.Resource> result = new java.util.ArrayList<>();
        for (CharacterSheetCreateRequest.Resource r : rs) {
            result.add(new Character.Resource(r.getName(), r.getCurrent(), r.getMax(), r.getDescription()));
        }
        return result;
    }

    private List<Character.InventoryItem> toInventory(List<CharacterSheetCreateRequest.InventoryItem> items) {
        if (items == null) {
            return null;
        }
        List<Character.InventoryItem> result = new java.util.ArrayList<>();
        for (CharacterSheetCreateRequest.InventoryItem i : items) {
            result.add(new Character.InventoryItem(i.getName(), i.getQuantity(), i.getDescription(), i.getWeight()));
        }
        return result;
    }

    /**
     * Projects a persisted document entity into the API-facing DTO. The
     * persistence-only revision counter is intentionally excluded from the API
     * representation.
     */
    private CharacterDto toDto(Character character) {
        return CharacterDto.from(character);
    }
}
