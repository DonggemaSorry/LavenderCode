package com.lavendercode.chat.terminal;

import org.jline.terminal.Size;
import org.jline.terminal.Terminal;
import org.jline.terminal.impl.DumbTerminal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.concurrent.LinkedBlockingQueue;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 输入流 EOF 或 requestShutdown 后，run() 必须在有限时间内返回，
 * 否则非守护 input 线程忙自旋，/exit 后 JVM 无法退出。
 */
class InputSystemEofShutdownTest {

    private Terminal terminal;
    private LinkedBlockingQueue<InputEvent> inputQueue;
    private Thread inputThread;

    @AfterEach
    void tearDown() throws Exception {
        if (inputThread != null && inputThread.isAlive()) {
            inputThread.interrupt();
        }
        if (terminal != null) terminal.close();
    }

    @Test
    void runShouldExitAndOfferShutdownOnEof() throws Exception {
        terminal = new DumbTerminal(new ByteArrayInputStream(new byte[0]), new ByteArrayOutputStream());
        terminal.setSize(new Size(80, 24));
        inputQueue = new LinkedBlockingQueue<>();
        var inputSystem = new InputSystem(
            terminal, inputQueue, new LinkedBlockingQueue<>(), new InputAreaLayout());

        inputThread = new Thread(inputSystem::run, "test-input");
        inputThread.start();
        inputThread.join(3000);

        assertThat(inputThread.isAlive())
            .as("EOF 后 run() 应退出而非忙自旋")
            .isFalse();
        assertThat(inputQueue).anyMatch(e -> e instanceof InputEvent.Shutdown);
    }

    @Test
    void readLoopShouldStopAfterRequestShutdown() throws Exception {
        terminal = new DumbTerminal(new ByteArrayInputStream(new byte[0]), new ByteArrayOutputStream());
        terminal.setSize(new Size(80, 24));
        inputQueue = new LinkedBlockingQueue<>();
        var inputSystem = new InputSystem(
            terminal, inputQueue, new LinkedBlockingQueue<>(), new InputAreaLayout());

        // 先置 shutdown 再启动：模拟 /exit 关停时序，读循环不得再自旋
        inputSystem.requestShutdown();
        inputThread = new Thread(inputSystem::run, "test-input");
        inputThread.start();
        inputThread.join(3000);

        assertThat(inputThread.isAlive())
            .as("requestShutdown 后 run() 应立即退出")
            .isFalse();
    }
}
