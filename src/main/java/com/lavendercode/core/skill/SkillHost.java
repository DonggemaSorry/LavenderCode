package com.lavendercode.core.skill;

import java.util.function.Predicate;

public interface SkillHost {
    void activateSkill(String name, String body);
    void setToolFilter(Predicate<String> filter);
    boolean hasTool(String name);
}
