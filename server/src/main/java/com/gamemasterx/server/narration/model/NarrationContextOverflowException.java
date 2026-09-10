package com.gamemasterx.server.narration.model;

/**
 * Thrown when an assembled context exceeds the configured size bound and
 * truncation is disabled.
 *
 * <p>This only occurs when {@code game.master.x.narration.allow-truncation=false}
 * and the ordered context lines cannot fit within the byte/line budget. The
 * caller is expected to raise the budget or enable truncation rather than treat
 * this as a runtime bug.</p>
 */
public class NarrationContextOverflowException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final int keptLines;
    private final int totalLines;
    private final int maxBytes;
    private final int maxLines;

    public NarrationContextOverflowException(int keptLines, int totalLines, int maxBytes, int maxLines) {
        super("Assembled context of " + totalLines + " line(s) exceeds the configured bound "
                + "(kept " + keptLines + " lines, max " + maxLines + " lines / "
                + maxBytes + " bytes); enable truncation or raise the bound");
        this.keptLines = keptLines;
        this.totalLines = totalLines;
        this.maxBytes = maxBytes;
        this.maxLines = maxLines;
    }

    /**
     * @return the number of lines retained before the overflow was detected
     */
    public int getKeptLines() {
        return keptLines;
    }

    /**
     * @return the total number of candidate lines that exceeded the bound
     */
    public int getTotalLines() {
        return totalLines;
    }

    /**
     * @return the configured byte cap
     */
    public int getMaxBytes() {
        return maxBytes;
    }

    /**
     * @return the configured line cap
     */
    public int getMaxLines() {
        return maxLines;
    }
}
