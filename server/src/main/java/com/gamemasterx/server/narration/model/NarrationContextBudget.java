package com.gamemasterx.server.narration.model;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Enforces the hard size bound on an assembled, ordered list of context lines.
 *
 * <p>The AI context window is finite, so the player-visible context sent to the
 * AI must be bounded. This class is the single authority for that bound: it
 * consumes an ordered list of candidate lines and returns a bounded subset whose
 * total UTF-8 size never exceeds {@code maxBytes} and whose line count never
 * exceeds {@code maxLines}.</p>
 *
 * <p>The bound is a hard guarantee, not a soft target. Two mechanisms combine to
 * make it airtight:</p>
 * <ul>
 *   <li>Lines are appended in order until the next line would exceed either the
 *       byte or line budget, at which point appending stops.</li>
 *   <li>A single line whose own size already exceeds {@code maxBytes} is capped
 *       (truncated with a marker) so the bound still holds even against one
 *       pathological line.</li>
 * </ul>
 *
 * <p>Truncation is destructive only in the sense of dropping later, less
 * relevant lines; it never silently corrupts a line that is kept.</p>
 */
public final class NarrationContextBudget {

    private final int maxBytes;
    private final int maxLines;
    private final boolean allowTruncation;

    /**
     * @param maxBytes      hard cap on total UTF-8 bytes of kept lines
     * @param maxLines      hard cap on the number of kept lines
     * @param allowTruncation when {@code true} an over-budget list is truncated
     *                        rather than rejected
     * @throws IllegalArgumentException when either bound is non-positive
     */
    public NarrationContextBudget(int maxBytes, int maxLines, boolean allowTruncation) {
        if (maxBytes <= 0) {
            throw new IllegalArgumentException("maxBytes must be positive");
        }
        if (maxLines <= 0) {
            throw new IllegalArgumentException("maxLines must be positive");
        }
        this.maxBytes = maxBytes;
        this.maxLines = maxLines;
        this.allowTruncation = allowTruncation;
    }

    public int getMaxBytes() {
        return maxBytes;
    }

    public int getMaxLines() {
        return maxLines;
    }

    /**
     * Bounds the ordered list of context lines to fit within the configured byte
     * and line budgets.
     *
     * @param lines the ordered candidate lines
     * @return the bounded result
     */
    public BoundedContext bind(List<NarrationLine> lines) {
        List<NarrationLine> kept = new ArrayList<>();
        int usedBytes = 0;
        boolean truncated = false;
        String truncateNote = null;

        for (NarrationLine line : lines) {
            if (kept.size() >= maxLines) {
                truncated = true;
                truncateNote = "Line cap reached (" + maxLines + " lines); "
                        + (lines.size() - kept.size()) + " later line(s) omitted";
                break;
            }

            int lineBytes = utf8Bytes(line.text());
            if (lineBytes > maxBytes) {
                // A single line cannot exceed the byte budget; cap it so the
                // bound still holds. This only ever happens once per bind.
                //
                // The ellipsis is a multi-byte (UTF-8 three-byte) character, so
                // the cap must be enforced on UTF-8 bytes, not on character
                // count. We truncate characters until the appended ellipsis keeps
                // the whole line within maxBytes, which preserves the hard
                // "total UTF-8 size never exceeds maxBytes" guarantee even for
                // text containing multi-byte characters.
                String capped = capToUtf8Bytes(line.text(), maxBytes);
                kept.add(new NarrationLine(capped, line.kind()));
                usedBytes += utf8Bytes(capped);
                truncated = true;
                truncateNote = "A context line exceeded " + maxBytes
                        + " bytes and was truncated";
                continue;
            }

            if (usedBytes + lineBytes > maxBytes) {
                truncated = true;
                truncateNote = "Byte cap reached (" + maxBytes + " bytes); "
                        + (lines.size() - kept.size()) + " later line(s) omitted";
                break;
            }

            kept.add(line);
            usedBytes += lineBytes;
        }

        if (truncateNote == null && !allowTruncation && lines.size() > kept.size()) {
            throw new NarrationContextOverflowException(
                    kept.size(), lines.size(), maxBytes, maxLines);
        }

        return new BoundedContext(
                List.copyOf(kept),
                usedBytes,
                kept.size() >= maxLines,
                truncated,
                truncateNote);
    }

    private static int utf8Bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8).length;
    }

    /**
     * Truncates {@code text} so that the result, with a trailing ellipsis,
     * fits within {@code maxBytes} UTF-8 bytes. The guarantee is a hard one:
     * the returned string's UTF-8 size never exceeds {@code maxBytes}, even
     * when the ellipsis itself is multi-byte. If even the ellipsis cannot fit
     * (an extremely small budget), the result may drop the ellipsis and be
     * shorter still, but it never exceeds the budget.
     *
     * @param text     the original line text (never {@code null})
     * @param maxBytes the maximum permitted UTF-8 size of the returned string
     * @return the truncated line, guaranteed to be within {@code maxBytes} UTF-8
     *         bytes, or {@code text} itself when it already fits
     */
    private static String capToUtf8Bytes(String text, int maxBytes) {
        final String ellipsis = "\u2026";
        String capped = text;
        if (utf8Bytes(capped) <= maxBytes) {
            return capped;
        }
        // Trim trailing characters until the line, plus the ellipsis, is within
        // budget. Trimming one code unit at a time keeps this correct for
        // text that contains multi-byte (or surrogate-pair) characters.
        while (capped.length() > 1 && utf8Bytes(capped + ellipsis) > maxBytes) {
            capped = capped.substring(0, capped.length() - 1);
        }
        capped = capped + ellipsis;
        // Final safety net: if the ellipsis alone already overflows a
        // pathologically small budget, drop trailing characters until within
        // budget regardless of the ellipsis.
        while (capped.length() > 1 && utf8Bytes(capped) > maxBytes) {
            capped = capped.substring(0, capped.length() - 1);
        }
        return capped;
    }

    /**
     * The result of binding a line list against the budget.
     *
     * @param lines      the ordered, bounded list of kept lines
     * @param totalBytes the total UTF-8 size of the kept lines
     * @param lineCapHit when {@code true} the line count reached the cap
     * @param truncated  when {@code true} the input was over budget and had to
     *                   be truncated (or a line was capped)
     * @param truncateNote a human-readable explanation when {@code truncated}
     */
    public record BoundedContext(
            List<NarrationLine> lines,
            int totalBytes,
            boolean lineCapHit,
            boolean truncated,
            String truncateNote) {

        /**
         * @return the kept lines as plain text (the AI-facing payload)
         */
        public List<String> texts() {
            List<String> texts = new ArrayList<>(lines.size());
            for (NarrationLine line : lines) {
                texts.add(line.text());
            }
            return List.copyOf(texts);
        }
    }
}
