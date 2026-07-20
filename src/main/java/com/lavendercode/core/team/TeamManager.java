package com.lavendercode.core.team;

import com.lavendercode.core.task.AgentNameRegistry;
import com.lavendercode.core.task.TaskManager;
import com.lavendercode.core.worktree.WorktreeManager;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;

public final class TeamManager {
    private final ReentrantLock lock = new ReentrantLock();
    private final Map<String, Team> teams = new HashMap<>();
    private final Path homeDir;
    private final Path teamsRoot;
    private final WorktreeManager worktreeManager;
    private final TaskManager taskManager;
    private final AgentNameRegistry registry;

    public TeamManager(
            Path homeDir,
            WorktreeManager worktreeManager,
            TaskManager taskManager,
            AgentNameRegistry registry) {
        this.homeDir = homeDir;
        this.worktreeManager = worktreeManager;
        this.taskManager = taskManager;
        this.registry = registry == null ? new AgentNameRegistry() : registry;
        this.teamsRoot = homeDir.resolve(".lavendercode").resolve("teams");
        try {
            Files.createDirectories(teamsRoot);
        } catch (IOException e) {
            throw new IllegalStateException("无法创建团队目录: " + teamsRoot, e);
        }
        scanExisting();
    }

    public Path teamsRoot() {
        return teamsRoot;
    }

    public AgentNameRegistry registry() {
        return registry;
    }

    public WorktreeManager worktreeManager() {
        return worktreeManager;
    }

    public TaskManager taskManager() {
        return taskManager;
    }

    private void scanExisting() {
        if (!Files.isDirectory(teamsRoot)) {
            return;
        }
        try (Stream<Path> dirs = Files.list(teamsRoot)) {
            dirs.filter(Files::isDirectory).forEach(dir -> {
                Path cfg = dir.resolve("config.json");
                if (!Files.exists(cfg)) {
                    return;
                }
                try {
                    Team t = Team.load(cfg);
                    teams.put(t.sanitizedName(), t);
                } catch (Exception e) {
                    System.err.println("跳过损坏的团队配置: " + cfg + " — " + e.getMessage());
                }
            });
        } catch (IOException e) {
            System.err.println("扫描团队目录失败: " + e.getMessage());
        }
    }

    public Team create(String rawName, String agentTypeIgnored) {
        lock.lock();
        try {
            String sanitized = TeamName.sanitize(rawName);
            sanitized = TeamName.ensureUnique(sanitized, Set.copyOf(teams.keySet()));
            BackendType backend = BackendFactory.detect();
            if (backend != BackendType.IN_PROCESS) {
                // 刀1：未强制 in-process 且检出窗格后端 → 明确失败
                BackendFactory.requireImplemented(backend);
            }
            Path configDir = teamsRoot.resolve(sanitized);
            Files.createDirectories(configDir);
            Path configPath = configDir.resolve("config.json");
            List<TeammateInfo> members = new ArrayList<>();
            members.add(TeammateInfo.leadPlaceholder());
            Team team = new Team(
                rawName.trim(), sanitized, "lead", backend, "",
                Instant.now(), configDir, configPath, members);
            team.saveAtomic();
            teams.put(sanitized, team);
            return team;
        } catch (IOException e) {
            throw new IllegalStateException("创建团队失败: " + e.getMessage(), e);
        } finally {
            lock.unlock();
        }
    }

    public Optional<Team> get(String nameOrSanitized) {
        String key;
        try {
            key = TeamName.sanitize(nameOrSanitized);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
        lock.lock();
        try {
            Team t = teams.get(key);
            if (t != null) {
                return Optional.of(t);
            }
            // 也试原始 sanitized 已存在 map key
            return Optional.ofNullable(teams.get(nameOrSanitized));
        } finally {
            lock.unlock();
        }
    }

    public List<Team> list() {
        lock.lock();
        try {
            return teams.values().stream()
                .sorted(Comparator.comparing(Team::sanitizedName))
                .toList();
        } finally {
            lock.unlock();
        }
    }

    public void delete(String nameOrSanitized, boolean force) {
        lock.lock();
        try {
            Team team = get(nameOrSanitized).orElseThrow(() -> new TeamNotFoundException(nameOrSanitized));
            if (!force && team.hasActiveMembers()) {
                throw new TeamHasActiveMembersException(team.sanitizedName());
            }
            for (TeammateInfo m : team.membersView()) {
                if ("lead".equals(m.name())) {
                    continue;
                }
                if (taskManager != null && m.agentId() != null && !m.agentId().isBlank()) {
                    try {
                        taskManager.stop(m.agentId());
                    } catch (Exception e) {
                        System.err.println("停止队员任务警告: " + e.getMessage());
                    }
                }
                cleanupMemberResources(m);
            }
            try {
                deleteRecursive(team.configDir());
            } catch (IOException e) {
                System.err.println("删除团队目录警告: " + e.getMessage());
            }
            teams.remove(team.sanitizedName());
        } finally {
            lock.unlock();
        }
    }

    private void cleanupMemberResources(TeammateInfo m) {
        if (worktreeManager != null && m.worktreePath() != null) {
            try {
                String slug = "team-" + inferTeamSlug(m) + "/" + m.name();
                // best-effort: try remove by reconstructed name; ignore failures
                worktreeManager.remove(slug, new com.lavendercode.core.worktree.ExitOptions(true));
            } catch (Exception e) {
                System.err.println("清理 worktree 警告: " + e.getMessage());
            }
        }
        if (m.sessionDir() != null) {
            try {
                deleteRecursive(m.sessionDir());
            } catch (Exception e) {
                System.err.println("清理 session 警告: " + e.getMessage());
            }
        }
        if (registry != null) {
            try {
                registry.unregister(m.name());
            } catch (Exception ignored) {
                // ignore
            }
        }
    }

    private static String inferTeamSlug(TeammateInfo m) {
        // worktree slug form team-<s>/<member> → branch worktree-team-<s>+<member>
        String branch = m.branch() == null ? "" : m.branch();
        if (branch.startsWith("worktree-team-") && branch.contains("+")) {
            String mid = branch.substring("worktree-team-".length());
            int plus = mid.lastIndexOf('+');
            if (plus > 0) {
                return mid.substring(0, plus);
            }
        }
        return "unknown";
    }

    private static void deleteRecursive(Path root) throws IOException {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(root)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException e) {
                    System.err.println("删除失败: " + p + " — " + e.getMessage());
                }
            });
        }
    }
}
