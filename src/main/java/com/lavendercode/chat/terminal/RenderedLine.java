package com.lavendercode.chat.terminal;

import org.jline.utils.AttributedString;
import java.util.List;
import java.util.Objects;

public record RenderedLine(List<AttributedString> segments) {
    public RenderedLine {
        Objects.requireNonNull(segments);
    }

    public RenderedLine(AttributedString segment) {
        this(List.of(segment));
    }
}
