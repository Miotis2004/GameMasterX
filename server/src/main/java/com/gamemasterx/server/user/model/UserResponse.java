package com.gamemasterx.server.user.model;

import java.time.Instant;

public class UserResponse {
    private String id;
    private int schemaVersion;
    private int revision;
    private Instant createdAt;
    private Instant updatedAt;
    private String username;
    private String email;

    public UserResponse() {
    }

    public UserResponse(UserDto dto) {
        this.id = dto.getId();
        this.schemaVersion = dto.getSchemaVersion();
        this.revision = dto.getRevision();
        this.createdAt = dto.getCreatedAt();
        this.updatedAt = dto.getUpdatedAt();
        this.username = dto.getUsername();
        this.email = dto.getEmail();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public int getSchemaVersion() { return schemaVersion; }
    public void setSchemaVersion(int schemaVersion) { this.schemaVersion = schemaVersion; }
    public int getRevision() { return revision; }
    public void setRevision(int revision) { this.revision = revision; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
}
