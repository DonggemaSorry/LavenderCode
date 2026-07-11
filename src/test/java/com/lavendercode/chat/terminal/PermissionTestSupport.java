package com.lavendercode.chat.terminal;

import com.lavendercode.core.permission.*;
import java.nio.file.Path;

final class PermissionTestSupport {
    private PermissionTestSupport() {}

    static BatchingToolExecutor bypassExecutor(long fileTimeoutSec, long commandTimeoutSec, Path projectRoot) {
        PermissionPipeline pipeline = PermissionPipeline.create(
            PermissionConfig.empty(),
            () -> PermissionMode.BYPASS_PERMISSIONS,
            (request, cancelFlag) -> HitlChoice.DENY,
            projectRoot,
            rules -> {});
        return new BatchingToolExecutor(fileTimeoutSec, commandTimeoutSec, pipeline, projectRoot);
    }
}
