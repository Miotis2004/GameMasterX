package com.gamemasterx.server.narration.service;

import com.gamemasterx.server.adventure.model.Adventure;
import com.gamemasterx.server.adventure.model.Adventure.Objective;
import com.gamemasterx.server.adventure.model.Adventure.Scene;
import com.gamemasterx.server.adventure.model.Adventure.WorldFact;
import com.gamemasterx.server.adventure.repository.AdventureRepository;
import com.gamemasterx.server.character.model.Character;
import com.gamemasterx.server.character.repository.CharacterRepository;
import com.gamemasterx.server.campaign.membership.service.MembershipService;
import com.gamemasterx.server.campaign.model.Campaign;
import com.gamemasterx.server.campaign.repository.CampaignRepository;
import com.gamemasterx.server.encounter.model.Encounter;
import com.gamemasterx.server.encounter.model.Encounter.Participant;
import com.gamemasterx.server.encounter.model.RulesProfile;
import com.gamemasterx.server.encounter.repository.EncounterRepository;
import com.gamemasterx.server.gameplay.model.Turn;
import com.gamemasterx.server.gameplay.repository.TurnRepository;
import com.gamemasterx.server.narration.config.NarrationContextProperties;
import com.gamemasterx.server.narration.model.NarrationContext;
import com.gamemasterx.server.narration.model.NarrationContextBudget;
import com.gamemasterx.server.narration.model.NarrationInput;
import com.gamemasterx.server.narration.model.NarrationLine;
import com.gamemasterx.server.narration.model.NarrationLineKind;
import com.gamemasterx.server.narration.model.NarrationSources;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Assembles the {@link NarrationContext} for a single AI narration completion.
 *
 * <p>This service is the application boundary of the narration bounded context.
 * It is the single place that pulls together the rules profile, campaign,
 * active scene, encounter snapshot, acting character, visible targets, recent
 * narrative, world facts and current objectives, and then splits the result
 * across the two disclosure partitions that the AI layer must never cross.</p>
 *
 * <p>The assembly is guarded by two invariants:</p>
 * <ul>
 *   <li><b>Backend authorization.</b> Assembly first resolves the owning
 *       campaign and asserts that the authenticated caller holds at least the
 *       configured {@link NarrationContextProperties#getRequiredRole()
 *       required role}. Anything below the gate is refused before a single
 *       context line is produced.</li>
 *   <li><b>Strict player-vs-GM separation.</b> Every line the assembler emits
 *       is tagged as either {@link NarrationLineKind#PLAYER_VISIBLE} or
 *       {@link NarrationLineKind#GM_SECRET}. The player-visible lines are the
 *       only lines that may be forwarded to the AI; the GM-only secret lines are
 *       assembled and separated but are never forwarded. The assembled
 *       player-visible partition is additionally bounded in size by the
 *       configured budget.</li>
 * </ul>
 *
 * <p>Every source degrades gracefully: when an identifier is missing or the
 * referenced document does not exist the corresponding lines are simply omitted
 * rather than raising, so a caller may ask for as much or as little context as
 * the current session carries.</p>
 */
@Service
public class NarrationContextAssembler {

    private static final String DEFAULT_INSTRUCTION =
            "Narrate the next moment of play from the players' point of view. Use only "
                    + "the information below, which is what the players can perceive. Do not "
                    + "reveal secrets the characters do not know.";

    private final EncounterRepository encounterRepository;
    private final CampaignRepository campaignRepository;
    private final AdventureRepository adventureRepository;
    private final CharacterRepository characterRepository;
    private final TurnRepository turnRepository;
    private final MembershipService membershipService;
    private final NarrationContextProperties properties;

    public NarrationContextAssembler(EncounterRepository encounterRepository,
                                     CampaignRepository campaignRepository,
                                     AdventureRepository adventureRepository,
                                     CharacterRepository characterRepository,
                                     TurnRepository turnRepository,
                                     MembershipService membershipService,
                                     NarrationContextProperties properties) {
        this.encounterRepository = encounterRepository;
        this.campaignRepository = campaignRepository;
        this.adventureRepository = adventureRepository;
        this.characterRepository = characterRepository;
        this.turnRepository = turnRepository;
        this.membershipService = membershipService;
        this.properties = properties;
    }

    /**
     * Assembles an authorization-checked, size-bounded, player/GM-separated
     * narration context for the given input.
     *
     * <p>The gate runs first: the owning campaign is resolved and the caller's
     * role is checked against the configured required role. Only then are the
     * sources gathered, split across the disclosure partitions, and the
     * player-visible partition bounded.</p>
     *
     * @param input the assembly input carrying the source identifiers, the
     *              completion instruction and the authenticated actor
     * @return the assembled context
     * @throws IllegalArgumentException when a referenced document is missing or a
     *                                 required identifier is blank
     * @throws com.gamemasterx.server.exception.AuthorizationException when the
     *         caller is unauthenticated or does not hold the required role
     * @throws com.gamemasterx.server.narration.model.NarrationContextOverflowException
     *         when the assembled player-visible context exceeds the configured
     *         bound and truncation is disabled
     */
    public NarrationContext assemble(NarrationInput input) {
        String actor = requireActor(input.actor());

        // 1. Resolve the owning campaign and gate assembly on backend
        // authorization against the membership role hierarchy.
        String campaignId = resolveCampaignId(input);
        membershipService.assertAuthorized(campaignId, actor, properties.resolveRequiredRole());

        // 2. Gather every candidate line in a stable, precedence-ordered list.
        List<NarrationLine> lines = new ArrayList<>();
        appendCampaignIdentity(lines, campaignId);
        appendCurrentScene(lines, campaignId, input.currentSceneId());
        appendEncounterSnapshot(lines, input.encounterId());
        appendActingCharacter(lines, input.actingCharacterId());
        appendVisibleTargets(lines, input.visibleTargetCharacterIds());
        appendRecentNarrative(lines, campaignId, input.encounterId());
        appendWorldFacts(lines, campaignId);
        appendObjectives(lines, campaignId);

        // 3. Split across the two disclosure partitions and bound the
        // player-visible one to the configured budget. GM-only secrets are kept
        // in a separate partition and are never forwarded to the AI.
        List<NarrationLine> playerLines = partition(lines, NarrationLineKind.PLAYER_VISIBLE);
        List<NarrationLine> secretLines = partition(lines, NarrationLineKind.GM_SECRET);

        NarrationContextBudget budget = new NarrationContextBudget(
                properties.getMaxContextBytes(), properties.getMaxContextLines(),
                properties.isAllowTruncation());
        NarrationContextBudget.BoundedContext bounded = budget.bind(playerLines);
        List<String> playerVisible = bounded.texts();

        // 4. Record per-source provenance and count the GM-only secret lines
        // contributed by every source. The per-source counts are diagnostic
        // metadata over the pre-partition line list; they never leak data.
        NarrationSources sources = new NarrationSources(
                countPrefix(lines, "Campaign: "),
                countPrefix(lines, "Scene: ") + countPrefix(lines, "GM (NPC intent): "),
                countPrefix(lines, "Game system: ") + countPrefix(lines, "Encounter '"),
                countPrefix(lines, "Your character: "),
                countPrefix(lines, "Visible: "),
                countPrefix(lines, "Recent: "),
                countPrefix(lines, "Known world fact: ") + countPrefix(lines, "Undiscovered world fact: "),
                countPrefix(lines, "Objective [") + countPrefix(lines, "GM secret objective "),
                secretLines.size());

        // 5. Verify that the two partitions are disjoint: no GM-only secret text
        // may appear in the player-visible context that is sent to the AI.
        boolean separationVerified = !overlaps(secretLines, playerLines);

        return new NarrationContext(
                resolveInstruction(input.instruction()),
                playerVisible,
                secretLines.stream().map(NarrationLine::text).toList(),
                sources,
                bounded.totalBytes(),
                bounded.truncated(),
                bounded.truncateNote(),
                separationVerified);
    }

    // --- Source resolvers -------------------------------------------------

    /**
     * Resolves the owning campaign identifier used for authorization and all
     * campaign-scoped source lookups. When an encounter id is supplied the
     * campaign id is taken from the loaded encounter so the caller does not have
     * to carry both; otherwise the supplied campaign id is used and validated.
     */
    private String resolveCampaignId(NarrationInput input) {
        if (input.encounterId() != null && !input.encounterId().isBlank()) {
            Encounter encounter = encounterRepository.findById(input.encounterId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Encounter not found: " + input.encounterId()));
            return requireNonBlank(encounter.getCampaignId(), "campaignId");
        }
        String campaignId = input.campaignId();
        if (campaignId == null || campaignId.isBlank()) {
            throw new IllegalArgumentException(
                    "A campaignId or encounterId is required to assemble a narration context");
        }
        return campaignId;
    }

    private void appendCampaignIdentity(List<NarrationLine> lines, String campaignId) {
        Optional<Campaign> campaign = campaignRepository.findById(campaignId);
        campaign.ifPresent(c -> lines.add(NarrationLine.playerVisible(
                "Campaign: " + c.getName() + " (" + c.getGameSystem() + ")")));
    }

    private void appendCurrentScene(List<NarrationLine> lines, String campaignId, String currentSceneId) {
        Adventure adventure = currentAdventure(campaignId);
        if (adventure == null) {
            return;
        }
        Scene scene = resolveScene(adventure, currentSceneId);
        if (scene == null) {
            return;
        }
        String visible = "Scene: " + scene.getTitle();
        if (scene.getDescription() != null && !scene.getDescription().isBlank()) {
            visible += " - " + scene.getDescription();
        }
        lines.add(NarrationLine.playerVisible(visible));

        // NPC intentions and motivations are GM-only: the players perceive the
        // scene, but not what the NPCs are planning within it.
        for (Adventure.Npc npc : adventure.getNpcs()) {
            if (npc.getGmNotes() == null || npc.getGmNotes().isBlank()) {
                continue;
            }
            lines.add(NarrationLine.gmSecret(
                    "GM (NPC intent): '" + npc.getName() + "' intends: " + npc.getGmNotes()));
        }
    }

    private void appendEncounterSnapshot(List<NarrationLine> lines, String encounterId) {
        if (encounterId == null || encounterId.isBlank()) {
            return;
        }
        Encounter encounter = encounterRepository.findById(encounterId).orElse(null);
        if (encounter == null) {
            return;
        }

        // The rules profile is the game system that governs play: player-visible.
        RulesProfile profile = encounter.getRulesProfile();
        if (profile != null) {
            lines.add(NarrationLine.playerVisible("Game system: " + profile.getRulesSubset()));
        }

        // The encounter as a whole is in progress; this is player-visible.
        if (encounter.getStatus() != null) {
            lines.add(NarrationLine.playerVisible(
                    "Encounter '" + encounter.getName() + "' in progress: round "
                            + encounter.getRound() + ", turn " + (encounter.getTurn() + 1)));
        }

        for (Participant participant : encounter.getParticipants()) {
            boolean visible = isVisibleParticipant(encounter, participant);
            addParticipantLine(lines, participant, visible);
        }
    }

    /**
     * Determines whether a participant is "visible" to the players: the acting
     * character (whose turn it is) is always visible to themselves, and any
     * participant that is also a known player character is treated as a visible
     * ally. Enemies and unknown creatures are not visible, so their true
     * tactical state stays a GM-only secret.
     */
    private boolean isVisibleParticipant(Encounter encounter, Participant participant) {
        List<String> order = encounter.getInitiativeOrder();
        String actingId = (encounter.getTurn() >= 0 && encounter.getTurn() < order.size())
                ? order.get(encounter.getTurn()) : null;
        if (actingId != null && actingId.equals(participant.getId())) {
            return true;
        }
        // A participant that maps onto a known player character is an ally the
        // players control and therefore perceive in full.
        return characterRepository.findAll().stream()
                .anyMatch(c -> participant.getId() != null && participant.getId().equals(c.getId()));
    }

    private void addParticipantLine(List<NarrationLine> lines, Participant participant, boolean visible) {
        String label = "Participant '" + participant.getName() + "'";
        if (participant.getHitPoints() != null) {
            Encounter.HitPoints hp = participant.getHitPoints();
            lines.add(NarrationLine.playerVisible(
                    label + ": " + hp.getCurrent() + "/" + hp.getMax() + " HP"));
        }
        if (participant.getConditions() != null && !participant.getConditions().isEmpty()) {
            String conditions = participant.getConditions().stream()
                    .map(c -> c.getName())
                    .collect(java.util.stream.Collectors.joining(", "));
            if (!conditions.isBlank()) {
                lines.add(NarrationLine.playerVisible(label + ": conditions [" + conditions + "]"));
            }
        }
        if (!visible) {
            // Hidden tactical data the players cannot perceive: initiative, grid
            // position, resources and any other concealed state.
            StringBuilder secret = new StringBuilder("GM (concealed): '" + participant.getName()
                    + "' is concealed — initiative " + participant.getInitiative()
                    + ", position " + position(participant) + ".");
            if (participant.getResources() != null && !participant.getResources().isEmpty()) {
                secret.append(" Hidden resources: ")
                        .append(participant.getResources().stream()
                                .map(r -> r.getName() + " " + r.getCurrent() + "/" + r.getMax())
                                .collect(java.util.stream.Collectors.joining(", ")));
            }
            lines.add(NarrationLine.gmSecret(secret.toString()));
        }
    }

    private String position(Participant participant) {
        Encounter.Position p = participant.getPosition();
        return (p != null) ? "(" + p.getX() + ", " + p.getY() + ")" : "unknown";
    }

    private void appendActingCharacter(List<NarrationLine> lines, String actingCharacterId) {
        if (actingCharacterId == null || actingCharacterId.isBlank()) {
            return;
        }
        Character character = characterRepository.findById(actingCharacterId).orElse(null);
        if (character == null) {
            return;
        }
        String name = character.getName();
        int level = character.getLevel();
        String header = "Your character: " + name + (level > 0 ? " (level " + level + ")" : "");
        lines.add(NarrationLine.playerVisible(header));
        if (character.getHitPoints() != null) {
            lines.add(NarrationLine.playerVisible(
                    "HP: " + character.getHitPoints().getCurrent() + "/" + character.getHitPoints().getMax()));
        }
        if (character.getArmorClass() != null && character.getArmorClass().getValue() > 0) {
            lines.add(NarrationLine.playerVisible("AC: " + character.getArmorClass().getValue()));
        }
        Character.AbilityScores scores = character.getAbilityScores();
        if (scores != null) {
            lines.add(NarrationLine.playerVisible(
                    "Ability scores: STR " + scores.getStrength() + ", DEX " + scores.getDexterity()
                            + ", CON " + scores.getConstitution() + ", INT " + scores.getIntelligence()
                            + ", WIS " + scores.getWisdom() + ", CHA " + scores.getCharisma()));
        }
    }

    private void appendVisibleTargets(List<NarrationLine> lines, List<String> visibleTargetCharacterIds) {
        if (visibleTargetCharacterIds == null || visibleTargetCharacterIds.isEmpty()) {
            return;
        }
        for (String targetId : visibleTargetCharacterIds) {
            if (targetId == null || targetId.isBlank()) {
                continue;
            }
            Character target = characterRepository.findById(targetId).orElse(null);
            if (target == null) {
                continue;
            }
            String header = "Visible: " + target.getName()
                    + (target.getLevel() > 0 ? " (level " + target.getLevel() + ")" : "");
            lines.add(NarrationLine.playerVisible(header));
            if (target.getHitPoints() != null) {
                lines.add(NarrationLine.playerVisible(
                        "HP: " + target.getHitPoints().getCurrent() + "/" + target.getHitPoints().getMax()));
            }
        }
    }

    private void appendRecentNarrative(List<NarrationLine> lines, String campaignId, String encounterId) {
        int turns = properties.getRecentNarrativeTurns();
        if (turns <= 0) {
            return;
        }
        List<Turn> recent = (encounterId != null && !encounterId.isBlank())
                ? turnRepository.findByEncounterIdOrderByRoundAscTurnIndexAsc(encounterId)
                : turnRepository.findByCampaignIdOrderByRoundAscTurnIndexAsc(campaignId);
        if (recent.isEmpty()) {
            return;
        }
        int count = Math.min(turns, recent.size());
        for (int i = recent.size() - count; i < recent.size(); i++) {
            Turn turn = recent.get(i);
            String when = "Round " + turn.round() + ", Turn " + (turn.turnIndex() + 1);
            String actedBy = (turn.actingParticipantId() != null) ? " by " + turn.actingParticipantId() : "";
            StringBuilder summary = new StringBuilder("Recent: " + when + actedBy);
            for (var action : turn.actions()) {
                if (action.note() != null && !action.note().isBlank()) {
                    summary.append("; ").append(action.note());
                }
            }
            lines.add(NarrationLine.playerVisible(summary.toString()));
        }
    }

    private void appendWorldFacts(List<NarrationLine> lines, String campaignId) {
        Adventure adventure = currentAdventure(campaignId);
        if (adventure == null) {
            return;
        }
        int max = properties.getMaxWorldFacts();
        int shown = 0;
        for (WorldFact fact : adventure.getWorldFacts()) {
            if (shown >= max) {
                break;
            }
            if (fact.isPlayerKnown()) {
                lines.add(NarrationLine.playerVisible("Known world fact: " + fact.getStatement()));
            } else {
                lines.add(NarrationLine.gmSecret("Undiscovered world fact: " + fact.getStatement()));
            }
            shown++;
        }
    }

    private void appendObjectives(List<NarrationLine> lines, String campaignId) {
        Adventure adventure = currentAdventure(campaignId);
        if (adventure == null) {
            return;
        }
        int max = properties.getMaxObjectives();
        int shown = 0;
        for (Objective objective : adventure.getObjectives()) {
            if (shown >= max) {
                break;
            }
            String status = objective.isCompleted() ? "(completed)" : "(active)";
            if (objective.getType() == Adventure.ObjectiveType.SECRET) {
                lines.add(NarrationLine.gmSecret(
                        "GM secret objective [" + objective.getType() + "] "
                                + objective.getTitle() + " " + status));
            } else {
                lines.add(NarrationLine.playerVisible(
                        "Objective [" + objective.getType() + "] " + objective.getTitle() + " " + status));
            }
            shown++;
        }
    }

    // --- Helpers ----------------------------------------------------------

    private Adventure currentAdventure(String campaignId) {
        Campaign campaign = campaignRepository.findById(campaignId).orElse(null);
        if (campaign == null || campaign.getAdventureId() == null || campaign.getAdventureId().isBlank()) {
            return null;
        }
        return adventureRepository.findById(campaign.getAdventureId()).orElse(null);
    }

    private Scene resolveScene(Adventure adventure, String currentSceneId) {
        if (currentSceneId != null && !currentSceneId.isBlank()) {
            for (Adventure.Chapter chapter : adventure.getChapters()) {
                for (Scene scene : chapter.getScenes()) {
                    if (scene.getId().equals(currentSceneId)) {
                        return scene;
                    }
                }
            }
            return null;
        }
        // Otherwise surface the adventure's leading scene (first scene of the
        // first chapter), when the adventure has any scenes at all.
        for (Adventure.Chapter chapter : adventure.getChapters()) {
            if (!chapter.getScenes().isEmpty()) {
                return chapter.getScenes().get(0);
            }
        }
        return null;
    }

    private String resolveInstruction(String instruction) {
        if (instruction == null || instruction.isBlank()) {
            return DEFAULT_INSTRUCTION;
        }
        return instruction;
    }

    private List<NarrationLine> partition(List<NarrationLine> lines, NarrationLineKind kind) {
        List<NarrationLine> result = new ArrayList<>();
        for (NarrationLine line : lines) {
            if (line.kind() == kind) {
                result.add(line);
            }
        }
        return result;
    }

    private boolean overlaps(List<NarrationLine> secrets, List<NarrationLine> playerLines) {
        for (NarrationLine secret : secrets) {
            for (NarrationLine player : playerLines) {
                if (secret.text().equals(player.text())) {
                    return true;
                }
            }
        }
        return false;
    }

    private String requireActor(String actor) {
        if (actor == null || actor.isBlank()) {
            throw new com.gamemasterx.server.exception.AuthorizationException(
                    properties.resolveRequiredRole(),
                    "Authentication required to assemble a narration context");
        }
        return actor;
    }

    private static String requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }

    /**
     * Counts the lines in the pre-partition list whose text starts with the
     * given prefix. Used only to produce the diagnostic per-source provenance
     * recorded in {@link NarrationSources}; it never leaks underlying data.
     */
    private int countPrefix(List<NarrationLine> lines, String prefix) {
        int count = 0;
        for (NarrationLine line : lines) {
            if (line.text().startsWith(prefix)) {
                count++;
            }
        }
        return count;
    }
}
