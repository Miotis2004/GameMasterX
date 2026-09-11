package com.gamemasterx.server.tactical.model;

import java.util.Set;

/**
 * API representation of a token.
 */
public class TokenDto {
    private String id;
    private String name;
    private String ownerId;
    private int x;
    private int y;
    private boolean hidden;
    private Set<String> visibleTo;

    public TokenDto() {
    }

    public static TokenDto from(Token t) {
        TokenDto dto = new TokenDto();
        dto.id = t.getId();
        dto.name = t.getName();
        dto.ownerId = t.getOwnerId();
        dto.x = t.getX();
        dto.y = t.getY();
        dto.hidden = t.isHidden();
        dto.visibleTo = t.getVisibleTo();
        return dto;
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

    public String getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(String ownerId) {
        this.ownerId = ownerId;
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

    public boolean isHidden() {
        return hidden;
    }

    public void setHidden(boolean hidden) {
        this.hidden = hidden;
    }

    public Set<String> getVisibleTo() {
        return visibleTo;
    }

    public void setVisibleTo(Set<String> visibleTo) {
        this.visibleTo = visibleTo;
    }
}
