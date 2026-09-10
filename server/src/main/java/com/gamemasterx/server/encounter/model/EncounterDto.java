package com.gamemasterx.server.encounter.model;

import java.time.Instant;
import java.util.List;

/**
 * API-facing representation of an {@link Encounter}.
 *
 * <p>This DTO is intentionally distinct from the persisted {@link Encounter}
 * MongoDB document entity. It defines only the fields that are safe to expose
 * over the wire and carries the optimistic-concurrency {@code revision} counter
 * out of the API surface. It is used both as the shape returned by the encounter
 * endpoints and as the projection of an {@link Encounter} document into the API
 * contract.</p>
 *
 * <p>The encounter contract additionally exposes the selected {@link RulesProfile},
 * which governs the supported rules subset and the encounter's critical-hit
 * behaviour, and is meaningful to clients.</p>
 */
public class EncounterDto {

    private String id;
    private int schemaVersion;
    private Instant createdAt;
    private Instant updatedAt;
    private String campaignId;
    private String name;
    private EncounterStatus status;
    private RulesProfile rulesProfile;
    private List<ParticipantDto> participants;
    private List<String> initiativeOrder;
    private int round;
    private int turn;
    private int revision;

    public EncounterDto() {
    }

    /**
     * Builds the API representation from a persisted document entity.
     */
    public static EncounterDto from(Encounter encounter) {
        EncounterDto dto = new EncounterDto();
        dto.id = encounter.getId();
        dto.schemaVersion = encounter.getSchemaVersion();
        dto.createdAt = encounter.getCreatedAt();
        dto.updatedAt = encounter.getUpdatedAt();
        dto.campaignId = encounter.getCampaignId();
        dto.name = encounter.getName();
        dto.status = encounter.getStatus();
        dto.rulesProfile = encounter.getRulesProfile();
        dto.round = encounter.getRound();
        dto.turn = encounter.getTurn();
        dto.revision = encounter.getRevision();
        List<ParticipantDto> participants = new java.util.ArrayList<>();
        for (Encounter.Participant p : encounter.getParticipants()) {
            participants.add(toParticipant(p));
        }
        dto.participants = participants;
        dto.initiativeOrder = encounter.getInitiativeOrder();
        return dto;
    }

    private static ParticipantDto toParticipant(Encounter.Participant p) {
        ParticipantDto dto = new ParticipantDto();
        dto.id = p.getId();
        dto.name = p.getName();
        dto.actorControl = p.getActorControl();
        dto.initiative = p.getInitiative();
        dto.movementSpeed = p.getMovementSpeed();
        if (p.getPosition() != null) {
            dto.position = new PositionDto(p.getPosition().getX(), p.getPosition().getY());
        }
        if (p.getHitPoints() != null) {
            dto.hitPoints = new HitPointsDto(
                    p.getHitPoints().getMax(),
                    p.getHitPoints().getCurrent(),
                    p.getHitPoints().getTemporary());
        }
        List<ConditionDto> conditions = new java.util.ArrayList<>();
        for (Encounter.Condition c : p.getConditions()) {
            conditions.add(new ConditionDto(
                    c.getName(),
                    c.getDescription(),
                    c.getAppliesAt(),
                    c.getRoundsRemaining()));
        }
        dto.conditions = conditions;
        List<ResourceDto> resources = new java.util.ArrayList<>();
        for (Encounter.Resource r : p.getResources()) {
            resources.add(new ResourceDto(
                    r.getName(),
                    r.getCurrent(),
                    r.getMax(),
                    r.getDescription()));
        }
        dto.resources = resources;
        return dto;
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

    public List<ParticipantDto> getParticipants() {
        return participants;
    }

    public void setParticipants(List<ParticipantDto> participants) {
        this.participants = participants;
    }

    public List<String> getInitiativeOrder() {
        return initiativeOrder;
    }

    public void setInitiativeOrder(List<String> initiativeOrder) {
        this.initiativeOrder = initiativeOrder;
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

    public int getRevision() {
        return revision;
    }

    public void setRevision(int revision) {
        this.revision = revision;
    }

    /**
     * API representation of a single participant.
     */
    public static class ParticipantDto {
        private String id;
        private String name;
        private ActorControl actorControl;
        private Long initiative;
        private PositionDto position;
        /** The participant's available movement, in grid squares. */
        private int movementSpeed;
        private HitPointsDto hitPoints;
        private List<ConditionDto> conditions;
        private List<ResourceDto> resources;

        public ParticipantDto() {
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

        public int getMovementSpeed() {
            return movementSpeed;
        }

        public void setMovementSpeed(int movementSpeed) {
            this.movementSpeed = movementSpeed;
        }

        public PositionDto getPosition() {
            return position;
        }

        public void setPosition(PositionDto position) {
            this.position = position;
        }

        public HitPointsDto getHitPoints() {
            return hitPoints;
        }

        public void setHitPoints(HitPointsDto hitPoints) {
            this.hitPoints = hitPoints;
        }

        public List<ConditionDto> getConditions() {
            return conditions;
        }

        public void setConditions(List<ConditionDto> conditions) {
            this.conditions = conditions;
        }

        public List<ResourceDto> getResources() {
            return resources;
        }

        public void setResources(List<ResourceDto> resources) {
            this.resources = resources;
        }
    }

    /**
     * API representation of a grid position.
     */
    public static class PositionDto {
        private int x;
        private int y;

        public PositionDto() {
        }

        public PositionDto(int x, int y) {
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
     * API representation of hit points.
     */
    public static class HitPointsDto {
        private int max;
        private int current;
        private int temporary;

        public HitPointsDto() {
        }

        public HitPointsDto(int max, int current, int temporary) {
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
    }

    /**
     * API representation of a condition.
     */
    public static class ConditionDto {
        private String name;
        private String description;
        private Instant appliesAt;
        private Integer roundsRemaining;

        public ConditionDto() {
        }

        public ConditionDto(String name, String description, Instant appliesAt, Integer roundsRemaining) {
            this.name = name;
            this.description = description;
            this.appliesAt = appliesAt;
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
     * API representation of a resource.
     */
    public static class ResourceDto {
        private String name;
        private int current;
        private int max;
        private String description;

        public ResourceDto() {
        }

        public ResourceDto(String name, int current, int max, String description) {
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
}
