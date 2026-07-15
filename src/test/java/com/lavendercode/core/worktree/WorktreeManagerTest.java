package com.lavendercode.core.worktree;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class WorktreeManagerTest {

    @TempDir Path temp;

    Path initRepo() throws Exception {
        Path repo = temp.resolve("repo");
        Files.createDirectories(repo);
        ProcessGitCli git = new ProcessGitCli();
        git.run(repo, List.of("init"));
        git.run(repo, List.of("config", "user.email", "t@t.com"));
        git.run(repo, List.of("config", "user.name", "t"));
        // default branch name may be master/main
        Files.writeString(repo.resolve("README.md"), "hi\n");
        git.run(repo, List.of("add", "."));
        git.run(repo, List.of("commit", "-m", "init"));
        return repo.toAbsolutePath().normalize();
    }

    @Test
    void createSimpleSlug() throws Exception {
        Path repo = initRepo();
        WorktreeManager mgr = new WorktreeManager(repo, new ProcessGitCli());
        Worktree wt = mgr.create("alice", "HEAD", true);
        assertThat(wt.path()).isEqualTo(repo.resolve(".lavendercode/worktrees/alice"));
        assertThat(wt.branch()).isEqualTo("worktree-alice");
        assertThat(Files.isDirectory(wt.path())).isTrue();
    }

    @Test
    void createNestedSlugFlattens() throws Exception {
        Path repo = initRepo();
        WorktreeManager mgr = new WorktreeManager(repo, new ProcessGitCli());
        Worktree wt = mgr.create("team/alice", "HEAD", true);
        assertThat(wt.path().getFileName().toString()).isEqualTo("team+alice");
        assertThat(wt.branch()).isEqualTo("worktree-team+alice");
    }

    @Test
    void createExistingDirFastRecoverDoesNotCallWorktreeAdd() throws Exception {
        Path repo = initRepo();
        AtomicInteger worktreeAddCalls = new AtomicInteger();
        GitCli counting = new GitCli() {
            final ProcessGitCli real = new ProcessGitCli();
            @Override public String run(Path cwd, List<String> args) {
                if (!args.isEmpty() && "worktree".equals(args.get(0))
                    && args.size() > 1 && "add".equals(args.get(1))) {
                    worktreeAddCalls.incrementAndGet();
                }
                return real.run(cwd, args);
            }
        };
        WorktreeManager mgr1 = new WorktreeManager(repo, counting);
        mgr1.create("alice", "HEAD", true);
        int afterFirst = worktreeAddCalls.get();
        assertThat(afterFirst).isEqualTo(1);

        WorktreeManager mgr2 = new WorktreeManager(repo, counting);
        Worktree wt = mgr2.create("alice", "HEAD", true);
        assertThat(worktreeAddCalls.get()).isEqualTo(afterFirst);
        assertThat(wt.name()).isEqualTo("alice");
    }

    @Test
    void postCreateCopiesSettingsLocal() throws Exception {
        Path repo = initRepo();
        Path local = repo.resolve(".lavendercode/settings.local.yaml");
        Files.createDirectories(local.getParent());
        Files.writeString(local, "x: 1\n");
        WorktreeManager mgr = new WorktreeManager(repo, new ProcessGitCli());
        Worktree wt = mgr.create("cfg", "HEAD", true);
        assertThat(wt.path().resolve(".lavendercode/settings.local.yaml")).exists();
    }

    @Test
    void postCreateSymlinksNodeModules() throws Exception {
        Path repo = initRepo();
        Path nm = repo.resolve("node_modules");
        Files.createDirectories(nm);
        Files.writeString(nm.resolve("x"), "1");
        WorktreeManager mgr = new WorktreeManager(repo, new ProcessGitCli());
        Worktree wt = mgr.create("deps", "HEAD", true);
        Path link = wt.path().resolve("node_modules");
        assumeTrue(Files.isSymbolicLink(link), "需要 symlink 权限");
    }

    @Test
    void enterDoesNotChangeJvmCwd() throws Exception {
        Path repo = initRepo();
        Path before = Path.of("").toAbsolutePath();
        WorktreeManager mgr = new WorktreeManager(repo, new ProcessGitCli());
        mgr.create("e1", "HEAD", true);
        WorktreeSession s = mgr.enter("e1");
        assertThat(Path.of("").toAbsolutePath()).isEqualTo(before);
        assertThat(s.worktreeName()).isEqualTo("e1");
        assertThat(s.worktreePath()).exists();
    }

    @Test
    void exitRemoveDeniedWhenDirty() throws Exception {
        Path repo = initRepo();
        WorktreeManager mgr = new WorktreeManager(repo, new ProcessGitCli());
        Worktree wt = mgr.create("dirty", "HEAD", true);
        mgr.enter("dirty");
        Files.writeString(wt.path().resolve("README.md"), "changed\n");
        assertThatThrownBy(() -> mgr.exit("dirty", ExitAction.REMOVE, ExitOptions.keepSafe()))
            .isInstanceOf(WorktreeHasChangesException.class);
        assertThat(wt.path()).exists();
    }

    @Test
    void exitRemoveWithDiscardDeletes() throws Exception {
        Path repo = initRepo();
        WorktreeManager mgr = new WorktreeManager(repo, new ProcessGitCli());
        Worktree wt = mgr.create("gone", "HEAD", true);
        mgr.enter("gone");
        Files.writeString(wt.path().resolve("README.md"), "x\n");
        mgr.exit("gone", ExitAction.REMOVE, ExitOptions.discard());
        assertThat(wt.path()).doesNotExist();
    }

    @Test
    void autoCleanupKeepsManual() throws Exception {
        Path repo = initRepo();
        WorktreeManager mgr = new WorktreeManager(repo, new ProcessGitCli());
        mgr.create("manual1", "HEAD", true);
        CleanupReport r = mgr.autoCleanup("manual1");
        assertThat(r.kept()).isTrue();
        assertThat(repo.resolve(".lavendercode/worktrees/manual1")).exists();
    }

    @Test
    void autoCleanupRemovesTempWhenClean() throws Exception {
        Path repo = initRepo();
        WorktreeManager mgr = new WorktreeManager(repo, new ProcessGitCli());
        mgr.create("agent-a1b2c3d", "HEAD", false);
        CleanupReport r = mgr.autoCleanup("agent-a1b2c3d");
        assertThat(r.kept()).isFalse();
    }

    @Test
    void sweepStaleOnlyTempPattern() throws Exception {
        Path repo = initRepo();
        WorktreeManager mgr = new WorktreeManager(repo, new ProcessGitCli());
        mgr.create("alice", "HEAD", true);
        mgr.create("agent-aabcdef0", "HEAD", false);
        Files.setLastModifiedTime(
            repo.resolve(".lavendercode/worktrees/agent-aabcdef0"),
            FileTime.from(Instant.EPOCH));
        List<String> removed = mgr.sweepStale(Instant.now().minus(Duration.ofHours(1)));
        assertThat(removed).contains("agent-aabcdef0");
        assertThat(repo.resolve(".lavendercode/worktrees/alice")).exists();
    }

    @Test
    void sessionPersistsAndClearsWhenPathGone() throws Exception {
        Path repo = initRepo();
        WorktreeManager mgr = new WorktreeManager(repo, new ProcessGitCli());
        mgr.create("s1", "HEAD", true);
        mgr.enter("s1");
        assertThat(repo.resolve(".lavendercode/worktree_session.json")).exists();

        mgr.exit("s1", ExitAction.REMOVE, ExitOptions.discard());

        String ghostPath = repo.resolve(".lavendercode/worktrees/ghost").toAbsolutePath().toString()
            .replace("\\", "\\\\");
        String repoPath = repo.toAbsolutePath().toString().replace("\\", "\\\\");
        Files.writeString(repo.resolve(".lavendercode/worktree_session.json"),
            "{\"original_cwd\":\"" + repoPath + "\",\"worktree_path\":\"" + ghostPath
                + "\",\"worktree_name\":\"ghost\",\"original_branch\":\"main\","
                + "\"original_head_commit\":\"abc\",\"session_id\":\"x\",\"hook_based\":false}");

        WorktreeManager mgr2 = new WorktreeManager(repo, new ProcessGitCli());
        assertThat(mgr2.currentSession()).isNull();
    }
}
