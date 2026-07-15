package com.lavendercode.core.team;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;

@JsonIgnoreProperties(ignoreUnknown = true)
public final class Team {
    private static final ObjectMapper JSON = createMapper();

    private final ReentrantLock lock = new ReentrantLock();

    private String name;
    private String sanitizedName;
    private String leadAgentId;
    private BackendType backend;
    private String description;
    private long createdAtEpochMs;
    private Path configDir;
    private Path configPath;
    private List<TeammateInfo> members;

    public Team() {
        this.members = new ArrayList<>();
        this.description = "";
    }

    public Team(
            String name,
            String sanitizedName,
            String leadAgentId,
            BackendType backend,
            String description,
            Instant createdAt,
            Path configDir,
            Path configPath,
            List<TeammateInfo> members) {
        this.name = name;
        this.sanitizedName = sanitizedName;
        this.leadAgentId = leadAgentId;
        this.backend = backend;
        this.description = description == null ? "" : description;
        this.createdAtEpochMs = createdAt.toEpochMilli();
        this.configDir = configDir;
        this.configPath = configPath;
        this.members = new ArrayList<>(members);
    }

    public String name() { return name; }
    public String sanitizedName() { return sanitizedName; }
    public String leadAgentId() { return leadAgentId; }
    public BackendType backend() { return backend; }
    public String description() { return description; }
    public Instant createdAt() { return Instant.ofEpochMilli(createdAtEpochMs); }
    public Path configDir() { return configDir; }
    public Path configPath() { return configPath; }

    @JsonIgnore
    public List<TeammateInfo> membersView() {
        return List.copyOf(members);
    }

    public List<TeammateInfo> getMembers() {
        return members;
    }

    public void setMembers(List<TeammateInfo> members) {
        this.members = members == null ? new ArrayList<>() : new ArrayList<>(members);
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getSanitizedName() { return sanitizedName; }
    public void setSanitizedName(String sanitizedName) { this.sanitizedName = sanitizedName; }
    public String getLeadAgentId() { return leadAgentId; }
    public void setLeadAgentId(String leadAgentId) { this.leadAgentId = leadAgentId; }
    public BackendType getBackend() { return backend; }
    public void setBackend(BackendType backend) { this.backend = backend; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public long getCreatedAtEpochMs() { return createdAtEpochMs; }
    public void setCreatedAtEpochMs(long createdAtEpochMs) { this.createdAtEpochMs = createdAtEpochMs; }
    public Path getConfigDir() { return configDir; }
    public void setConfigDir(Path configDir) { this.configDir = configDir; }
    public Path getConfigPath() { return configPath; }
    public void setConfigPath(Path configPath) { this.configPath = configPath; }

    public Optional<TeammateInfo> findMember(String memberName) {
        return members.stream().filter(m -> m.name().equals(memberName)).findFirst();
    }

    public boolean hasActiveMembers() {
        return members.stream()
            .filter(m -> !"lead".equals(m.name()))
            .anyMatch(TeammateInfo::isEffectivelyActive);
    }

    public void addMember(TeammateInfo info) {
        lock.lock();
        try {
            reloadFromDiskLocked();
            if (members.stream().anyMatch(m -> m.name().equals(info.name()))) {
                throw new IllegalArgumentException("队员名已存在: " + info.name());
            }
            members.add(info);
            saveAtomicLocked();
        } catch (IOException e) {
            throw new IllegalStateException("保存团队配置失败: " + e.getMessage(), e);
        } finally {
            lock.unlock();
        }
    }

    public void setMemberActive(String memberName, boolean active) {
        lock.lock();
        try {
            reloadFromDiskLocked();
            boolean found = false;
            for (int i = 0; i < members.size(); i++) {
                TeammateInfo m = members.get(i);
                if (m.name().equals(memberName)) {
                    members.set(i, new TeammateInfo(
                        m.name(), m.agentId(), m.agentType(), m.model(),
                        m.worktreePath(), m.branch(), m.backendType(), m.paneId(),
                        active, m.planModeRequired(), m.sessionDir()));
                    found = true;
                    break;
                }
            }
            if (!found) {
                return;
            }
            saveAtomicLocked();
        } catch (IOException e) {
            throw new IllegalStateException("保存团队配置失败: " + e.getMessage(), e);
        } finally {
            lock.unlock();
        }
    }

    public void removeMember(String memberName) {
        lock.lock();
        try {
            reloadFromDiskLocked();
            members.removeIf(m -> m.name().equals(memberName));
            saveAtomicLocked();
        } catch (IOException e) {
            throw new IllegalStateException("保存团队配置失败: " + e.getMessage(), e);
        } finally {
            lock.unlock();
        }
    }

    public void saveAtomic() {
        lock.lock();
        try {
            saveAtomicLocked();
        } catch (IOException e) {
            throw new IllegalStateException("保存团队配置失败: " + e.getMessage(), e);
        } finally {
            lock.unlock();
        }
    }

    public void reloadFromDiskLocked() throws IOException {
        if (configPath == null || !Files.exists(configPath)) {
            return;
        }
        Team loaded = load(configPath);
        this.members = new ArrayList<>(loaded.members);
        this.backend = loaded.backend;
        this.description = loaded.description;
        this.name = loaded.name;
        this.sanitizedName = loaded.sanitizedName;
        this.leadAgentId = loaded.leadAgentId;
        this.createdAtEpochMs = loaded.createdAtEpochMs;
    }

    private void saveAtomicLocked() throws IOException {
        Files.createDirectories(configDir);
        Path tmp = configPath.resolveSibling(configPath.getFileName() + ".tmp");
        JSON.writerWithDefaultPrettyPrinter().writeValue(tmp.toFile(), this);
        try {
            Files.move(tmp, configPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, configPath, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static Team load(Path configPath) throws IOException {
        Team t = JSON.readValue(configPath.toFile(), Team.class);
        if (t.configPath == null) {
            t.configPath = configPath;
        }
        if (t.configDir == null) {
            t.configDir = configPath.getParent();
        }
        if (t.members == null) {
            t.members = new ArrayList<>();
        }
        return t;
    }

    private static ObjectMapper createMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
        SimpleModule module = new SimpleModule();
        module.addSerializer(Path.class, new JsonSerializer<>() {
            @Override
            public void serialize(Path v, JsonGenerator g, SerializerProvider p) throws IOException {
                if (v == null) {
                    g.writeNull();
                } else {
                    g.writeString(v.toAbsolutePath().normalize().toString());
                }
            }
        });
        module.addDeserializer(Path.class, new JsonDeserializer<>() {
            @Override
            public Path deserialize(JsonParser p, DeserializationContext c) throws IOException {
                String s = p.getValueAsString();
                return s == null || s.isBlank() ? null : Path.of(s);
            }
        });
        mapper.registerModule(module);
        return mapper;
    }
}
