package com.lavendercode.core.command;

@FunctionalInterface
public interface CommandHandler {
    void execute(CommandContext ctx);
}