package com.gamemasterx.server.campaign.poll.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Request body for creating a campaign poll.
 */
public class PollCreateRequest {

    @NotBlank
    private String question;

    @Size(min = 2, max = 10)
    private List<@NotBlank String> options;

    public PollCreateRequest() {
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public List<String> getOptions() {
        return options;
    }

    public void setOptions(List<String> options) {
        this.options = options;
    }
}
