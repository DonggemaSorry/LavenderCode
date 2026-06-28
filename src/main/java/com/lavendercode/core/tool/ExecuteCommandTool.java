package com.lavendercode.core.tool;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class ExecuteCommandTool implements Tool {
    private final boolean enabled;
    private final long timeoutSeconds;
    private final int maxOutputChars;

    public ExecuteCommandTool(boolean enabled, long timeoutSeconds, int maxOutputChars) {
        this.enabled = enabled;
        this.timeoutSeconds = timeoutSeconds;
        this.maxOutputChars = maxOutputChars;
    }

    @Override
    public String name() {
        return "execute_command";
    }

    @Override
    public String description() {
        return "Executes a shell command in the working directory. Returns stdout, stderr, and exit code.";
    }

    @Override
    public ToolParameterSchema parameters() {
        return new ToolParameterSchema("object",
            Map.of(
                "command", new ToolParameterSchema.PropertyDef("string", "Shell 命令", null, null),
                "working_dir", new ToolParameterSchema.PropertyDef("string", "工作目录", null, null)
            ), List.of("command"));
    }

    @Override
    public ToolResult execute(Map<String, Object> params) {
        if (!enabled) {
            return ToolResult.error("COMMAND_DISABLED", "命令执行已禁用", "Command execution is disabled in configuration");
        }

        String command = (String) params.get("command");
        if (command == null || command.isBlank()) {
            return ToolResult.error("INVALID_PARAMETER", "命令为空", "command is null or blank");
        }

        String workingDir = (String) params.get("working_dir");
        File dir = workingDir != null ? new File(workingDir) : new File(System.getProperty("user.dir"));

        try {
            ProcessBuilder pb = new ProcessBuilder();
            boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
            if (isWindows) {
                pb.command("cmd.exe", "/c", command);
            } else {
                pb.command("sh", "-c", command);
            }
            pb.directory(dir);
            pb.redirectErrorStream(false);

            Process process = pb.start();

            CompletableFuture<ToolResult> future = CompletableFuture.supplyAsync(() -> {
                try {
                    String stdout = readStream(process.getInputStream(), maxOutputChars);
                    String stderr = readStream(process.getErrorStream(), maxOutputChars);
                    int exitCode = process.waitFor();
                    if (exitCode == 0) {
                        long lineCount = stdout.lines().count();
                        return ToolResult.success(exitCode + "·" + lineCount + "行", stdout);
                    } else {
                        String firstStderrLine = stderr.lines().findFirst().orElse("");
                        return ToolResult.error("COMMAND_FAILED",
                            "失败 (exit " + exitCode + ")·" + firstStderrLine,
                            "stdout:\n" + stdout + "\nstderr:\n" + stderr);
                    }
                } catch (IOException | InterruptedException e) {
                    if (e instanceof InterruptedException) {
                        Thread.currentThread().interrupt();
                        return ToolResult.error("TOOL_ERROR", "执行中断", e.getMessage());
                    }
                    return ToolResult.error("TOOL_ERROR", "读取输出失败", e.getMessage());
                }
            });

            try {
                return future.orTimeout(timeoutSeconds, TimeUnit.SECONDS)
                    .exceptionally(ex -> {
                        process.destroyForcibly();
                        return ToolResult.error("TIMEOUT",
                            "超时 (" + timeoutSeconds + "s)·" + name(),
                            ex.getMessage());
                    }).get();
            } catch (Exception e) {
                process.destroyForcibly();
                return ToolResult.error("TIMEOUT",
                    "超时 (" + timeoutSeconds + "s)·" + name(),
                    e.getMessage());
            }
        } catch (IOException e) {
            return ToolResult.error("TOOL_ERROR", "命令执行失败", e.getMessage());
        }
    }

    private String readStream(InputStream inputStream, int maxChars) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] data = new byte[8192];
        int totalRead = 0;
        int nRead;
        while ((nRead = inputStream.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
            totalRead += nRead;
            if (totalRead > maxChars) {
                break;
            }
        }
        String result = buffer.toString();
        if (result.length() > maxChars) {
            result = result.substring(0, maxChars) + "\n...[truncated]";
        }
        return result;
    }
}
