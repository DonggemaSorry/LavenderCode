package com.lavendercode.core.context;

import com.lavendercode.core.provider.Message;
import com.lavendercode.core.provider.Role;
import com.lavendercode.core.tool.ToolDefinition;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public final class RecoverySegmentBuilder {
    public static final String BOUNDARY_TEXT = """
        IMPORTANT: Details of files, errors, and user messages may have been omitted from the summary.
        Use the read_file tool to retrieve original content when you need exact quotes, code, or error text.
        Do not guess or invent content based on the summary alone.
        """;

    private final FileReadTracker fileReadTracker;

    public RecoverySegmentBuilder(FileReadTracker fileReadTracker) {
        this.fileReadTracker = fileReadTracker;
    }

    public List<Message> build(List<ToolDefinition> toolDefs) {
        List<Message> segments = new ArrayList<>();
        segments.add(new Message(Role.USER, buildFileSnapshots()));
        segments.add(new Message(Role.USER, buildToolList(toolDefs)));
        segments.add(new Message(Role.USER, BOUNDARY_TEXT));
        return segments;
    }

    private String buildFileSnapshots() {
        StringBuilder sb = new StringBuilder("## Recently read files\n");
        int maxChars = (int) (ContextConstants.FILE_SNAPSHOT_MAX_TOKENS * ContextConstants.ESTIMATE_CHARS_PER_TOKEN);
        for (FileSnapshot snap : fileReadTracker.latest(ContextConstants.MAX_FILE_SNAPSHOTS)) {
            sb.append("### File: ").append(snap.path())
                .append(" (read at ").append(snap.readAt()).append(")\n");
            String content = snap.content();
            if (content.length() > maxChars) {
                content = content.substring(0, maxChars) + "\n(content truncated)";
            }
            sb.append(content).append("\n\n");
        }
        return sb.toString();
    }

    private String buildToolList(List<ToolDefinition> toolDefs) {
        String names = toolDefs.stream().map(ToolDefinition::name).collect(Collectors.joining(", "));
        return "## Available tools for this session\n" + names;
    }
}
