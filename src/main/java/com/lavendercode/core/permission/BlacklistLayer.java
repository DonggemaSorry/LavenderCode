package com.lavendercode.core.permission;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

public final class BlacklistLayer implements PermissionLayer {

    private static final List<Pattern> PATTERNS = List.of(
        Pattern.compile("rm\\s+-[^\\n]*\\s+/(\\s|$)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("rm\\s+-rf\\s+(/|~|\\$HOME)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("mkfs\\.", Pattern.CASE_INSENSITIVE),
        Pattern.compile(":\\(\\)\\s*\\{\\s*:\\|\\:&\\s*\\}\\s*;", Pattern.CASE_INSENSITIVE),
        Pattern.compile(">\\s*/dev/sd[a-z]", Pattern.CASE_INSENSITIVE));

    @Override
    public Optional<PermissionDecision> evaluate(ToolCallContext ctx) {
        if (ctx.category() != ToolCategory.COMMAND) {
            return Optional.empty();
        }
        String cmd = ctx.matchKey();
        for (Pattern pattern : PATTERNS) {
            if (pattern.matcher(cmd).find()) {
                return Optional.of(new PermissionDecision.Deny(
                    "BLACKLIST",
                    "危险命令已被系统内置策略拦截",
                    "请改用安全命令"));
            }
        }
        return Optional.empty();
    }
}
