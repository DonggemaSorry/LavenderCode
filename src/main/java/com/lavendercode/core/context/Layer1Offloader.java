package com.lavendercode.core.context;

import com.lavendercode.chat.session.SessionManager;
import com.lavendercode.core.provider.Message;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.logging.Logger;
import java.util.function.Supplier;

public final class Layer1Offloader {
    private static final Logger logger = Logger.getLogger(Layer1Offloader.class.getName());

    private final SessionManager sessionManager;
    private final Supplier<SessionPaths> pathsSupplier;
    private final ReplacementLedger ledger;

    public Layer1Offloader(SessionManager sessionManager, Supplier<SessionPaths> pathsSupplier, ReplacementLedger ledger) {
        this.sessionManager = sessionManager;
        this.pathsSupplier = pathsSupplier;
        this.ledger = ledger;
    }

    public int offloadAndSnip() {
        List<Message> history = sessionManager.getHistory();
        List<ToolRound> rounds = ToolRoundGrouper.group(history);
        int totalReplaced = 0;
        for (ToolRound round : rounds) {
            totalReplaced += offloadRound(round);
        }
        return totalReplaced;
    }

    private int offloadRound(ToolRound round) {
        List<ToolResultEntry> unseen = round.entries().stream()
            .filter(e -> !ledger.isSeen(e.toolCallId()))
            .sorted(Comparator.comparingInt(ToolResultEntry::utf8Bytes).reversed())
            .toList();

        Set<String> replacedThisRound = new HashSet<>();
        int count = 0;

        for (ToolResultEntry e : unseen) {
            if (e.utf8Bytes() > ContextConstants.SINGLE_TOOL_RESULT_BYTES) {
                if (tryOffload(e)) {
                    replacedThisRound.add(e.toolCallId());
                    count++;
                }
            }
        }

        int aggregate = round.entries().stream()
            .filter(e -> !replacedThisRound.contains(e.toolCallId()))
            .filter(e -> !ledger.isReplaced(e.toolCallId()))
            .mapToInt(ToolResultEntry::utf8Bytes)
            .sum();

        for (ToolResultEntry e : unseen) {
            if (aggregate <= ContextConstants.ROUND_AGGREGATE_BYTES) break;
            if (ledger.isSeen(e.toolCallId()) || replacedThisRound.contains(e.toolCallId())) continue;
            if (tryOffload(e)) {
                replacedThisRound.add(e.toolCallId());
                aggregate -= e.utf8Bytes();
                count++;
            }
        }

        for (ToolResultEntry e : round.entries()) {
            if (ledger.isReplaced(e.toolCallId()) && !replacedThisRound.contains(e.toolCallId())) {
                sessionManager.updateToolContent(e.toolCallId(), ledger.getReplacement(e.toolCallId()));
            }
        }
        for (ToolResultEntry e : unseen) {
            if (!replacedThisRound.contains(e.toolCallId()) && !ledger.isSeen(e.toolCallId())) {
                ledger.recordKeepOriginal(e.toolCallId());
            }
        }
        return count;
    }

    private boolean tryOffload(ToolResultEntry e) {
        if (ledger.isSeen(e.toolCallId())) return false;
        SessionPaths sessionPaths = pathsSupplier.get();
        try {
            if (sessionPaths.fileExists(e.toolCallId())) {
                String existing = ledger.getReplacement(e.toolCallId());
                if (existing == null) {
                    Path p = sessionPaths.toolResultPath(e.toolCallId());
                    String content = Files.readString(p);
                    String preview = PreviewBuilder.build(content, e.utf8Bytes(), p);
                    ledger.recordReplacement(e.toolCallId(), preview);
                    sessionManager.updateToolContent(e.toolCallId(), preview);
                }
                return ledger.isReplaced(e.toolCallId());
            }
            sessionPaths.ensureDirectories();
            sessionPaths.writeToolResult(e.toolCallId(), e.content());
            Path path = sessionPaths.toolResultPath(e.toolCallId());
            String preview = PreviewBuilder.build(e.content(), e.utf8Bytes(), path);
            sessionManager.updateToolContent(e.toolCallId(), preview);
            ledger.recordReplacement(e.toolCallId(), preview);
            return true;
        } catch (IOException ex) {
            logger.warning("Offload failed for " + e.toolCallId() + ": " + ex.getMessage());
            return false;
        }
    }
}
