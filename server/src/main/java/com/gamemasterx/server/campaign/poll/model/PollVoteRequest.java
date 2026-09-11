package com.gamemasterx.server.campaign.poll.model;

/**
 * Request body for voting on a campaign poll.
 */
public class PollVoteRequest {

    private int optionIndex;

    public PollVoteRequest() {
    }

    public int getOptionIndex() {
        return optionIndex;
    }

    public void setOptionIndex(int optionIndex) {
        this.optionIndex = optionIndex;
    }
}
