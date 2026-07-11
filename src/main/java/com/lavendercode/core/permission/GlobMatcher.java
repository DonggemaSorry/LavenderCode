package com.lavendercode.core.permission;

import java.util.regex.Pattern;

public final class GlobMatcher {

    private GlobMatcher() {}

    public static boolean matches(String input, String glob, boolean pathMode) {
        if (glob.isEmpty()) {
            return true;
        }
        String regex = toRegex(glob, pathMode);
        return Pattern.compile("^" + regex + "$", Pattern.DOTALL).matcher(input).matches();
    }

    private static String toRegex(String glob, boolean pathMode) {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < glob.length()) {
            char c = glob.charAt(i);
            if (c == '*') {
                if (pathMode && i + 1 < glob.length() && glob.charAt(i + 1) == '*') {
                    sb.append(".*");
                    i += 2;
                    continue;
                }
                sb.append("[^/]*");
            } else if (c == '?') {
                sb.append("[^/]");
            } else if (".^$+{}[]|()\\".indexOf(c) >= 0) {
                sb.append('\\').append(c);
            } else {
                sb.append(c);
            }
            i++;
        }
        return sb.toString();
    }
}
