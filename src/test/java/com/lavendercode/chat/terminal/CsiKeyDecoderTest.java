package com.lavendercode.chat.terminal;

import org.junit.jupiter.api.Test;

import static com.lavendercode.chat.terminal.CsiKeyDecoder.*;
import static org.assertj.core.api.Assertions.assertThat;

class CsiKeyDecoderTest {

    @Test
    void shouldDecodeArrowKeys() {
        assertThat(CsiKeyDecoder.decodeCsi("", 'A')).isEqualTo(KEY_SCROLL_UP);
        assertThat(CsiKeyDecoder.decodeCsi("", 'B')).isEqualTo(KEY_SCROLL_DOWN);
    }

    @Test
    void shouldDecodeHomeAndEnd() {
        assertThat(CsiKeyDecoder.decodeCsi("", 'H')).isEqualTo(KEY_SCROLL_TOP);
        assertThat(CsiKeyDecoder.decodeCsi("", 'F')).isEqualTo(KEY_SCROLL_BOTTOM);
        assertThat(CsiKeyDecoder.decodeCsi("1", '~')).isEqualTo(KEY_SCROLL_TOP);
        assertThat(CsiKeyDecoder.decodeCsi("4", '~')).isEqualTo(KEY_SCROLL_BOTTOM);
    }

    @Test
    void shouldDecodePageKeys() {
        assertThat(CsiKeyDecoder.decodeCsi("5", '~')).isEqualTo(KEY_PAGE_UP);
        assertThat(CsiKeyDecoder.decodeCsi("6", '~')).isEqualTo(KEY_PAGE_DOWN);
    }

    @Test
    void shouldDecodeSs3Arrows() {
        assertThat(CsiKeyDecoder.decodeSs3('A')).isEqualTo(KEY_SCROLL_UP);
        assertThat(CsiKeyDecoder.decodeSs3('F')).isEqualTo(KEY_SCROLL_BOTTOM);
    }

    @Test
    void shouldDecodeMouseWheel() {
        assertThat(CsiKeyDecoder.decodeMouse("64;10;20")).isEqualTo(KEY_WHEEL_UP);
        assertThat(CsiKeyDecoder.decodeMouse("65;10;20")).isEqualTo(KEY_WHEEL_DOWN);
    }

    @Test
    void shouldMapKeysToScrollCommands() {
        assertThat(CsiKeyDecoder.toScrollCommand(KEY_SCROLL_UP)).isEqualTo("up");
        assertThat(CsiKeyDecoder.toScrollCommand(KEY_PAGE_DOWN)).isEqualTo("page-down");
        assertThat(CsiKeyDecoder.toScrollCommand(KEY_SCROLL_BOTTOM)).isEqualTo("bottom");
        assertThat(CsiKeyDecoder.toScrollCommand(KEY_WHEEL_UP)).isEqualTo("up");
        assertThat(CsiKeyDecoder.toScrollCommand('a')).isNull();
    }
}
