package com.lavendercode.core.permission;

import com.lavendercode.core.tool.ToolCall;
import java.nio.file.Path;
import java.util.List;

public record ToolCallContext(
    ToolCall rawCall,
    String registryName,
    String friendlyName,
    ToolCategory category,
    String matchKey,
    List<Path> sandboxPaths,
    Path projectRoot,
    boolean parseFailed) {}
