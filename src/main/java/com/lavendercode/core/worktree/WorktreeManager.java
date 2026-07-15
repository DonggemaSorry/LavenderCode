package com.lavendercode.core.worktree;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.DeserializationContext;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class WorktreeManager {
    private static final Pattern TEMP_NAME = Pattern.compile("^agent-a[0-9a-f]{7}$");
    private static final ObjectMapper JSON = createMapper();

    private final Path repoRoot;
    private final Path worktreeDir;
    private final Path sessionFile;
    private final GitCli git;
    private final ReentrantLock lock = new ReentrantLock();
    private final Map<String, Worktree> active = new LinkedHashMap<>();
    private WorktreeSession currentSession;

    public WorktreeManager(Path repoRoot) throws IOException {
        this(repoRoot, new ProcessGitCli());
    }

    public WorktreeManager(Path repoRoot, GitCli git) throws IOException {
        this.repoRoot = repoRoot.toAbsolutePath().normalize();
        this.git = git;
        this.worktreeDir = this.repoRoot.resolve(".lavendercode/worktrees");
        this.sessionFile = this.repoRoot.resolve(".lavendercode/worktree_session.json");
        String top = git.run(this.repoRoot, List.of("rev-parse", "--show-toplevel"));
        Path topPath = Path.of(top.trim()).toAbsolutePath().normalize();
        if (!topPath.equals(this.repoRoot)) {
            throw new IOException("repoRoot 不是 git 仓库根: " + repoRoot + " (toplevel=" + topPath + ")");
        }
        Files.createDirectories(worktreeDir);
        loadSessionOrClear();
        scanActiveFromDisk();
    }

    public WorktreeSession currentSession() {
        return currentSession;
    }

    public List<Worktree> list() {
        lock.lock();
        try {
            return List.copyOf(active.values());
        } finally {
            lock.unlock();
        }
    }

    public Worktree create(String name, String baseRef, boolean manual) {
        WorktreeSlug.validate(name);
        String flat = WorktreeSlug.flatten(name);
        Path wtPath = worktreeDir.resolve(flat);
        String branch = "worktree-" + flat;

        lock.lock();
        try {
            Worktree existing = active.get(name);
            if (existing != null) {
                // 已由扫盘或先前 create 登记：幂等返回（不调 git worktree add）
                return existing;
            }
        } finally {
            lock.unlock();
        }

        if (Files.isDirectory(wtPath)) {
            Worktree wt = recoverFromFilesystem(name, wtPath, branch, baseRef, manual);
            lock.lock();
            try {
                active.put(name, wt);
                return wt;
            } finally {
                lock.unlock();
            }
        }

        try {
            git.run(repoRoot, List.of(
                "worktree", "add", "-B", branch, wtPath.toString(), baseRef));
        } catch (RuntimeException e) {
            deleteRecursivelyQuiet(wtPath);
            throw e;
        }
        WorktreeSetup.perform(repoRoot, wtPath, git);
        String head = git.run(wtPath, List.of("rev-parse", "HEAD"));
        Worktree wt = new Worktree(name, wtPath.toAbsolutePath().normalize(), branch, baseRef, head,
            Instant.now(), manual);
        lock.lock();
        try {
            active.put(name, wt);
            return wt;
        } finally {
            lock.unlock();
        }
    }

    public WorktreeSession enter(String name) {
        lock.lock();
        try {
            Worktree wt = active.get(name);
            if (wt == null) {
                throw new IllegalArgumentException("Worktree 不存在: " + name);
            }
            Path originalCwd = Path.of("").toAbsolutePath();
            String originalBranch = safeGit(repoRoot, List.of("rev-parse", "--abbrev-ref", "HEAD"), "");
            String originalHead = safeGit(repoRoot, List.of("rev-parse", "HEAD"), "");
            WorktreeSession session = new WorktreeSession(
                originalCwd,
                wt.path(),
                name,
                originalBranch,
                originalHead,
                UUID.randomUUID().toString(),
                false);
            currentSession = session;
            persistSession(session);
            return session;
        } finally {
            lock.unlock();
        }
    }

    public ExitReport exit(String name, ExitAction action, ExitOptions opts) {
        lock.lock();
        try {
            if (currentSession == null || !currentSession.worktreeName().equals(name)) {
                throw new IllegalStateException("只能退出当前 Worktree session: " + name);
            }
            Worktree wt = active.get(name);
            if (wt == null) {
                throw new IllegalArgumentException("Worktree 不存在: " + name);
            }
            if (action == ExitAction.REMOVE && !opts.discardChanges()) {
                if (hasWorktreeChanges(wt.path(), wt.headCommit())) {
                    throw new WorktreeHasChangesException(
                        "Worktree 有未提交修改或新增提交，已拒绝删除以保护变更: " + name);
                }
            }
            currentSession = null;
            persistSession(null);
            if (action == ExitAction.REMOVE) {
                removeLocked(name, wt, true);
                return new ExitReport(true, wt.path().toString(), wt.branch());
            }
            return new ExitReport(false, wt.path().toString(), wt.branch());
        } finally {
            lock.unlock();
        }
    }

    public void remove(String name, ExitOptions opts) {
        lock.lock();
        try {
            Worktree wt = active.get(name);
            if (wt == null) {
                // try disk
                String flat = WorktreeSlug.flatten(name);
                Path wtPath = worktreeDir.resolve(flat);
                if (!Files.isDirectory(wtPath)) {
                    throw new IllegalArgumentException("Worktree 不存在: " + name);
                }
                wt = recoverFromFilesystem(name, wtPath, "worktree-" + flat, "HEAD", false);
                active.put(name, wt);
            }
            if (!opts.discardChanges() && hasWorktreeChanges(wt.path(), wt.headCommit())) {
                throw new WorktreeHasChangesException(
                    "Worktree 有未提交修改或新增提交，已拒绝删除以保护变更: " + name);
            }
            if (currentSession != null && name.equals(currentSession.worktreeName())) {
                currentSession = null;
                persistSession(null);
            }
            removeLocked(name, wt, true);
        } finally {
            lock.unlock();
        }
    }

    public CleanupReport autoCleanup(String name) {
        lock.lock();
        Worktree wt;
        try {
            wt = active.get(name);
            if (wt == null) {
                throw new IllegalArgumentException("Worktree 不存在: " + name);
            }
            if (wt.manual()) {
                return new CleanupReport(true, wt.path(), wt.branch());
            }
        } finally {
            lock.unlock();
        }
        if (!hasWorktreeChanges(wt.path(), wt.headCommit())) {
            remove(name, ExitOptions.discard());
            return new CleanupReport(false, wt.path(), wt.branch());
        }
        return new CleanupReport(true, wt.path(), wt.branch());
    }

    public List<String> sweepStale(Instant cutoff) {
        List<String> removed = new ArrayList<>();
        if (!Files.isDirectory(worktreeDir)) {
            return removed;
        }
        try (Stream<Path> stream = Files.list(worktreeDir)) {
            for (Path dir : stream.filter(Files::isDirectory).toList()) {
                String flat = dir.getFileName().toString();
                if (!TEMP_NAME.matcher(flat).matches()) {
                    continue; // layer 1
                }
                String name = flat; // temp names have no /
                try {
                    Instant mtime = Files.getLastModifiedTime(dir).toInstant();
                    if (mtime.isAfter(cutoff)) {
                        continue; // layer 2 age
                    }
                    if (currentSession != null
                        && currentSession.worktreePath().toAbsolutePath().normalize()
                            .equals(dir.toAbsolutePath().normalize())) {
                        continue;
                    }
                    Worktree wt;
                    lock.lock();
                    try {
                        wt = active.get(name);
                        if (wt == null) {
                            wt = recoverFromFilesystem(name, dir, "worktree-" + flat, "HEAD", false);
                            active.put(name, wt);
                        }
                    } finally {
                        lock.unlock();
                    }
                    if (hasWorktreeChanges(dir, wt.headCommit())) {
                        continue; // layer 3
                    }
                    if (hasUnpushedCommits(dir)) {
                        continue;
                    }
                    remove(name, ExitOptions.discard());
                    removed.add(name);
                } catch (Exception e) {
                    System.err.println("WARN: sweepStale 跳过 " + flat + ": " + e.getMessage());
                }
            }
        } catch (IOException e) {
            System.err.println("WARN: sweepStale 列出目录失败: " + e.getMessage());
        }
        return removed;
    }

    boolean hasWorktreeChanges(Path wtPath, String baseCommit) {
        try {
            String st = git.run(wtPath, List.of("status", "--porcelain"));
            if (!st.isBlank()) {
                return true;
            }
            if (baseCommit == null || baseCommit.isBlank()) {
                return true;
            }
            String count = git.run(wtPath, List.of("rev-list", "--count", baseCommit + "..HEAD"));
            return Integer.parseInt(count.trim()) > 0;
        } catch (Exception e) {
            return true;
        }
    }

    private boolean hasUnpushedCommits(Path wtPath) {
        try {
            String remotes = git.run(wtPath, List.of("remote"));
            if (remotes.isBlank()) {
                return false; // 无远程时不做未推送拦截（本地仓可回收临时 wt）
            }
            String out = git.run(wtPath, List.of("rev-list", "--max-count=1", "HEAD", "--not", "--remotes"));
            return !out.isBlank();
        } catch (Exception e) {
            return true;
        }
    }

    private void removeLocked(String name, Worktree wt, boolean alreadyHoldingLock) {
        Runnable body = () -> {
            try {
                git.run(repoRoot, List.of("worktree", "remove", "--force", wt.path().toString()));
            } catch (Exception e) {
                deleteRecursivelyQuiet(wt.path());
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
            try {
                git.run(repoRoot, List.of("branch", "-D", wt.branch()));
            } catch (Exception ignored) {
                // branch may already be gone
            }
            active.remove(name);
            deleteRecursivelyQuiet(wt.path());
        };
        if (alreadyHoldingLock) {
            body.run();
        } else {
            lock.lock();
            try {
                body.run();
            } finally {
                lock.unlock();
            }
        }
    }

    private Worktree recoverFromFilesystem(String name, Path wtPath, String branch,
                                           String basedOn, boolean manual) {
        String head = readHeadCommitFromFs(wtPath, branch);
        return new Worktree(name, wtPath.toAbsolutePath().normalize(), branch, basedOn,
            head, Instant.now(), manual);
    }

    /** Fast recover: filesystem only, no git subprocess. */
    private String readHeadCommitFromFs(Path wtPath, String branch) {
        try {
            Path gitFile = wtPath.resolve(".git");
            if (!Files.isRegularFile(gitFile)) {
                // maybe bare dir or .git directory in rare cases
                Path head = wtPath.resolve(".git/HEAD");
                if (Files.isRegularFile(head)) {
                    return resolveHeadRef(wtPath.resolve(".git"), Files.readString(head).strip());
                }
                return "";
            }
            String content = Files.readString(gitFile).strip();
            // gitdir: <path>
            if (!content.startsWith("gitdir:")) {
                return "";
            }
            Path gitdir = Path.of(content.substring("gitdir:".length()).trim());
            if (!gitdir.isAbsolute()) {
                gitdir = wtPath.resolve(gitdir).normalize();
            }
            Path headFile = gitdir.resolve("HEAD");
            if (!Files.isRegularFile(headFile)) {
                return "";
            }
            return resolveHeadRef(gitdir, Files.readString(headFile).strip());
        } catch (Exception e) {
            return "";
        }
    }

    private String resolveHeadRef(Path gitdir, String headContent) throws IOException {
        if (headContent.startsWith("ref:")) {
            String ref = headContent.substring(4).trim();
            Path refFile = gitdir.resolve(ref);
            if (Files.isRegularFile(refFile)) {
                return Files.readString(refFile).strip();
            }
            // packed-refs fallback omitted for simplicity
            return "";
        }
        return headContent;
    }

    private void scanActiveFromDisk() {
        if (!Files.isDirectory(worktreeDir)) {
            return;
        }
        try (Stream<Path> stream = Files.list(worktreeDir)) {
            for (Path dir : stream.filter(Files::isDirectory).toList()) {
                String flat = dir.getFileName().toString();
                String name = flat.replace("+", "/");
                try {
                    WorktreeSlug.validate(name);
                } catch (IllegalArgumentException e) {
                    continue;
                }
                if (active.containsKey(name)) {
                    continue;
                }
                String branch = "worktree-" + flat;
                Worktree wt = recoverFromFilesystem(name, dir, branch, "HEAD", false);
                active.put(name, wt);
            }
        } catch (IOException e) {
            System.err.println("WARN: 扫描 worktrees 失败: " + e.getMessage());
        }
    }

    private void loadSessionOrClear() {
        if (!Files.isRegularFile(sessionFile)) {
            currentSession = null;
            return;
        }
        try {
            String raw = Files.readString(sessionFile).strip();
            if (raw.isEmpty() || "null".equals(raw)) {
                currentSession = null;
                return;
            }
            WorktreeSession s = JSON.readValue(raw, WorktreeSession.class);
            if (s.worktreePath() == null || !Files.isDirectory(s.worktreePath())) {
                System.err.println("WARN: session worktree gone, cleared");
                currentSession = null;
                persistSession(null);
                return;
            }
            currentSession = s;
        } catch (Exception e) {
            System.err.println("WARN: Worktree session 损坏，已清空: " + e.getMessage());
            currentSession = null;
            try {
                persistSession(null);
            } catch (Exception ignored) {
            }
        }
    }

    private void persistSession(WorktreeSession session) {
        try {
            Files.createDirectories(sessionFile.getParent());
            Path tmp = Path.of(sessionFile.toString() + ".tmp");
            String body = session == null ? "null" : JSON.writeValueAsString(session);
            Files.writeString(tmp, body);
            try {
                Files.move(tmp, sessionFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, sessionFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            System.err.println("WARN: 写入 worktree session 失败: " + e.getMessage());
        }
    }

    private String safeGit(Path cwd, List<String> args, String fallback) {
        try {
            return git.run(cwd, args);
        } catch (Exception e) {
            return fallback;
        }
    }

    private static void deleteRecursivelyQuiet(Path root) {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.deleteIfExists(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    Files.deleteIfExists(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException ignored) {
        }
    }

    private static ObjectMapper createMapper() {
        ObjectMapper mapper = new ObjectMapper();
        SimpleModule module = new SimpleModule();
        module.addSerializer(Path.class, new JsonSerializer<>() {
            @Override
            public void serialize(Path v, JsonGenerator g, SerializerProvider p) throws IOException {
                g.writeString(v.toAbsolutePath().toString());
            }
        });
        module.addDeserializer(Path.class, new JsonDeserializer<>() {
            @Override
            public Path deserialize(JsonParser p, DeserializationContext c) throws IOException {
                return Path.of(p.getValueAsString());
            }
        });
        mapper.registerModule(module);
        return mapper;
    }
}
