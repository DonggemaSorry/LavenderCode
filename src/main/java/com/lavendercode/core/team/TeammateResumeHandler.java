package com.lavendercode.core.team;

@FunctionalInterface
public interface TeammateResumeHandler {
    void resumeIfStopped(Team team, TeammateInfo member, String message) throws Exception;
}
