package com.gamemasterx.server.character;

import com.gamemasterx.server.character.model.Character;
import com.gamemasterx.server.character.model.Character.AbilityModifiers;
import com.gamemasterx.server.character.model.Character.AbilityScores;
import com.gamemasterx.server.character.model.Character.ArmorClass;
import com.gamemasterx.server.character.model.Character.HitPoints;
import com.gamemasterx.server.character.model.Character.InventoryItem;
import com.gamemasterx.server.character.model.Character.Proficiency;
import com.gamemasterx.server.character.model.Character.Resource;

import java.util.List;

/**
 * Deterministic validation for a {@link Character} sheet, expressed using the
 * SRD 5.2 / 2024 terminology and rules.
 *
 * <p>Every rule enforced here is fully deterministic: the same inputs always
 * produce the same verdict and, on failure, the same field-specific message.
 * This is what makes the validation reproducible rather than depending on
 * incidental ordering or environment.</p>
 *
 * <p>The rules enforced by {@link #validate(Character)} are:</p>
 * <ul>
 *   <li><b>Ability scores</b> must be integers in the inclusive range
 *       {@code [1, 30]} (SRD 2024 ability score range).</li>
 *   <li><b>Ability modifiers</b> must equal the deterministic derivation
 *       {@code floor((score - 10) / 2)} of the corresponding score.</li>
 *   <li><b>Level</b> must be an integer in the inclusive range {@code [1, 20]}
 *       (the SRD 2024 maximum character level).</li>
 *   <li><b>Proficiency bonus</b> must equal the bonus dictated by the SRD 2024
 *       proficiency bonus table for the character's level.</li>
 *   <li><b>Hit points</b> must satisfy {@code 0 &lt;= current &lt;= max} with
 *       {@code max &gt;= 0} and {@code temporary &gt;= 0}.</li>
 *   <li><b>Armor class</b> must be an integer in the inclusive range
 *       {@code [1, 30]}.</li>
 *   <li><b>Resource quantities</b> (current and max) must be non-negative
 *       integers.</li>
 *   <li><b>Inventory quantities</b> must be non-negative integers.</li>
 * </ul>
 *
 * <p>Only the sections that are actually present ({@code non-null}) are
 * validated, so partial updates that omit a section do not trigger spurious
 * failures. When {@code abilityScores} is present, its modifiers are validated
 * too; when {@code abilityScores} is absent the modifiers are not validated.</p>
 */
public final class CharacterValidator {

    private CharacterValidator() {
    }

    /** Lowest legal ability score (SRD 2024). */
    public static final int MIN_ABILITY_SCORE = 1;
    /** Highest legal ability score (SRD 2024). */
    public static final int MAX_ABILITY_SCORE = 30;
    /** Lowest legal character level. */
    public static final int MIN_LEVEL = 1;
    /** Highest legal character level (SRD 2024). */
    public static final int MAX_LEVEL = 20;
    /** Lowest legal armor class value. */
    public static final int MIN_ARMOR_CLASS = 1;
    /** Highest legal armor class value. */
    public static final int MAX_ARMOR_CLASS = 30;

    /**
     * Validates every present section of the character sheet.
     *
     * @param character the fully assembled character to validate
     * @throws CharacterSheetValidationException if any present section violates a rule
     */
    public static void validate(Character character) {
        AbilityScores scores = character.getAbilityScores();
        if (scores != null) {
            validateAbilityScores(scores);
            validateAbilityModifiers(scores, character.getAbilityModifiers());
        }

        int level = character.getLevel();
        validateLevel(level);

        Proficiency proficiency = character.getProficiency();
        if (proficiency != null) {
            validateProficiency(proficiency, level);
        }

        HitPoints hitPoints = character.getHitPoints();
        if (hitPoints != null) {
            validateHitPoints(hitPoints);
        }

        ArmorClass armorClass = character.getArmorClass();
        if (armorClass != null) {
            validateArmorClass(armorClass);
        }

        validateResources(character.getResources());
        validateInventory(character.getInventory());
    }

