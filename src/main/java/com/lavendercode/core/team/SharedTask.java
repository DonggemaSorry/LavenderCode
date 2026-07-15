package com.lavendercode.core.team;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SharedTask(
    String id,
    String title,
    String description,
    String status,
    String assignee,
    List<String> blockedBy,
    List<String> blocks,
    long createdAt,
    long updatedAt,
    @JsonIgnore Boolean ready
) {
    public SharedTask {
        if (blockedBy == null) {
            blockedBy = List.of();
        }
        if (blocks == null) {
            blocks = List.of();
        }
        if (description == null) {
            description = "";
        }
    }

    public SharedTask(
            String id,
            String title,
            String description,
            String status,
            String assignee,
            List<String> blockedBy,
            List<String> blocks,
            long createdAt,
            long updatedAt) {
        this(id, title, description, status, assignee, blockedBy, blocks, createdAt, updatedAt, null);
    }

    public SharedTask withReady(boolean readyFlag) {
        return new SharedTask(
            id, title, description, status, assignee, blockedBy, blocks, createdAt, updatedAt, readyFlag);
    }

    @JsonIgnore
    public boolean isReady() {
        return Boolean.TRUE.equals(ready);
    }
}
