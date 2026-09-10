package com.gamemasterx.server.dice;

import java.util.ArrayList;
import java.util.List;

/**
 * A parsed, immutable representation of a polyhedral dice expression.
 *
 * <p>A dice expression is an ordered list of one or more {@link DiceGroup}s
 * joined by {@code +}. Each group describes a batch of identical dice &ndash;
 * how many were rolled ({@link DiceGroup#count()}), how many faces each has
 * ({@link DiceGroup#sides()}), an optional {@link Advantage} and a constant
 * {@link DiceGroup#modifier()} added to (or subtracted from) the batch. The
 * overall expression total is the sum of every group's subtotal.</p>
 *
 * <p>The canonical grammar, applied case-insensitively after surrounding
 * whitespace is stripped, is:</p>
 *
 * <pre>
 *   expression := group ('+' group)*
 *   group      := [count] 'd' sides [ advantage ] [ modifier ]
 *   count      := digit+          (defaults to 1 when omitted)
 *   sides      := digit+
 *   advantage  := 'adv' | 'dis' | 'advantage' | 'disadvantage'
 *   modifier   := '+' digit+ | '-' digit+
 * </pre>
 *
 * <p>For example {@code "2d6+3"} parses to a single group
 * ({@code count=2, sides=6, modifier=+3}); {@code "1d20dis"} parses to a single
 * group with {@link Advantage#DISADVANTAGE}. Every expression must contain at
 * least one die group.</p>
 */
public final class DiceExpression {

    /**
     * How advantage or disadvantage was applied to a batch of dice.
     *
     * <ul>
     *   <li>{@link #NONE} &ndash; ordinary roll, every die counts.</li>
     *   <li>{@link #ADVANTAGE} &ndash; each die is rolled twice and the
     *       higher of the pair is kept.</li>
     *   <li>{@link #DISADVANTAGE} &ndash; each die is rolled twice and the
     *       lower of the pair is kept.</li>
     * </ul>
     */
    public enum Advantage {
        NONE,
        ADVANTAGE,
        DISADVANTAGE
    }

    private static final String[] ADVANTAGE_KEYWORDS = {
            "disadvantage", "advantage", "dis", "adv"
    };

    /** Per-group parse state: the produced {@link DiceGroup} and the index in
     * the source string immediately after the group. */
    private static final class ParsedGroup {
        final DiceGroup group;
        final int index;

        ParsedGroup(DiceGroup group, int index) {
            this.group = group;
            this.index = index;
        }
    }

    private final List<DiceGroup> groups;

    /**
     * @param groups the ordered, non-empty list of parsed groups
     * @throws IllegalArgumentException if {@code groups} is {@code null}, empty,
     *                                  or contains a {@code null} element
     */
    public DiceExpression(List<DiceGroup> groups) {
        if (groups == null || groups.isEmpty()) {
            throw new IllegalArgumentException("A dice expression must contain at least one group");
        }
        for (DiceGroup g : groups) {
            if (g == null) {
                throw new IllegalArgumentException("A dice expression must not contain an empty group");
            }
        }
        this.groups = List.copyOf(groups);
    }

    /**
     * @return the immutable, ordered list of groups that make up this expression
     */
    public List<DiceGroup> groups() {
        return groups;
    }

    /**
     * @return {@code true} when the expression contains no groups
     */
    public boolean isEmpty() {
        return groups.isEmpty();
    }

    /**
     * The sum of every group's maximum subtotal.
     *
     * @return {@code sum(group.maximum())} across all groups
     */
    public int maximum() {
        int total = 0;
        for (DiceGroup group : groups) {
            total += group.maximum();
        }
        return total;
    }

    /**
     * Deterministically parse a polyhedral dice expression into a structured
     * {@link DiceExpression}. Parsing is a pure, input-driven transformation:
     * the same non-blank input always yields the identical result, and the same
     * malformed input always fails with the same {@link DiceExpressionException}
     * and message.
     *
     * <p>The canonical grammar, applied case-insensitively after surrounding
     * whitespace is stripped, is exactly that documented on this class:
     * {@code expression := group ('+' group)*}, with each group being
     * {@code [count] 'd' sides [ advantage ] [ modifier ]}.</p>
     *
     * <p>Examples:</p>
     * <ul>
     *   <li>{@code "2d6+3"} &rarr; a single group ({@code count=2, sides=6, modifier=+3});</li>
     *   <li>{@code "  1D20 "} &rarr; a single group ({@code count=1, sides=20});</li>
     *   <li>{@code "1d20dis"} &rarr; a single group with {@link Advantage#DISADVANTAGE};</li>
     *   <li>{@code "1d20+2d6"} &rarr; two groups joined by {@code +}.</li>
     * </ul>
     *
     * @param raw the expression to parse; surrounding whitespace is ignored
     * @return the parsed, immutable {@link DiceExpression}
     * @throws DiceExpressionException if {@code raw} is {@code null} or blank, or
     *                                 if it does not match the grammar or falls
     *                                 outside the permitted numeric ranges
     */
    public static DiceExpression parse(String raw) {
        if (raw == null) {
            throw new DiceExpressionException("expression", "A dice expression is required");
        }
        String s = raw.trim();
        if (s.isEmpty()) {
            throw new DiceExpressionException("expression", "A dice expression is required");
        }

        List<DiceGroup> groups = new ArrayList<>();
        int n = s.length();
        int i = 0;
        boolean first = true;

        while (i < n) {
            char c = s.charAt(i);
            if (c == '+') {
                if (first) {
                    throw new DiceExpressionException("expression",
                            "A dice expression cannot start with '+'");
                }
                i++; // '+' joins two groups
                if (i >= n) {
                    throw new DiceExpressionException("expression",
                            "A dice expression must contain at least one die group");
                }
            } else if (first && (c == '-')) {
                throw new DiceExpressionException("expression",
                        "A dice expression cannot start with '-'");
            }
            first = false;

            ParsedGroup parsed = parseGroup(s, i);
            groups.add(parsed.group);
            i = parsed.index;

            if (i < n && s.charAt(i) != '+') {
                throw new DiceExpressionException("expression",
                        "Unexpected character '" + s.charAt(i) + "' at position " + i);
            }
        }

        return new DiceExpression(groups);
    }

