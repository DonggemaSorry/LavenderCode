package com.lavendercode.chat.terminal;

import static org.assertj.core.api.Assertions.assertThat;

import com.lavendercode.core.config.Options;
import com.lavendercode.core.coordinator.Coordinator;
import com.lavendercode.core.permission.PermissionMode;
import com.lavendercode.core.tool.TeamCreateTool;
import com.lavendercode.core.tool.TeamSendMessageTool;
import com.lavendercode.core.tool.TeamTaskCreateTool;
import com.lavendercode.core.tool.ToolRegistry;
import com.lavendercode.core.tool.WriteFileTool;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LeadToolVisibilityTest {
    @BeforeEach
    void setUp() {
        ToolRegistry.clear();
        ToolRegistry.register(new WriteFileTool());
        ToolRegistry.register(new TeamCreateTool(null));
        ToolRegistry.register(new TeamTaskCreateTool(null));
        ToolRegistry.register(new TeamSendMessageTool(null));
    }

    @AfterEach
    void tearDown() {
        ToolRegistry.clear();
    }

    @Test
    void mainAgentHidesTeamCollabTools() {
        var mgr = new PermissionModeManager(PermissionMode.DEFAULT);
        var names = mgr.getToolDefinitions(true).stream().map(d -> d.name()).toList();
        assertThat(names).contains("TeamCreate", "write_file");
        assertThat(names).doesNotContain("TeamTaskCreate", "TeamSendMessage");
    }

    @Test
    void coordinatorWhitelistDropsWriteFile() {
        var mgr = new PermissionModeManager(PermissionMode.DEFAULT);
        mgr.setCoordinatorMode(true);
        var names = mgr.getToolDefinitions(true).stream().map(d -> d.name()).toList();
        assertThat(names).doesNotContain("write_file");
        assertThat(names).contains("TeamCreate", "TeamTaskCreate", "TeamSendMessage");
        assertThat(Coordinator.ALLOWED_TOOLS).containsAll(names);
    }
}