    /**
     * Convenience form used by callers that already hold a level value, so the
     * level is not required to be on the aggregate (for example during a
     * partial update where {@code level} may be {@code null}).
     *
     * @param level   the effective character level (validated to {@code [1, 20]})
     * @param scores  ability scores, may be {@code null}
     * @param modifiers ability modifiers, may be {@code null}
     * @param proficiency proficiency, may be {@code null}
     * @param hitPoints hit points, may be {@code null}
     * @param armorClass armor class, may be {@code null}
     * @param resources resources list, may be {@code null}
     * @param inventory inventory list, may be {@code null}
     */
    public static void validate(
            int level,
            AbilityScores scores,
            AbilityModifiers modifiers,
            Proficiency proficiency,
            HitPoints hitPoints,
            ArmorClass armorClass,
            List<Resource> resources,
            List<InventoryItem> inventory) {

        if (scores != null) {
            validateAbilityScores(scores);
            validateAbilityModifiers(scores, modifiers);
        }
        validateLevel(level);
        if (proficiency != null) {
            validateProficiency(proficiency, level);
        }
        if (hitPoints != null) {
            validateHitPoints(hitPoints);
        }
        if (armorClass != null) {
            validateArmorClass(armorClass);
        }
        validateResources(resources);
        validateInventory(inventory);
    }

    // --- Individual section validation. ---

    private static void validateAbilityScores(AbilityScores scores) {
        check(scores.getStrength(), "strength");
        check(scores.getDexterity(), "dexterity");
        check(scores.getConstitution(), "constitution");
        check(scores.getIntelligence(), "intelligence");
        check(scores.getWisdom(), "wisdom");
        check(scores.getCharisma(), "charisma");
    }

    private static void check(int score, String ability) {
        if (score < MIN_ABILITY_SCORE || score > MAX_ABILITY_SCORE) {
            throw new CharacterSheetValidationException(
                    "abilityScores." + ability,
                    "Ability score '" + ability + "' must be an integer between "
                            + MIN_ABILITY_SCORE + " and " + MAX_ABILITY_SCORE
                            + " (was " + score + ")");
        }
    }

    private static void validateAbilityModifiers(AbilityScores scores, AbilityModifiers modifiers) {
        if (modifiers == null) {
            // Modifiers are derived and recomputed by the aggregate before they
            // are read back; a missing value is not a validation failure.
            return;
        }
        assertModifier("strength", scores.getStrength(), modifiers.getStrength());
        assertModifier("dexterity", scores.getDexterity(), modifiers.getDexterity());
        assertModifier("constitution", scores.getConstitution(), modifiers.getConstitution());
        assertModifier("intelligence", scores.getIntelligence(), modifiers.getIntelligence());
        assertModifier("wisdom", scores.getWisdom(), modifiers.getWisdom());
        assertModifier("charisma", scores.getCharisma(), modifiers.getCharisma());
    }

    private static void assertModifier(String ability, int score, int modifier) {
        int expected = Math.floorDiv(score - 10, 2);
        if (modifier != expected) {
            throw new CharacterSheetValidationException(
                    "abilityModifiers." + ability,
                    "Modifier for '" + ability + "' must be " + fmt(expected)
                            + " (the deterministic derivation floor((" + score
                            + " - 10) / 2)); was " + modifier + "); recompute from the ability scores");
        }
    }

    private static void validateLevel(int level) {
        if (level < MIN_LEVEL || level > MAX_LEVEL) {
            throw new CharacterSheetValidationException(
                    "level",
                    "Level must be an integer between " + MIN_LEVEL + " and " + MAX_LEVEL
                            + " (was " + level + ")");
        }
    }

    private static void validateProficiency(Proficiency proficiency, int level) {
        if (level < MIN_LEVEL || level > MAX_LEVEL) {
            // Reported via validateLevel; avoid duplicating the message.
            return;
        }
        int expected = expectedProficiencyBonus(level);
        int actual = proficiency.getProficiencyBonus();
        if (actual != expected) {
            throw new CharacterSheetValidationException(
                    "proficiency.proficiencyBonus",
                    "Proficiency bonus must be " + fmt(expected) + " for level " + level
                            + " (SRD 2024 proficiency bonus table); was " + actual + ")");
        }
    }

