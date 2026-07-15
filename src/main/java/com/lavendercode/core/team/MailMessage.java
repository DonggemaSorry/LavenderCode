package com.lavendercode.core.team;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MailMessage(
    String from,
    String to,
    String type,
    String summary,
    String content,
    Object payload,
    long timestamp,
    boolean read
) {
    public static MailMessage text(String from, String to, String summary, String content) {
        return new MailMessage(from, to, "text", summary, content, null,
            System.currentTimeMillis(), false);
    }

    public MailMessage withRead(boolean read) {
        return new MailMessage(from, to, type, summary, content, payload, timestamp, read);
    }
}
