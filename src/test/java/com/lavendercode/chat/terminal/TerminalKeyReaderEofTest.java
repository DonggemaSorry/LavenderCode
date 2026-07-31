package com.lavendercode.chat.terminal;

import org.jline.terminal.Terminal;
import org.jline.terminal.impl.DumbTerminal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * EOF（read() == -1）必须转换为 {@link TerminalInput.Exit}，
 * 否则 InputSystem.readEditedLine 会把 Character(-1) 静默忽略并无限忙自旋，
 * 导致非守护 input 线程永不退出、/exit 后 JVM 无法终止。
 */
class TerminalKeyReaderEofTest {

    private Terminal terminal;

    @AfterEach
    void tearDown() throws Exception {
        if (terminal != null) terminal.close();
    }

    @Test
    void readInputShouldReturnExitOnEof() throws Exception {
        // 空输入流：首次 read() 即返回 -1
        terminal = new DumbTerminal(new ByteArrayInputStream(new byte[0]), new ByteArrayOutputStream());
        var keyReader = new TerminalKeyReader(terminal);

        TerminalInput input = keyReader.readInput();

        assertThat(input).isInstanceOf(TerminalInput.Exit.class);
    }

    @Test
    void readInputShouldReturnExitOnEofAfterContent() throws Exception {
        // 消费完普通字符后再遇 EOF，仍须产生 Exit 而非 Character(-1)
        terminal = new DumbTerminal(new ByteArrayInputStream("a".getBytes()), new ByteArrayOutputStream());
        var keyReader = new TerminalKeyReader(terminal);

        assertThat(keyReader.readInput()).isEqualTo(new TerminalInput.Character('a'));
        assertThat(keyReader.readInput()).isInstanceOf(TerminalInput.Exit.class);
    }
}
