package com.gamemasterx.server.user.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;
import java.util.Objects;

@Document(collection = "users")
public class UserDto {

    @Id
    private String id;
    private int schemaVersion;
    private int revision;
    private Instant createdAt;
    private Instant updatedAt;
    private String username;
    private String email;
    private String passwordHash;

    public UserDto() {
    }

    public UserDto(String id, int schemaVersion, int revision, Instant createdAt, Instant updatedAt, String username, String email, String passwordHash) {
        this.id = id;
        this.schemaVersion = schemaVersion;
        this.revision = revision;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.username = username;
        this.email = email;
        this.passwordHash = passwordHash;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public int getSchemaVersion() {
        return schemaVersion;
    }

    public void setSchemaVersion(int schemaVersion) {
        this.schemaVersion = schemaVersion;
    }

    public int getRevision() {
        return revision;
    }

    public void setRevision(int revision) {
        this.revision = revision;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UserDto userDto = (UserDto) o;
        return Objects.equals(id, userDto.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
