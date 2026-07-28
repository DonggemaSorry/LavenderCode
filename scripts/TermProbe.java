import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.InfoCmp;

/**
 * 终端能力探针：在真实终端里运行，打印 JLine 检测到的终端类型与滚动区域相关能力。
 * 运行方式（在平时启动 LavenderCode 的同一个终端窗口中）：
 *   java -cp "%USERPROFILE%\.m2\repository\org\jline\jline\3.26.3\jline-3.26.3.jar" scripts\TermProbe.java
 */
public class TermProbe {
    public static void main(String[] args) throws Exception {
        Terminal t = TerminalBuilder.builder().name("probe").system(true).build();
        System.out.println("terminal class = " + t.getClass().getName());
        System.out.println("terminal type  = " + t.getType());
        System.out.println("size           = " + t.getWidth() + "x" + t.getHeight());
        print(t, InfoCmp.Capability.change_scroll_region);
        print(t, InfoCmp.Capability.parm_index);
        print(t, InfoCmp.Capability.clr_eol);
        print(t, InfoCmp.Capability.cursor_address);
        boolean fastPath = t.getStringCapability(InfoCmp.Capability.change_scroll_region) != null
            && t.getStringCapability(InfoCmp.Capability.parm_index) != null
            && t.getStringCapability(InfoCmp.Capability.clr_eol) != null;
        System.out.println("ScrollRegionPainter.available = " + fastPath);
        t.close();
    }

    private static void print(Terminal t, InfoCmp.Capability cap) {
        String v = t.getStringCapability(cap);
        System.out.println(String.format("%-22s = %s", cap.name(), v == null ? "<MISSING>" : v.replace("\u001b", "ESC")));
    }
}
