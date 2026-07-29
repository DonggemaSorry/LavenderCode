package com.lavendercode.chat.terminal;

import com.lavendercode.core.provider.Role;
import org.jline.terminal.Terminal;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStyle;
import org.jline.utils.InfoCmp;

import java.util.List;

/**
 * Incremental content printer for native-scroll mode.
 * Tracks printed state and only outputs new/changed content,
 * avoiding full-viewport redraws on every streaming delta.
 */
final class StreamPrinter {

    private static final AttributedStyle ROLE_ASSISTANT =
        AttributedStyle.DEFAULT.foreground(AttributedStyle.CYAN);
    private static final AttributedStyle ROLE_SYSTEM =
        AttributedStyle.DEFAULT.foreground(AttributedStyle.YELLOW);
    private static final AttributedStyle TOOL_STYLE =
        AttributedStyle.DEFAULT.foreground(136, 136, 136);
    private static final AttributedStyle TOOL_OK_STYLE =
        AttributedStyle.DEFAULT.foreground(100, 200, 100);
    private static final AttributedStyle TOOL_ERR_STYLE =
        AttributedStyle.DEFAULT.foreground(220, 80, 80);

    private final Terminal terminal;

    /** Number of block lines printed for the current AI message. */
    private int printedLineCount;

    /** Whether a partial (incomplete) line was printed and needs overwrite on next update. */
    private boolean hasPartialLine;

    StreamPrinter(Terminal terminal) {
        this.terminal = terminal;
    }

    // ===== Streaming content =====

    /**
     * Incrementally prints new content lines from the AI message block.
     * Complete lines (index 0..totalLines-2) are printed once with role prefix.
     * The partial last line (index totalLines-1) is overwritten on each call.
     */
    void appendIncremental(MessageBlock block) {
        List<RenderedLine> lines = block.allLines();
        int totalLines = lines.size();
        if (totalLines == 0) return;

        int completeCount = totalLines - 1;

        // 表格行闭合后会进入缓冲，block 行数可能暂时回缩；
        // 此时只需清掉屏幕上的部分行预览，避免重复打印已打印行。
        if (completeCount < printedLineCount) {
            if (hasPartialLine) {
                terminal.puts(InfoCmp.Capability.carriage_return);
                terminal.puts(InfoCmp.Capability.clr_eol);
                hasPartialLine = false;
                terminal.flush();
            }
            return;
        }

        // Print new complete lines (those beyond what we already printed)
        for (int i = printedLineCount; i < completeCount; i++) {
            if (hasPartialLine) {
                // Overwrite the partial line with this complete line
                terminal.puts(InfoCmp.Capability.carriage_return);
                terminal.puts(InfoCmp.Capability.clr_eol);
                printAssistantPrefix();
                printLineSegments(lines.get(i));
                terminal.writer().print("\n");
                hasPartialLine = false;
            } else {
                printAssistantPrefix();
                printLineSegments(lines.get(i));
                terminal.writer().print("\n");
            }
        }

        // Update partial last line (overwrite in-place)
        if (totalLines > completeCount) {
            RenderedLine partial = lines.get(completeCount);
            if (hasPartialLine) {
                terminal.puts(InfoCmp.Capability.carriage_return);
                terminal.puts(InfoCmp.Capability.clr_eol);
            }
            printAssistantPrefix();
            printLineSegments(partial);
            hasPartialLine = true;
            printedLineCount = completeCount;
        } else {
            // No partial line (content ended on a newline)
            printedLineCount = completeCount;
            hasPartialLine = false;
        }
        terminal.flush();
    }

    /**
     * Finalizes the current AI message: overwrites the displayed partial
     * line and prints all remaining (now styled) lines of the block.
     */
    void finalizeMessage(MessageBlock block) {
        List<RenderedLine> lines = block != null ? block.allLines() : List.of();
        if (hasPartialLine) {
            if (printedLineCount < lines.size()) {
                // Remaining styled lines will overwrite the partial row
                terminal.puts(InfoCmp.Capability.carriage_return);
                terminal.puts(InfoCmp.Capability.clr_eol);
            } else {
                // Nothing to overwrite with — keep the displayed text
                terminal.writer().print("\n");
            }
            hasPartialLine = false;
        }
        for (int i = printedLineCount; i < lines.size(); i++) {
            printAssistantPrefix();
            printLineSegments(lines.get(i));
            terminal.writer().print("\n");
        }
        terminal.flush();
        reset();
    }

    // ===== Tool rows =====

    void printToolCall(String toolName, String paramsSummary) {
        if (hasPartialLine) {
            // Finish the partial content line first
            terminal.writer().print("\n");
            hasPartialLine = false;
        }
        String shortName = shortenToolName(toolName);
        String line = "● " + shortName;
        if (paramsSummary != null && !paramsSummary.isEmpty()) {
            line += "(" + paramsSummary + ")";
        }
        line += "  → running";
        terminal.writer().print(new AttributedString(line, TOOL_STYLE).toAnsi(terminal));
        terminal.writer().print("\n");
        terminal.flush();
    }

    void printToolResult(String summary, boolean success) {
        if (hasPartialLine) {
            terminal.writer().print("\n");
            hasPartialLine = false;
        }
        AttributedStyle style = success ? TOOL_OK_STYLE : TOOL_ERR_STYLE;
        String prefix = success ? "  ✓ " : "  ✗ ";
        String line = prefix + (summary != null ? summary : "");
        terminal.writer().print(new AttributedString(line, style).toAnsi(terminal));
        terminal.writer().print("\n");
        terminal.flush();
    }

    // ===== Direct message printing =====

    void printUserMessage(String text) {
        if (hasPartialLine) {
            terminal.writer().print("\n");
            hasPartialLine = false;
        }
        terminal.writer().print(new AttributedString("You: ",
            AttributedStyle.DEFAULT.bold()).toAnsi(terminal));
        terminal.writer().print(text);
        terminal.writer().print("\n");
        terminal.flush();
    }

    void printSystemMessage(String text) {
        if (hasPartialLine) {
            terminal.writer().print("\n");
            hasPartialLine = false;
        }
        terminal.writer().print(new AttributedString(text, ROLE_SYSTEM).toAnsi(terminal));
        terminal.writer().print("\n");
        terminal.flush();
    }

    // ===== State management =====

    void reset() {
        printedLineCount = 0;
        hasPartialLine = false;
    }

    int printedLineCount() { return printedLineCount; }

    boolean hasPartialLine() { return hasPartialLine; }

    // ===== Internal helpers =====

    private void printAssistantPrefix() {
        terminal.writer().print(new AttributedString("│ ", ROLE_ASSISTANT).toAnsi(terminal));
    }

    private void printLineSegments(RenderedLine line) {
        for (AttributedString seg : line.segments()) {
            terminal.writer().print(seg.toAnsi(terminal));
        }
    }

    private static String shortenToolName(String toolName) {
        return switch (toolName) {
            case "read_file" -> "Read";
            case "write_file" -> "Write";
            case "edit_file" -> "Edit";
            case "execute_command" -> "Bash";
            case "search_file" -> "Glob";
            case "search_content" -> "Grep";
            default -> toolName;
        };
    }
}
