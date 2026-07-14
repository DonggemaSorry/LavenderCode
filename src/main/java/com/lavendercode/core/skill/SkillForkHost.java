package com.lavendercode.core.skill;

import com.lavendercode.core.provider.Message;
import java.util.List;

public interface SkillForkHost extends SkillHost {
    String runSubAgent(String body, List<Message> seed,
                       List<String> allowedTools, String model);
    List<Message> snapshotParentMessages();
}
