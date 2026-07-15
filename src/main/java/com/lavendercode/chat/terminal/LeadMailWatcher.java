package com.lavendercode.chat.terminal;

import com.lavendercode.core.team.MailMessage;
import com.lavendercode.core.team.Mailbox;
import com.lavendercode.core.team.Team;
import com.lavendercode.core.team.TeamManager;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * 轮询各 Team 的 lead 邮箱，将未读转为 reminder 文本回调。
 * IDLE 自动续推由上层（若具备）订阅同一 callback。
 */
public final class LeadMailWatcher implements AutoCloseable {
    private final TeamManager teamManager;
    private final Consumer<String> reminderSink;
    private final ScheduledExecutorService scheduler;
    private final AtomicBoolean started = new AtomicBoolean(false);

    public LeadMailWatcher(TeamManager teamManager, Consumer<String> reminderSink) {
        this.teamManager = teamManager;
        this.reminderSink = reminderSink;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "lead-mail-watcher");
            t.setDaemon(true);
            return t;
        });
    }

    public void start() {
        if (!started.compareAndSet(false, true)) {
            return;
        }
        scheduler.scheduleAtFixedRate(this::pollOnce, 1, 1, TimeUnit.SECONDS);
    }

    void pollOnce() {
        try {
            for (Team team : teamManager.list()) {
                Mailbox mailbox = new Mailbox(team.configDir());
                List<MailMessage> unread = mailbox.readUnread("lead");
                if (unread.isEmpty()) {
                    continue;
                }
                StringBuilder sb = new StringBuilder("<team-update>\n");
                for (MailMessage m : unread) {
                    String content = m.content() == null ? "" : m.content();
                    if (content.length() > 8000) {
                        content = content.substring(0, 8000);
                    }
                    sb.append("from=").append(m.from())
                        .append(" summary=").append(m.summary())
                        .append('\n').append(content).append("\n---\n");
                }
                sb.append("</team-update>");
                mailbox.markAllUnreadAsRead("lead");
                if (reminderSink != null) {
                    reminderSink.accept(sb.toString());
                }
            }
        } catch (Exception e) {
            System.err.println("LeadMailWatcher: " + e.getMessage());
        }
    }

    @Override
    public void close() {
        scheduler.shutdownNow();
    }
}