    private static void validateHitPoints(HitPoints hitPoints) {
        int max = hitPoints.getMax();
        int current = hitPoints.getCurrent();
        int temporary = hitPoints.getTemporary();

        if (max < 0) {
            throw new CharacterSheetValidationException(
                    "hitPoints.max", "Max hit points must not be negative (was " + max + ")");
        }
        if (current < 0) {
            throw new CharacterSheetValidationException(
                    "hitPoints.current", "Current hit points must not be negative (was " + current + ")");
        }
        if (current > max) {
            throw new CharacterSheetValidationException(
                    "hitPoints.current",
                    "Current hit points must not exceed max hit points (current " + current
                            + " > max " + max + ")");
        }
        if (temporary < 0) {
            throw new CharacterSheetValidationException(
                    "hitPoints.temporary", "Temporary hit points must not be negative (was " + temporary + ")");
        }
    }

    private static void validateArmorClass(ArmorClass armorClass) {
        int value = armorClass.getValue();
        if (value < MIN_ARMOR_CLASS || value > MAX_ARMOR_CLASS) {
            throw new CharacterSheetValidationException(
                    "armorClass.value",
                    "Armor class must be an integer between " + MIN_ARMOR_CLASS + " and " + MAX_ARMOR_CLASS
                            + " (was " + value + ")");
        }
    }

    private static void validateResources(List<Resource> resources) {
        if (resources == null) {
            return;
        }
        for (int i = 0; i < resources.size(); i++) {
            Resource resource = resources.get(i);
            String label = resource.getName() != null && !resource.getName().isBlank()
                    ? resource.getName()
                    : "#" + i;
            if (resource.getCurrent() < 0) {
                throw new CharacterSheetValidationException(
                        "resources[" + i + "]current",
                        "Resource '" + label + "' current must not be negative (was "
                                + resource.getCurrent() + ")");
            }
            if (resource.getMax() < 0) {
                throw new CharacterSheetValidationException(
                        "resources[" + i + "]max",
                        "Resource '" + label + "' max must not be negative (was "
                                + resource.getMax() + ")");
            }
        }
    }

    private static void validateInventory(List<InventoryItem> inventory) {
        if (inventory == null) {
            return;
        }
        for (int i = 0; i < inventory.size(); i++) {
            InventoryItem item = inventory.get(i);
            String label = item.getName() != null && !item.getName().isBlank()
                    ? item.getName()
                    : "#" + i;
            if (item.getQuantity() < 0) {
                throw new CharacterSheetValidationException(
                        "inventory[" + i + "]quantity",
                        "Inventory item '" + label + "' quantity must not be negative (was "
                                + item.getQuantity() + ")");
            }
        }
    }

    /**
     * Returns the SRD 2024 proficiency bonus for the given level using the
     * standard tiered table:
     *
     * <table border="1">
     *   <caption>Proficiency bonus by level (SRD 2024)</caption>
     *   <tr><th>Level</th><th>Proficiency bonus</th></tr>
     *   <tr><td>1&ndash;4</td><td>+2</td></tr>
     *   <tr><td>5&ndash;8</td><td>+3</td></tr>
     *   <tr><td>9&ndash;12</td><td>+4</td></tr>
     *   <tr><td>13&ndash;16</td><td>+5</td></tr>
     *   <tr><td>17&ndash;20</td><td>+6</td></tr>
     * </table>
     *
     * @param level a level already validated to be within {@code [1, 20]}
     * @return the proficiency bonus for that level
     */
    private static int expectedProficiencyBonus(int level) {
        return switch (level) {
            case 1, 2, 3, 4 -> 2;
            case 5, 6, 7, 8 -> 3;
            case 9, 10, 11, 12 -> 4;
            case 13, 14, 15, 16 -> 5;
            default -> 6; // 17-20
        };
    }

    private static String fmt(int bonus) {
        return (bonus >= 0 ? "+" : "") + bonus;
    }
}
