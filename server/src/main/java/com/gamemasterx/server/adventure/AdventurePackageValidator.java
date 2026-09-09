package com.gamemasterx.server.adventure;

import com.gamemasterx.server.adventure.model.Adventure;
import com.gamemasterx.server.adventure.model.Adventure.Branch;
import com.gamemasterx.server.adventure.model.Adventure.Chapter;
import com.gamemasterx.server.adventure.model.Adventure.Creature;
import com.gamemasterx.server.adventure.model.Adventure.Location;
import com.gamemasterx.server.adventure.model.Adventure.Npc;
import com.gamemasterx.server.adventure.model.Adventure.Objective;
import com.gamemasterx.server.adventure.model.Adventure.Reward;
import com.gamemasterx.server.adventure.model.Adventure.SecretNote;
import com.gamemasterx.server.adventure.model.Adventure.Scene;
import com.gamemasterx.server.adventure.model.Adventure.Tag;
import com.gamemasterx.server.adventure.model.Adventure.WorldFact;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Deterministic validation for a parsed local adventure package, expressed as an
 * {@link Adventure} tree read from the package manifest.
 *
 * <p>Every rule enforced here is fully deterministic: the same inputs always
 * produce the same verdict and, on failure, the same location-specific message.
 * This is what makes package validation reproducible rather than depending on
 * incidental ordering or environment.</p>
 *
 * <p>The rules enforced by {@link #validate(Adventure)} are:</p>
 * <ul>
 *   <li><b>Root identifiers &amp; required fields</b> — the adventure
 *       {@code id}, {@code title} and {@code gameSystem} must be present and
 *       non-blank. The {@code id} must be a stable, import-safe identifier
 *       (lower-case letters, digits, hyphens and underscores, length
 *       {@code [1, 128]}).</li>
 *   <li><b>Uniqueness</b> — every identifier across the whole aggregate
 *       (chapters, scenes, locations, NPCs, creatures, objectives, branches,
 *       rewards, secret notes, world facts, tags) must be unique. Duplicate
 *       identifiers within a package are a structural collision.</li>
 *   <li><b>Referential integrity</b> — cross-references stored as stable IDs
 *       must resolve: a scene's {@code locationId}/{@code objectiveId} must name
 *       an existing location/objective, its {@code npcIds}/{@code creatureIds}/
 *       {@code branchIds} must each name an existing entry, and a branch's
 *       {@code fromSceneIds}/{@code consequenceSceneId} must name existing
 *       scenes.</li>
 *   <li><b>Enumerated &amp; numeric fields</b> — scene/chapter orders and
 *       counts must be non-negative, the recommended player count (when set)
 *       must be positive, and secret-note identifiers must be present.</li>
 * </ul>
 *
 * <p>Only the sections that are actually present ({@code non-null}) are
 * validated, so a partial package does not trigger spurious failures.</p>
 */
public final class AdventurePackageValidator {

    private AdventurePackageValidator() {
    }

    /** Lowest legal length for an imported adventure identifier. */
    public static final int MIN_ID_LENGTH = 1;
    /** Highest legal length for an imported adventure identifier. */
    public static final int MAX_ID_LENGTH = 128;

    // Conservative identifier pattern: stable slugs only.
    private static final String ID_PATTERN = "^[a-z0-9][a-z0-9_-]{0," + (MAX_ID_LENGTH - 1) + "}$";

    /**
     * Validates every present section of the parsed package adventure.
     *
     * @param adventure the fully assembled adventure read from the package
     * @throws AdventurePackageImportException if any rule is violated
     */
    public static void validate(Adventure adventure) {
        validateRoot(adventure);
        validateUniqueness(adventure);
        validateReferentialIntegrity(adventure);
        validateCounts(adventure);
    }

    private static void validateRoot(Adventure adventure) {
        String id = adventure.getId();
        if (id == null || id.isBlank()) {
            throw new AdventurePackageImportException("id", "Adventure id must not be empty");
        }
        if (id.length() < MIN_ID_LENGTH || id.length() > MAX_ID_LENGTH
                || !id.matches(ID_PATTERN)) {
            throw new AdventurePackageImportException(
                    "id",
                    "Adventure id must be between " + MIN_ID_LENGTH + " and " + MAX_ID_LENGTH
                            + " characters, using only lower-case letters, digits, hyphens and underscores "
                            + "(was '" + id + "')");
        }

        requireNonBlank(adventure.getTitle(), "title");
        requireNonBlank(adventure.getGameSystem(), "gameSystem");

        Integer players = adventure.getRecommendedPlayerCount();
        if (players != null && players <= 0) {
            throw new AdventurePackageImportException(
                    "recommendedPlayerCount",
                    "Recommended player count must be positive when set (was " + players + ")");
        }

        if (adventure.getStatus() == null) {
            throw new AdventurePackageImportException(
                    "status", "Adventure status must be one of DRAFT, PLAYTEST, PUBLISHED, ARCHIVED");
        }
    }

    private static void validateUniqueness(Adventure adventure) {
        Set<String> seen = new HashSet<>();
        int index = 0;

        for (Chapter chapter : adventure.getChapters()) {
            String chapterId = requireId(chapter, "chapter", index);
            ensureUnique(seen, chapterId, "chapter");
            int sceneIndex = 0;
            for (Scene scene : chapter.getScenes()) {
                String sceneId = requireId(scene, "scene", sceneIndex);
                ensureUnique(seen, sceneId, "scene");
                sceneIndex++;
            }
            index++;
        }

        index = 0;
        for (Location location : adventure.getLocations()) {
            String locationId = requireId(location, "location", index);
            ensureUnique(seen, locationId, "location");
            index++;
        }

        index = 0;
        for (Npc npc : adventure.getNpcs()) {
            String npcId = requireId(npc, "npc", index);
            ensureUnique(seen, npcId, "npc");
            index++;
        }

        index = 0;
        for (Creature creature : adventure.getCreatures()) {
            String creatureId = requireId(creature, "creature", index);
            ensureUnique(seen, creatureId, "creature");
            index++;
        }

        index = 0;
        for (Objective objective : adventure.getObjectives()) {
            String objectiveId = requireId(objective, "objective", index);
            ensureUnique(seen, objectiveId, "objective");
            index++;
        }

        index = 0;
        for (Branch branch : adventure.getBranches()) {
            String branchId = requireId(branch, "branch", index);
            ensureUnique(seen, branchId, "branch");
            index++;
        }

        index = 0;
        for (Reward reward : adventure.getRewards()) {
            String rewardId = requireId(reward, "reward", index);
            ensureUnique(seen, rewardId, "reward");
            index++;
        }

        index = 0;
        for (SecretNote secret : adventure.getSecretNotes()) {
            String secretId = requireId(secret, "secretNote", index);
            ensureUnique(seen, secretId, "secretNote");
            requireNonBlank(secret.getIdentifier(), "secretNote.identifier");
            index++;
        }

        index = 0;
        for (WorldFact fact : adventure.getWorldFacts()) {
            String factId = requireId(fact, "worldFact", index);
            ensureUnique(seen, factId, "worldFact");
            index++;
        }

        index = 0;
        for (Tag tag : adventure.getTags()) {
            String tagId = requireId(tag, "tag", index);
            ensureUnique(seen, tagId, "tag");
            index++;
        }
    }

    private static void validateReferentialIntegrity(Adventure adventure) {
        Set<String> locationIds = ids(adventure.getLocations());
        Set<String> npcIds = ids(adventure.getNpcs());
        Set<String> creatureIds = ids(adventure.getCreatures());
        Set<String> objectiveIds = ids(adventure.getObjectives());
        Set<String> branchIds = ids(adventure.getBranches());
        Set<String> sceneIds = new HashSet<>();
        for (Chapter chapter : adventure.getChapters()) {
            for (Scene scene : chapter.getScenes()) {
                sceneIds.add(scene.getId());
            }
        }

        for (Chapter chapter : adventure.getChapters()) {
            for (Scene scene : chapter.getScenes()) {
                if (scene.getLocationId() != null && !scene.getLocationId().isBlank()
                        && !locationIds.contains(scene.getLocationId())) {
                    throw new AdventurePackageImportException(
                            "scenes[].locationId",
                            "Scene references unknown location '" + scene.getLocationId() + "'");
                }
                if (scene.getObjectiveId() != null && !scene.getObjectiveId().isBlank()
                        && !objectiveIds.contains(scene.getObjectiveId())) {
                    throw new AdventurePackageImportException(
                            "scenes[].objectiveId",
                            "Scene references unknown objective '" + scene.getObjectiveId() + "'");
                }
                checkReferences("scenes[].npcIds", scene.getNpcs(), npcIds, "NPC");
                checkReferences("scenes[].creatureIds", scene.getCreatures(), creatureIds, "creature");
                checkReferences("scenes[].branchIds", scene.getBranches(), branchIds, "branch");
            }
        }

        for (Branch branch : adventure.getBranches()) {
            if (branch.getConsequenceSceneId() != null && !branch.getConsequenceSceneId().isBlank()
                    && !sceneIds.contains(branch.getConsequenceSceneId())) {
                throw new AdventurePackageImportException(
                        "branches[].consequenceSceneId",
                        "Branch references unknown scene '" + branch.getConsequenceSceneId() + "'");
            }
            checkReferences("branches[].fromSceneIds", branch.getFromSceneIds(), sceneIds, "scene");
        }
    }

    private static void validateCounts(Adventure adventure) {
        int index = 0;
        for (Chapter chapter : adventure.getChapters()) {
            if (chapter.getOrder() < 0) {
                throw new AdventurePackageImportException(
                        "chapters[" + index + "].order",
                        "Chapter order must not be negative (was " + chapter.getOrder() + ")");
            }
            int sceneIndex = 0;
            for (Scene scene : chapter.getScenes()) {
                if (scene.getOrder() < 0) {
                    throw new AdventurePackageImportException(
                            "chapters[" + index + "].scenes[" + sceneIndex + "].order",
                            "Scene order must not be negative (was " + scene.getOrder() + ")");
                }
                sceneIndex++;
            }
            index++;
        }

        index = 0;
        for (Reward reward : adventure.getRewards()) {
            if (reward.getExperiencePoints() < 0) {
                throw new AdventurePackageImportException(
                        "rewards[" + index + "].experiencePoints",
                        "Reward experience points must not be negative (was "
                                + reward.getExperiencePoints() + ")");
            }
            index++;
        }
    }

    private static void checkReferences(String field, List<String> references, Set<String> known, String label) {
        if (references == null) {
            return;
        }
        for (String ref : references) {
            if (ref != null && !ref.isBlank() && !known.contains(ref)) {
                throw new AdventurePackageImportException(
                        field,
                        "Reference to unknown " + label + " '" + ref + "'");
            }
        }
    }

    private static Set<String> ids(List<?> entries) {
        Set<String> result = new HashSet<>();
        for (Object entry : entries) {
            if (entry instanceof Adventure.Chapter c) {
                result.add(c.getId());
            } else if (entry instanceof Adventure.Location l) {
                result.add(l.getId());
            } else if (entry instanceof Adventure.Npc n) {
                result.add(n.getId());
            } else if (entry instanceof Adventure.Creature r) {
                result.add(r.getId());
            } else if (entry instanceof Adventure.Objective o) {
                result.add(o.getId());
            } else if (entry instanceof Adventure.Branch b) {
                result.add(b.getId());
            } else if (entry instanceof Adventure.Reward w) {
                result.add(w.getId());
            } else if (entry instanceof Adventure.SecretNote s) {
                result.add(s.getId());
            } else if (entry instanceof Adventure.WorldFact f) {
                result.add(f.getId());
            } else if (entry instanceof Adventure.Tag t) {
                result.add(t.getId());
            } else if (entry instanceof Adventure.Scene sc) {
                result.add(sc.getId());
            }
        }
        return result;
    }

    private static String requireId(Object entry, String label, int index) {
        String resolved;
        if (entry instanceof Adventure.Chapter c) {
            resolved = c.getId();
        } else if (entry instanceof Adventure.Location l) {
            resolved = l.getId();
        } else if (entry instanceof Adventure.Npc n) {
            resolved = n.getId();
        } else if (entry instanceof Adventure.Creature r) {
            resolved = r.getId();
        } else if (entry instanceof Adventure.Objective o) {
            resolved = o.getId();
        } else if (entry instanceof Adventure.Branch b) {
            resolved = b.getId();
        } else if (entry instanceof Adventure.Reward w) {
            resolved = w.getId();
        } else if (entry instanceof Adventure.SecretNote s) {
            resolved = s.getId();
        } else if (entry instanceof Adventure.WorldFact f) {
            resolved = f.getId();
        } else if (entry instanceof Adventure.Tag t) {
            resolved = t.getId();
        } else if (entry instanceof Adventure.Scene sc) {
            resolved = sc.getId();
        } else {
            resolved = null;
        }

        if (resolved == null || resolved.isBlank()) {
            throw new AdventurePackageImportException(
                    label + "[" + index + "].id",
                    label + " is missing an id");
        }
        return resolved;
    }

    private static void ensureUnique(Set<String> seen, String id, String label) {
        if (!seen.add(id)) {
            throw new AdventurePackageImportException(
                    label + ".id",
                    "Duplicate " + label + " identifier '" + id + "' in package");
        }
    }

    private static void requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new AdventurePackageImportException(field, field + " must not be empty");
        }
    }
}
