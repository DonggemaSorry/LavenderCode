package com.lavendercode.chat.terminal;

import org.jline.terminal.Terminal;
import org.jline.utils.InfoCmp;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ScrollRegionPainterTest {

    private Terminal capableTerminal() {
        Terminal t = mock(Terminal.class);
        when(t.getStringCapability(InfoCmp.Capability.change_scroll_region)).thenReturn("csr");
        when(t.getStringCapability(InfoCmp.Capability.parm_index)).thenReturn("indn");
        when(t.getStringCapability(InfoCmp.Capability.clr_eol)).thenReturn("el");
        when(t.getHeight()).thenReturn(24);
        return t;
    }

    @Test
    void availableShouldBeTrueWhenAllCapabilitiesPresent() {
        assertThat(new ScrollRegionPainter(capableTerminal()).available()).isTrue();
    }

    @Test
    void availableShouldBeFalseWhenScrollRegionMissing() {
        Terminal t = capableTerminal();
        when(t.getStringCapability(InfoCmp.Capability.change_scroll_region)).thenReturn(null);
        assertThat(new ScrollRegionPainter(t).available()).isFalse();
    }

    @Test
    void availableShouldBeFalseWhenParmIndexMissing() {
        Terminal t = capableTerminal();
        when(t.getStringCapability(InfoCmp.Capability.parm_index)).thenReturn(null);
        assertThat(new ScrollRegionPainter(t).available()).isFalse();
    }

    @Test
    void availableShouldBeFalseWhenClrEolMissing() {
        Terminal t = capableTerminal();
        when(t.getStringCapability(InfoCmp.Capability.clr_eol)).thenReturn(null);
        assertThat(new ScrollRegionPainter(t).available()).isFalse();
    }

    @Test
    void scrollUpShouldEmitCsrIndnThenRestoreCsr() {
        Terminal t = capableTerminal();
        ScrollRegionPainter painter = new ScrollRegionPainter(t);

        painter.scrollUp(1, 20, 3);

        InOrder order = inOrder(t);
        order.verify(t).puts(InfoCmp.Capability.change_scroll_region, 1, 20);
        order.verify(t).puts(InfoCmp.Capability.cursor_address, 20, 0);
        order.verify(t).puts(InfoCmp.Capability.parm_index, 3);
        order.verify(t).puts(InfoCmp.Capability.change_scroll_region, 0, 23);
    }
}
