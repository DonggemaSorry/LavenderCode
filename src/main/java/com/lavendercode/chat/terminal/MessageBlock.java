package com.lavendercode.chat.terminal;

import com.lavendercode.core.provider.Role;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStyle;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class MessageBlock {

    private final UUID id;
    private final Role role;
    private boolean isComplete;
    private boolean inCodeBlock;
    private final List<Segment> segments;

    public MessageBlock(Role role) {
        this.id = UUID.randomUUID();
        this.role = role;
        this.isComplete = false;
        this.inCodeBlock = false;
        this.segments = new ArrayList<>();
    }

    public Role role() { return role; }

    public boolean isComplete() {
        return isComplete;
    }

    public void markComplete() {
        this.isComplete = true;
    }

    public int lineCount() {
        return segments.stream().mapToInt(s -> s.lines.size()).sum();
    }

    public int append(String text, int terminalWidth) {
        int oldCount = lineCount();
        ensureLastContentSegment();
        ContentSegment last = (ContentSegment) segments.get(segments.size() - 1);
        last.rawText.append(text);
        last.lines.clear();
        wrapAndColor(last.rawText.toString(), terminalWidth, last.lines);
        return lineCount() - oldCount;
    }

    public int appendThinking(String text, int terminalWidth) {
        int oldCount = lineCount();
        ThinkingSegment last = findLastThinkingSegment();
        if (last == null) {
            last = new ThinkingSegment();
            segments.add(last);
        }
        last.rawText.append(text);
        last.lines.clear();
        wrapAsThinking(last.rawText.toString(), terminalWidth, last.lines);
        return lineCount() - oldCount;
    }

    public List<RenderedLine> allLines() {
        List<RenderedLine> result = new ArrayList<>();
        for (Segment seg : segments) {
            result.addAll(seg.lines);
        }
        return result;
    }

    public void reflow(int terminalWidth) {
        boolean savedInCode = inCodeBlock;
        inCodeBlock = false;
        for (Segment seg : segments) {
            seg.lines.clear();
            if (seg instanceof ContentSegment cs) {
                wrapAndColor(cs.rawText.toString(), terminalWidth, cs.lines);
            } else if (seg instanceof ThinkingSegment ts) {
                wrapAsThinking(ts.rawText.toString(), terminalWidth, ts.lines);
            }
        }
        inCodeBlock = savedInCode;
    }

    // --- Private helpers ---

    private void ensureLastContentSegment() {
        if (segments.isEmpty() || !(segments.get(segments.size() - 1) instanceof ContentSegment)) {
            segments.add(new ContentSegment());
        }
    }

    private ThinkingSegment findLastThinkingSegment() {
        for (int i = segments.size() - 1; i >= 0; i--) {
            if (segments.get(i) instanceof ThinkingSegment ts) {
                return ts;
            }
        }
        return null;
    }

    private void wrapAndColor(String raw, int width, List<RenderedLine> out) {
        StringBuilder currentLine = new StringBuilder();
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '\n') {
                flushLineToOutput(currentLine.toString(), width, out);
                currentLine.setLength(0);
            } else {
                currentLine.append(c);
            }
        }
        if (currentLine.length() > 0) {
            flushLineToOutput(currentLine.toString(), width, out);
        }
    }

    private void flushLineToOutput(String line, int width, List<RenderedLine> out) {
        if (line.startsWith("```")) {
            out.add(new RenderedLine(new AttributedString(line,
                    AttributedStyle.DEFAULT.foreground(136, 136, 136))));
            inCodeBlock = !inCodeBlock;
            return;
        }
        int start = 0;
        while (start < line.length()) {
            int end = Math.min(start + width, line.length());
            String seg = line.substring(start, end);
            if (inCodeBlock) {
                out.add(new RenderedLine(new AttributedString(seg,
                        AttributedStyle.DEFAULT.background(68, 68, 68))));
            } else {
                out.add(new RenderedLine(new AttributedString(seg)));
            }
            start = end;
        }
    }

    private void wrapAsThinking(String raw, int width, List<RenderedLine> out) {
        for (String line : raw.split("\n", -1)) {
            int start = 0;
            while (start < line.length()) {
                int end = Math.min(start + width, line.length());
                out.add(new RenderedLine(new AttributedString(line.substring(start, end),
                        AttributedStyle.DEFAULT.italic())));
                start = end;
            }
        }
    }

    // --- Inner types ---

    private abstract static sealed class Segment
            permits ContentSegment, ThinkingSegment {
        final List<RenderedLine> lines = new ArrayList<>();
    }

    private static final class ContentSegment extends Segment {
        final StringBuilder rawText = new StringBuilder();
    }

    private static final class ThinkingSegment extends Segment {
        final StringBuilder rawText = new StringBuilder();
    }
}
