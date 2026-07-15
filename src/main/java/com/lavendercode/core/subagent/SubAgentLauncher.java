package com.lavendercode.core.subagent;

import com.lavendercode.chat.session.InMemorySessionManager;
import com.lavendercode.core.provider.Message;
import com.lavendercode.core.tool.ToolDefinition;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;

public final class SubAgentLauncher {

    private SubAgentLauncher() {}

    public static String runDefinedInline(SubAgentServices services, AgentDefinition def,
                                          String prompt, AtomicBoolean cancelFlag) {
        var session = new InMemorySessionManager();
        var toolDefs = ToolFilter.filterDefinitions(def, false, false);
        var runner = services.createRunner(def);
        return runner.runToCompletion(session, toolDefs, prompt, cancelFlag);
    }

    public static String runWithSeed(SubAgentServices services, AgentDefinition def,
                                     List<Message> seed, String prompt,
                                     AtomicBoolean cancelFlag) {
        var session = new InMemorySessionManager();
        if (seed != null && !seed.isEmpty()) {
            session.replaceHistory(seed);
        }
        boolean fork = "__fork__".equals(def.name()) || def.name().startsWith("skill-fork");
        var toolDefs = ToolFilter.filterDefinitions(def, fork, false);
        var runner = services.createRunner(def);
        return runner.runToCompletion(session, toolDefs, prompt, cancelFlag);
    }

    public static String runFork(SubAgentServices services, String prompt,
                                 List<Message> seed, AtomicBoolean cancelFlag) {
        AgentDefinition def = AgentDefinition.forkBase(ForkBoilerplate.format(prompt));
        boolean background = true;
        var toolDefs = ToolFilter.filterDefinitions(def, true, background);
        var session = new InMemorySessionManager();
        if (seed != null && !seed.isEmpty()) {
            session.replaceHistory(seed);
        }
        var runner = services.createRunner(def);
        return runner.runToCompletion(session, toolDefs, prompt, cancelFlag);
    }

    public static Callable<String> buildWork(SubAgentServices services, AgentDefinition def,
                                             String prompt, boolean fork, boolean background,
                                             List<Message> seed, AtomicBoolean cancelFlag) {
        return buildWork(services, def, prompt, fork, background, seed, cancelFlag, null);
    }

    public static Callable<String> buildWork(SubAgentServices services, AgentDefinition def,
                                             String prompt, boolean fork, boolean background,
                                             List<Message> seed, AtomicBoolean cancelFlag,
                                             java.util.concurrent.atomic.AtomicReference<List<Message>> conversationOut) {
        return () -> {
            var session = new InMemorySessionManager();
            if (seed != null && !seed.isEmpty()) {
                session.replaceHistory(seed);
            }
            List<ToolDefinition> toolDefs = ToolFilter.filterDefinitions(def, fork, background);
            var runner = services.createRunner(def);
            String text = runner.runToCompletion(session, toolDefs, prompt, cancelFlag);
            if (conversationOut != null) {
                conversationOut.set(List.copyOf(session.getHistory()));
            }
            return text;
        };
    }
}
