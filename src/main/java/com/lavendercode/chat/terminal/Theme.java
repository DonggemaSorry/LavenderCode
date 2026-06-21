package com.lavendercode.chat.terminal;

import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStyle;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

public record Theme(String name, Map<StyleCatalog, AttributedStyle> styles) {

    public Theme {
        Objects.requireNonNull(name);
        Objects.requireNonNull(styles);
    }

    public AttributedString apply(StyleCatalog key, String text) {
        AttributedStyle style = styles.getOrDefault(key, AttributedStyle.DEFAULT);
        return new AttributedString(text, style);
    }

    public static Theme dark() {
        Map<StyleCatalog, AttributedStyle> map = new EnumMap<>(StyleCatalog.class);
        map.put(StyleCatalog.USER_MESSAGE,    AttributedStyle.DEFAULT.foreground(0, 255, 255));
        map.put(StyleCatalog.ASSISTANT_MESSAGE, AttributedStyle.DEFAULT.foreground(255, 255, 255));
        map.put(StyleCatalog.ASSISTANT_BORDER,  AttributedStyle.DEFAULT.foreground(68, 68, 255));
        map.put(StyleCatalog.SYSTEM_MESSAGE,    AttributedStyle.DEFAULT.foreground(255, 255, 0).italic());
        map.put(StyleCatalog.CODE_BLOCK,        AttributedStyle.DEFAULT.foreground(255, 255, 255).background(68, 68, 68));
        map.put(StyleCatalog.THINKING_TEXT,     AttributedStyle.DEFAULT.foreground(136, 136, 136).italic());
        map.put(StyleCatalog.THINKING_LABEL,    AttributedStyle.DEFAULT.foreground(255, 0, 255));
        map.put(StyleCatalog.STATUS_BAR,        AttributedStyle.DEFAULT.foreground(0, 0, 0).background(0, 255, 255));
        map.put(StyleCatalog.SCROLLBAR_TRACK,   AttributedStyle.DEFAULT.foreground(85, 85, 85));
        map.put(StyleCatalog.SCROLLBAR_THUMB,   AttributedStyle.DEFAULT.foreground(255, 255, 255).bold());
        map.put(StyleCatalog.PROMPT,            AttributedStyle.DEFAULT.foreground(0, 255, 0).bold());
        map.put(StyleCatalog.INPUT_TEXT,        AttributedStyle.DEFAULT.foreground(255, 255, 255));
        map.put(StyleCatalog.INPUT_BG,          AttributedStyle.DEFAULT.foreground(255, 255, 255).background(60, 60, 60));
        map.put(StyleCatalog.INPUT_BORDER,      AttributedStyle.DEFAULT.foreground(136, 136, 136));
        return new Theme("dark", Collections.unmodifiableMap(map));
    }
}
