package com.lavendercode.core.permission;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

public final class ModeFallbackLayer implements PermissionLayer {

    private static final Map<PermissionMode, Map<ToolCategory, PermissionDecision>> MATRIX = buildMatrix();

    private final Supplier<PermissionMode> modeSupplier;

    public ModeFallbackLayer(Supplier<PermissionMode> modeSupplier) {
        this.modeSupplier = modeSupplier;
    }

    @Override
    public Optional<PermissionDecision> evaluate(ToolCallContext ctx) {
        PermissionMode mode = modeSupplier.get();
        PermissionDecision decision = MATRIX.get(mode).get(ctx.category());
        return Optional.of(decision);
    }

    private static Map<PermissionMode, Map<ToolCategory, PermissionDecision>> buildMatrix() {
        Map<PermissionMode, Map<ToolCategory, PermissionDecision>> matrix = new EnumMap<>(PermissionMode.class);

        matrix.put(PermissionMode.DEFAULT, Map.of(
            ToolCategory.READ_ONLY, new PermissionDecision.Allow(),
            ToolCategory.FILE_WRITE, new PermissionDecision.Ask("default 模式下文件写入需确认"),
            ToolCategory.COMMAND, new PermissionDecision.Ask("default 模式下命令执行需确认")));

        matrix.put(PermissionMode.ACCEPT_EDITS, Map.of(
            ToolCategory.READ_ONLY, new PermissionDecision.Allow(),
            ToolCategory.FILE_WRITE, new PermissionDecision.Allow(),
            ToolCategory.COMMAND, new PermissionDecision.Ask("acceptEdits 模式下命令执行需确认")));

        matrix.put(PermissionMode.PLAN, Map.of(
            ToolCategory.READ_ONLY, new PermissionDecision.Allow(),
            ToolCategory.FILE_WRITE, new PermissionDecision.Ask("plan 模式下文件写入需确认"),
            ToolCategory.COMMAND, new PermissionDecision.Ask("plan 模式下命令执行需确认")));

        matrix.put(PermissionMode.BYPASS_PERMISSIONS, Map.of(
            ToolCategory.READ_ONLY, new PermissionDecision.Allow(),
            ToolCategory.FILE_WRITE, new PermissionDecision.Allow(),
            ToolCategory.COMMAND, new PermissionDecision.Allow()));

        return matrix;
    }
}
