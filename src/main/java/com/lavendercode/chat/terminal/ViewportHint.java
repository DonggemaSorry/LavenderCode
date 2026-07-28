package com.lavendercode.chat.terminal;

/**
 * VIEWPORT 脏时携带的变化提示，决定 paintFrame 的绘制策略。
 * null（无实例）= 全量视口重绘。
 */
sealed interface ViewportHint {

    /**
     * autoScroll 末尾追加：scrollUp(scrolled) 后从 firstDirtyContentRow 重画到视口底部。
     * prevThumbRow 为滚动前滚动条 thumb 所在屏幕行（scrollUp 会把旧 thumb 字符一起上移，需擦除残留）。
     */
    record ScrollAppend(int scrolled, int firstDirtyContentRow, int prevThumbRow) implements ViewportHint {}

    /**
     * 局部行变化（用户上翻中追加等）：从 contentRow 起 count 行内容变化（多半在屏外，仅更新滚动条）。
     * prevThumbRow 为变化前 thumb 屏幕行（上翻时视口无滚动，thumb 仅因 totalLines 增长而移动）。
     */
    record DiffRows(int contentRow, int count, int prevThumbRow) implements ViewportHint {}
}
