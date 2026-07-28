package com.lavendercode.chat.terminal;

/** 渲染帧内需要重绘的屏幕区域。paintFrame 按脏区域最小化绘制。 */
enum DirtyRegion {
    STATUS_BAR, VIEWPORT, INPUT_AREA, COMPLETION_MENU, PERMISSION_PROMPT
}
