package com.lavendercode.core.command;

@FunctionalInterface
public interface CommandHandler {
    String execute(CommandContext ctx, String args);
}