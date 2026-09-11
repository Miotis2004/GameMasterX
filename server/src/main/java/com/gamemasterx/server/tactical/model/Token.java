package com.gamemasterx.server.tactical.model;

import java.util.HashSet;
import java.util.Set;

/**
 * A token placed on a tactical map.
 */
public class Token {
    private String id;
    private String name;
    private String ownerId;
    private int x;
    private int y;
    private boolean hidden;
    private Set<String> visibleTo;

    public Token() {
        this.visibleTo = new HashSet<>();
    }

    public Token(String id, String name, String ownerId, int x, int y) {
        this.id = id;
        this.name = name;
        this.ownerId = ownerId;
        this.x = x;
        this.y = y;
        this.visibleTo = new HashSet<>();
        this.hidden = false;
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
