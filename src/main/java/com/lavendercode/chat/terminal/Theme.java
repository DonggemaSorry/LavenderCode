package com.lavendercode.chat.terminal;

import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStyle;
import java.util.Map;

public record Theme(String name, Map<StyleCatalog, AttributedStyle> styles) {
    public AttributedString apply(StyleCatalog key, String text) {
        return new AttributedString(text, styles.getOrDefault(key, AttributedStyle.DEFAULT));
    }

    public static Theme dark() {
        return new Theme("dark", Map.of());
    }
}
