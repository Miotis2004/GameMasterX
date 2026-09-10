package com.gamemasterx.server.encounter.model;

import com.gamemasterx.server.encounter.EncounterStateTransitionException;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Encounter aggregate root persisted as a MongoDB document.
 *
 * <p>This is the persistence-only representation of the Encounter aggregate. It
 * is deliberately distinct from {@link EncounterDto}: the document entity is
 * what Spring Data MongoDB reads from and writes to the {@code encounters}
 * collection, while {@link EncounterDto} is the API-facing representation that
 * is returned to (and accepted from) callers. Keeping the two types separate
 * prevents leaking persistence concerns (such as the optimistic-concurrency
 * revision counter) into the API contract.</p>
 *
 * <p>The document carries a stable identifier, a schema version, a revision
 * counter and created/updated timestamps. The {@link #revision} field is also
 * annotated with {@link Version} so that Spring Data MongoDB applies optimistic
 * concurrency control to the aggregate.</p>
 *
 * <p>The encounter is composed of embedded value objects that model the
 * combat/session resolution concerns:
 * <ul>
 *   <li>{@link Participant} &ndash; encounter participants, each carrying its
 *       {@link ActorControl}, {@link #initiative} score, {@link Position},
 *       {@link HitPoints}, {@link Condition}s and {@link Resource}s.</li>
 *   <li>Initiative &ndash; the {@link #initiativeOrder} lists participant ids in
 *       descending initiative order.</li>
 *   <li>Rounds and turns &ndash; {@link #round} is the current round number and
 *       {@link #turn} is the index of the acting participant within
 *       {@link #initiativeOrder} (-1 when no turn is in progress).</li>
 * </ul>
 *
 * <p>Aggregates that are mutable own their own state-machine: the set of legal
 * {@link EncounterStatus} transitions is enforced here by
 * {@link #transitionTo(EncounterStatus)}.</p>
 */
@Document(collection = "encounters")
public class Encounter {

    /** Current schema version for the Encounter document shape. */
    public static final int SCHEMA_VERSION = 1;

    @Id
    private String id;

    /** Logical schema version for this document. */
    private int schemaVersion;

    /**
     * Monotonic revision counter used for optimistic concurrency control.
     * Managed automatically by Spring Data MongoDB because of {@link Version}.
     */
    @Version
    private int revision;

    private Instant createdAt;
    private Instant updatedAt;

    /**
     * Stable identifier of the campaign this encounter belongs to. Every
     * encounter is owned by exactly one campaign.
     */
    private String campaignId;

    private String name;

    /**
     * Current lifecycle state. The set of legal transitions out of each state
     * is enforced by {@link #transitionTo(EncounterStatus)}.
     */
    private EncounterStatus status;

    /**
     * The selected {@link RulesProfile} that governs the supported rules subset
     * for this encounter and, through it, the encounter's critical-hit
     * behaviour. Defaults to {@link RulesProfile#SRD_5_2024} when the encounter
     * is created without an explicit selection; it must always resolve to a
     * supported profile before the encounter is allowed to proceed.
     */
    private RulesProfile rulesProfile;

    /**
     * Encounter participants. Each participant carries its own actor control,
     * initiative score, position, hit points, conditions and resources. The
     * list is the source of truth; {@link #initiativeOrder} is derived from the
     * initiative scores.
     */
    private List<Participant> participants;

    /**
     * Participant identifiers ordered by descending {@link Participant#initiative}.
     * Index {@link #turn} into this list identifies the participant whose turn
     * it currently is.
     */
    private List<String> initiativeOrder;

    /** Current round number, starting at 1 while the encounter is active. */
    private int round;

    /**
     * Index of the acting participant within {@link #initiativeOrder}, or -1
     * when no turn is in progress (for example in the {@link
     * EncounterStatus#DRAFT} or {@link EncounterStatus#COMPLETED} states).
     */
    private int turn;

    public Encounter() {
        this.schemaVersion = SCHEMA_VERSION;
        this.status = EncounterStatus.DRAFT;
        this.rulesProfile = RulesProfile.SRD_5_2024;
        this.participants = new ArrayList<>();
        this.initiativeOrder = new ArrayList<>();
        this.round = 1;
        this.turn = -1;
    }

    /**
     * Full constructor used when materialising an encounter from persistence.
     */
    public Encounter(String id, int schemaVersion, int revision, Instant createdAt, Instant updatedAt,
                     String campaignId, String name, EncounterStatus status, RulesProfile rulesProfile,
                     List<Participant> participants, List<String> initiativeOrder,
                     int round, int turn) {
        this.id = id;
        this.schemaVersion = schemaVersion;
        this.revision = revision;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.campaignId = campaignId;
        this.name = name;
        this.status = (status != null) ? status : EncounterStatus.DRAFT;
        this.rulesProfile = (rulesProfile != null) ? rulesProfile : RulesProfile.SRD_5_2024;
        this.participants = (participants != null) ? participants : new ArrayList<>();
        this.initiativeOrder = (initiativeOrder != null) ? initiativeOrder : new ArrayList<>();
        this.round = round;
        this.turn = turn;
    }

    // --- Lifecycle state machine. ---

    /**
     * Enforces the encounter lifecycle by moving this encounter to
     * {@code target}. If the target equals the current state this is a
     * no-op. Otherwise the transition must be permitted by
     * {@link #allowedTransitions()}; a forbidden transition throws
     * {@link EncounterStateTransitionException} and leaves the state
     * unchanged.
     *
     * @param target the status to move to
     * @throws EncounterStateTransitionException if the transition is not allowed
     */
    public void transitionTo(EncounterStatus target) {
        if (target == null) {
            throw new EncounterStateTransitionException(this.status, null);
        }
        if (target == this.status) {
            return;
        }
        List<EncounterStatus> allowed = allowedTransitions().get(this.status);
        if (allowed == null || !allowed.contains(target)) {
            throw new EncounterStateTransitionException(this.status, target);
        }
        this.status = target;
        if (target == EncounterStatus.ACTIVE && this.turn < 0) {
            // Beginning (or resuming) the encounter positions the initiative at
            // the start of the round.
            this.turn = 0;
        } else if (target == EncounterStatus.DRAFT) {
            // Leaving active play resets the round/turn tracking.
            this.round = 1;
            this.turn = -1;
        }
    }

    /**
     * The set of states that are legal to move to directly from the current
     * state.
     *
     * <p>The lifecycle is:
     * <pre>
     *   DRAFT  --start-->      ACTIVE  --pause-->     PAUSED
     *     ^                     |  \                  |   \
     *     |                    |   \end              |    \end
     *     |                    |    \--> COMPLETED <--|     \--> COMPLETED
     * </pre>
     * {@link #COMPLETED} is terminal.
     */
    public Map<EncounterStatus, List<EncounterStatus>> allowedTransitions() {
        Map<EncounterStatus, List<EncounterStatus>> transitions = new EnumMap<>(EncounterStatus.class);
        transitions.put(EncounterStatus.DRAFT, List.of(EncounterStatus.ACTIVE, EncounterStatus.COMPLETED));
        transitions.put(EncounterStatus.ACTIVE, List.of(EncounterStatus.PAUSED, EncounterStatus.COMPLETED));
        transitions.put(EncounterStatus.PAUSED, List.of(EncounterStatus.ACTIVE, EncounterStatus.COMPLETED));
        transitions.put(EncounterStatus.COMPLETED, List.of());
        return transitions;
    }

    /**
     * @return {@code true} when this encounter is in the terminal
     * {@link EncounterStatus#COMPLETED} state
     */
    public boolean isComplete() {
        return status == EncounterStatus.COMPLETED;
    }

    // --- Initiative / rounds / turns. ---

    /**
     * Recomputes {@link #initiativeOrder} from the current participants'
     * initiative scores. Participants with a {@code null} initiative score are
     * ordered last, stably by their insertion order. This is the authoritative
     * turn order used throughout the encounter.
     */
    public void recomputeInitiativeOrder() {
        List<String> order = new ArrayList<>();
        List<InitiativeEntry> entries = new ArrayList<>();
        int index = 0;
        for (Participant p : participants) {
            Long init = p.getInitiative();
            entries.add(new InitiativeEntry(init == null ? Long.MIN_VALUE : init, p.getId(), index));
            index++;
        }
        // Stable descending sort by initiative, ties broken by insertion order.
        entries.sort((a, b) -> {
            int cmp = Long.compare(b.initiative, a.initiative);
            return (cmp != 0) ? cmp : Integer.compare(a.order, b.order);
        });
        for (InitiativeEntry e : entries) {
            order.add(e.participantId);
        }
        this.initiativeOrder = order;
    }

    /**
     * Advances to the next turn within the current round, wrapping to the
     * start of the next round when the initiative list is exhausted. No-op when
     * the initiative order is empty.
     *
     * @return the id of the participant whose turn it now is
     * @throws IllegalStateException if there is no initiative order to advance
     */
    public String advanceTurn() {
        if (initiativeOrder.isEmpty()) {
            throw new IllegalStateException("Cannot advance turn: initiative order is empty");
        }
        if (turn + 1 >= initiativeOrder.size()) {
            turn = 0;
            round++;
        } else {
            turn++;
        }
        return initiativeOrder.get(turn);
    }

    /**
     * @return the id of the participant whose turn it currently is, or
     * {@code Optional.empty()} when no turn is in progress
     */
    public Optional<String> currentTurnParticipantId() {
        if (turn < 0 || turn >= initiativeOrder.size()) {
            return Optional.empty();
        }
        return Optional.of(initiativeOrder.get(turn));
    }

    // --- Mutators for embedded value objects. ---

    public void setParticipants(List<Participant> participants) {
        this.participants = (participants != null) ? participants : new ArrayList<>();
        this.recomputeInitiativeOrder();
    }

    /**
     * Adds or replaces a participant by its id and refreshes the initiative
     * order so the new participant is placed according to its initiative score.
     */
    public void upsertParticipant(Participant participant) {
        if (participant == null || participant.getId() == null) {
            throw new IllegalArgumentException("Participant must have an id");
        }
        boolean found = false;
        for (int i = 0; i < participants.size(); i++) {
            if (participants.get(i).getId().equals(participant.getId())) {
                participants.set(i, participant);
                found = true;
                break;
            }
        }
        if (!found) {
            participants.add(participant);
        }
        recomputeInitiativeOrder();
    }

    /**
     * Removes the participant with the given id and refreshes the initiative
     * order. No-op when no such participant exists.
     *
     * @param participantId the id of the participant to remove
     * @return {@code true} if a participant was removed
     */
    public boolean removeParticipant(String participantId) {
        boolean removed = participants.removeIf(p -> p.getId().equals(participantId));
        if (removed) {
            recomputeInitiativeOrder();
        }
        return removed;
    }

    public List<Participant> getParticipants() {
        return participants;
    }

    public List<String> getInitiativeOrder() {
        return Collections.unmodifiableList(initiativeOrder);
    }

    public int getRound() {
        return round;
    }

    public void setRound(int round) {
        this.round = round;
    }

    public int getTurn() {
        return turn;
    }

    public void setTurn(int turn) {
        this.turn = turn;
    }

    public String getCampaignId() {
        return campaignId;
    }

    public void setCampaignId(String campaignId) {
        this.campaignId = campaignId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public EncounterStatus getStatus() {
        return status;
    }

    public void setStatus(EncounterStatus status) {
        this.status = status;
    }

    public RulesProfile getRulesProfile() {
        return rulesProfile;
    }

    public void setRulesProfile(RulesProfile rulesProfile) {
        this.rulesProfile = rulesProfile;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public int getSchemaVersion() {
        return schemaVersion;
    }

    public void setSchemaVersion(int schemaVersion) {
        this.schemaVersion = schemaVersion;
    }

    public int getRevision() {
        return revision;
    }

    public void setRevision(int revision) {
        this.revision = revision;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Encounter encounter = (Encounter) o;
        return Objects.equals(id, encounter.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    /**
     * A single encounter participant.
     *
     * <p>A participant models one combatant in the encounter. It carries the
     * {@link ActorControl} deciding who issues actions for it, its
     * {@link #initiative} score, its grid {@link Position}, its {@link
     * HitPoints}, any active {@link Condition}s and its {@link Resource}s (such
     * as spell slots or action charges). Each participant has a stable id that
     * is referenced from {@link #initiativeOrder} and by the current turn.</p>
     */
    public static class Participant {
        private String id;
        private String name;
        private ActorControl actorControl;
        private Long initiative;
        private Position position;
        /**
         * The participant's available movement, expressed in grid squares. Used
         * to validate how far the participant may move on its turn. A value of
         * {@code 0} means no movement has been configured and the participant
         * may not change square.
         */
        private int movementSpeed;
        private HitPoints hitPoints;
        private List<Condition> conditions;
        private List<Resource> resources;
        /**
         * Free-form boolean flags a participant may carry (for example a
         * {@code stealthed} or {@code hidden} state toggled by a {@code
         * SET_STATE} operation). Backed by an insertion-ordered map so the
         * state survives serialization in a stable order.
         */
        private java.util.Map<String, Boolean> states;

        public Participant() {
            this.actorControl = ActorControl.SELF;
            this.conditions = new ArrayList<>();
            this.resources = new ArrayList<>();
            this.states = new java.util.LinkedHashMap<>();
        }

        public Participant(String id, String name, ActorControl actorControl) {
            this.id = id;
            this.name = name;
            this.actorControl = (actorControl != null) ? actorControl : ActorControl.SELF;
            this.conditions = new ArrayList<>();
            this.resources = new ArrayList<>();
            this.states = new java.util.LinkedHashMap<>();
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public ActorControl getActorControl() {
            return actorControl;
        }

        public void setActorControl(ActorControl actorControl) {
            this.actorControl = actorControl;
        }

        public Long getInitiative() {
            return initiative;
        }

        public void setInitiative(Long initiative) {
            this.initiative = initiative;
        }

        public Position getPosition() {
            return position;
        }

        public void setPosition(Position position) {
            this.position = position;
        }

        public int getMovementSpeed() {
            return movementSpeed;
        }

        public void setMovementSpeed(int movementSpeed) {
            this.movementSpeed = movementSpeed;
        }

        public HitPoints getHitPoints() {
            return hitPoints;
        }

        public void setHitPoints(HitPoints hitPoints) {
            this.hitPoints = hitPoints;
        }

        public List<Condition> getConditions() {
            return conditions;
        }

        public void setConditions(List<Condition> conditions) {
            this.conditions = (conditions != null) ? conditions : new ArrayList<>();
        }

        public List<Resource> getResources() {
            return resources;
        }

        public void setResources(List<Resource> resources) {
            this.resources = (resources != null) ? resources : new ArrayList<>();
        }

        /**
         * @return the participant's boolean state flags, never {@code null}
         */
        public java.util.Map<String, Boolean> getStates() {
            return states;
        }

        public void setStates(java.util.Map<String, Boolean> states) {
            this.states = (states != null) ? states : new java.util.LinkedHashMap<>();
        }
    }

    /**
     * A grid position, expressed as integer coordinates. Positions are part of
     * the aggregate model so that movement and reach can be reasoned about
     * against the stored state.
     */
    public static class Position {
        private int x;
        private int y;

        public Position() {
        }

        public Position(int x, int y) {
            this.x = x;
            this.y = y;
        }

        public int getX() {
            return x;
        }

        public void setX(int x) {
            this.x = x;
        }

        public int getY() {
            return y;
        }

        public void setY(int y) {
            this.y = y;
        }
    }

    /**
     * Hit point state for a participant. Follows the same convention as the
     * {@code HitPoints} value object used elsewhere in the aggregate:
     * {@code current} must always be within {@code [0, max]} and
     * {@code temporary} must be non-negative.
     */
    public static class HitPoints {
        private int max;
        private int current;
        private int temporary;

        public HitPoints() {
        }

        public HitPoints(int max, int current, int temporary) {
            this.max = max;
            this.current = current;
            this.temporary = temporary;
        }

        public int getMax() {
            return max;
        }

        public void setMax(int max) {
            this.max = max;
        }

        public int getCurrent() {
            return current;
        }

        public void setCurrent(int current) {
            this.current = current;
        }

        public int getTemporary() {
            return temporary;
        }

        public void setTemporary(int temporary) {
            this.temporary = temporary;
        }

        public int getEffectiveCurrent() {
            return current + temporary;
        }
    }

    /**
     * A condition affecting a participant, such as {@code Prone}, {@code
     * Dazed} or {@code Poisoned}. Conditions carry an optional duration, in
     * rounds, that the game master can decrement as rounds advance.
     */
    public static class Condition {
        private String name;
        private String description;
        private Instant appliesAt;
        /** Rounds of the condition remaining, or {@code null} for indefinite. */
        private Integer roundsRemaining;

        public Condition() {
        }

        public Condition(String name, String description, Integer roundsRemaining) {
            this.name = name;
            this.description = description;
            this.roundsRemaining = roundsRemaining;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public Instant getAppliesAt() {
            return appliesAt;
        }

        public void setAppliesAt(Instant appliesAt) {
            this.appliesAt = appliesAt;
        }

        public Integer getRoundsRemaining() {
            return roundsRemaining;
        }

        public void setRoundsRemaining(Integer roundsRemaining) {
            this.roundsRemaining = roundsRemaining;
        }
    }

    /**
     * A named, possibly limited, resource tracked during the encounter such as
     * spell slots, action charges or hit dice.
     */
    public static class Resource {
        private String name;
        private int current;
        private int max;
        private String description;

        public Resource() {
        }

        public Resource(String name, int current, int max, String description) {
            this.name = name;
            this.current = current;
            this.max = max;
            this.description = description;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getCurrent() {
            return current;
        }

        public void setCurrent(int current) {
            this.current = current;
        }

        public int getMax() {
            return max;
        }

        public void setMax(int max) {
            this.max = max;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }
    }

    /**
     * Internal helper used by {@link #recomputeInitiativeOrder()} to keep a
     * participant's initiative score together with its original insertion order
     * so that the descending sort is stable.
     */
    private static final class InitiativeEntry {
        private final long initiative;
        private final String participantId;
        private final int order;

        private InitiativeEntry(long initiative, String participantId, int order) {
            this.initiative = initiative;
            this.participantId = participantId;
            this.order = order;
        }
    }
}