    /**
     * Parse a single {@code group} starting at {@code start}. Expects an
     * optional count, the {@code 'd'} separator, a mandatory face count, an
     * optional advantage keyword and an optional signed modifier. Does not
     * consume a trailing group-separating {@code '+'}.
     *
     * @param s    the source string
     * @param start the index at which the group begins
     * @return the parsed {@link DiceGroup} and the index immediately after it
     * @throws DiceExpressionException when the group does not match the grammar
     */
    private static ParsedGroup parseGroup(String s, int start) {
        int n = s.length();
        int i = start;

        // Optional count (defaults to 1 when omitted).
        int count = 1;
        int countStart = i;
        while (i < n && Character.isDigit(s.charAt(i))) {
            i++;
        }
        if (i > countStart) {
            count = parseIntDigits(s, countStart, i, "count", start);
        }

        // Mandatory 'd' separator.
        if (i >= n || (s.charAt(i) != 'd' && s.charAt(i) != 'D')) {
            throw new DiceExpressionException("expression",
                    "Expected 'd' in die group starting at position " + start);
        }
        i++;

        // Mandatory face count.
        int sidesStart = i;
        while (i < n && Character.isDigit(s.charAt(i))) {
            i++;
        }
        if (i == sidesStart) {
            throw new DiceExpressionException("expression",
                    "A die must specify a number of faces starting at position " + start);
        }
        int sides = parseIntDigits(s, sidesStart, i, "sides", start);

        // Optional advantage/disadvantage keyword (must end at a word boundary).
        Advantage advantage = Advantage.NONE;
        for (String keyword : ADVANTAGE_KEYWORDS) {
            if (matchesKeyword(s, i, keyword)) {
                advantage = switch (keyword) {
                    case "disadvantage", "dis" -> Advantage.DISADVANTAGE;
                    case "advantage", "adv" -> Advantage.ADVANTAGE;
                    default -> Advantage.NONE;
                };
                i += keyword.length();
                break;
            }
        }

        // Optional signed modifier. A trailing '+digits' is a modifier only when
        // the digits are not immediately followed by a 'd'/'D', which would mark
        // them as the count of the next group instead (for example the '+2' in
        // "1d20+2d6" joins two groups rather than modifying the first). A '-digits'
        // is always a modifier because '-' can never introduce a new group.
        int modifier = 0;
        if (i < n) {
            char c = s.charAt(i);
            if (c == '+' || c == '-') {
                int j = i + 1;
                int modStart = j;
                while (j < n && Character.isDigit(s.charAt(j))) {
                    j++;
                }
                boolean looksLikeNextGroup = c == '+' && j < n
                        && (s.charAt(j) == 'd' || s.charAt(j) == 'D');
                if (!looksLikeNextGroup && j != modStart) {
                    int sign = (c == '-') ? -1 : 1;
                    int modValue = parseIntDigits(s, modStart, j, "modifier", start);
                    modifier = sign * modValue;
                    i = j;
                }
                // Otherwise the '+/-digits' was a group separator (or a bare '-');
                // leave i in place so the caller re-dispatches the separator.
            }
        }

        return new ParsedGroup(DiceGroup.of(count, sides, advantage, modifier), i);
    }

    /**
     * @return {@code true} when {@code s} contains {@code keyword} (case
     *         insensitive) at {@code pos} and the keyword is followed by the end
     *         of the string or by a {@code '+'} or {@code '-'} (so it cannot be
     *         the prefix of a longer, non-keyword run of letters)
     */
    private static boolean matchesKeyword(String s, int pos, String keyword) {
        int end = pos + keyword.length();
        if (end > s.length()) {
            return false;
        }
        for (int k = 0; k < keyword.length(); k++) {
            char expected = keyword.charAt(k);
            char actual = Character.toLowerCase(s.charAt(pos + k));
            if (actual != Character.toLowerCase(expected)) {
                return false;
            }
        }
        if (end >= s.length()) {
            return true;
        }
        char next = s.charAt(end);
        return next == '+' || next == '-';
    }

    /**
     * Parse a run of digit characters into an {@link Integer}, reporting an
     * overflow as a {@link DiceExpressionException} rather than a
     * {@link NumberFormatException} so that malformed numeric fields produce a
     * stable, well-formed validation error.
     */
    private static int parseIntDigits(String s, int from, int to, String field, int groupStart) {
        String token = s.substring(from, to);
        try {
            return Integer.parseInt(token);
        } catch (NumberFormatException e) {
            throw new DiceExpressionException("expression",
                    "The " + field + " value '" + token + "' is out of range");
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < groups.size(); i++) {
            if (i > 0) {
                sb.append("+");
            }
            sb.append(groups.get(i));
        }
        return sb.toString();
    }
}
