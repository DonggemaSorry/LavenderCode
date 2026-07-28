package com.lavendercode.chat.terminal;

import org.jline.terminal.Terminal;
import org.jline.utils.InfoCmp;

/**
 * 终端滚动区域绘制器：构造期一次性检测能力，任一缺失则 available=false（运行期零开销）。
 * 能力缺失时调用方降级到 drawViewport 全量路径，行为与优化前一致。
 */
final class ScrollRegionPainter {

    private final Terminal terminal;
    private final boolean available;

    ScrollRegionPainter(Terminal terminal) {
        this.terminal = terminal;
        this.available = terminal.getStringCapability(InfoCmp.Capability.change_scroll_region) != null
            && terminal.getStringCapability(InfoCmp.Capability.parm_index) != null
            && terminal.getStringCapability(InfoCmp.Capability.clr_eol) != null;
    }

    boolean available() {
        return available;
    }

    /**
     * 将 [topRow, bottomRow] 屏幕区域内的内容上移 lines 行（底部空出 lines 行）。
     * 状态栏、分隔线、输入区在区域之外，不受滚动影响。
     */
    void scrollUp(int topRow, int bottomRow, int lines) {
        terminal.puts(InfoCmp.Capability.change_scroll_region, topRow, bottomRow);
        terminal.puts(InfoCmp.Capability.cursor_address, bottomRow, 0);
        terminal.puts(InfoCmp.Capability.parm_index, lines);
        terminal.puts(InfoCmp.Capability.change_scroll_region, 0, terminal.getHeight() - 1);
    }
}
