package cn.bcd.lib.vehicle.can.util;

import org.apache.fesod.sheet.FesodSheet;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CANUtil_cornexExportTest {
    @Test
    void writesSignalsWithFieldNamesAndChineseDescriptions() {
        CANUtil_cornex.CanSignal signal = new CANUtil_cornex.CanSignal(
                "VehicleSpeed", null, null, null, null, null, "车速", "Motorola MSB",
                0, 7, "Cyclic", 16, "unsigned", BigDecimal.ONE, BigDecimal.ZERO,
                BigDecimal.ZERO, new BigDecimal("65535"), "0x0", "0xFFFF", "0x0",
                "0xFFFF", null, "km/h", null, null,
                Map.of("AMP", CANUtil_cornex.NodeDirection.RX));
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        CANUtil_cornex.writeSignals(outputStream, List.of(signal));

        List<Map<Integer, String>> rows = FesodSheet.read(new ByteArrayInputStream(outputStream.toByteArray()))
                .headRowNumber(0)
                .sheet("CAN Signals")
                .doReadSync();
        assertEquals(2, rows.size());
        assertEquals("name\n信号名称", rows.getFirst().get(0));
        assertEquals("nodeDirections.AMP\nAMP收发方向", rows.getFirst().get(25));
        assertEquals("VehicleSpeed", rows.get(1).get(0));
        assertEquals("Rx", rows.get(1).get(25));
    }
}
