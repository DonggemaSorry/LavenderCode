package com.lavendercode.core.task;

public final class TaskNotificationFormatter {

    private TaskNotificationFormatter() {}

    public static String format(BackgroundTask task) {
        String statusWord = switch (task.status()) {
            case COMPLETED -> "completed";
            case FAILED -> "failed";
            case CANCELLED -> "cancelled";
            case RUNNING -> "running";
        };
        StringBuilder sb = new StringBuilder();
        sb.append("<task-notification>\n");
        sb.append("Task ").append(task.id())
            .append(" (name=\"").append(task.name()).append("\"): ")
            .append(statusWord).append('\n');
        if (task.result() != null && !task.result().isBlank()) {
            sb.append("Result: ").append(task.result()).append('\n');
        } else if (task.error() != null && !task.error().isBlank()) {
            sb.append("Result: ").append(task.error()).append('\n');
        }
        sb.append("</task-notification>");
        return sb.toString();
    }
}
