package cn.bcd.lib.vehicle.can.util;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class CANUtil_cornexTest {
    private static final File TEST_FILE = new File("../test.xlsx");

    @Test
    void parsesCanMatrixAndBuildsMessageSignalHierarchy() {
        CANUtil_cornex.CanSheet sheet = CANUtil_cornex.parse(TEST_FILE, "CCU-CcuCANFD");

        assertEquals("CCU-CcuCANFD", sheet.name);
        assertEquals(6, sheet.nodes.size());
        assertFalse(sheet.messages.isEmpty());

        CANUtil_cornex.CanMessage message = sheet.messages.getFirst();
        assertEquals("HmiSet_Fr01", message.name);
        assertEquals(0x110L, message.id);
        assertEquals(48, message.lengthBytes);
        assertEquals(CANUtil_cornex.NodeDirection.TX, message.nodeDirections.get("CCU"));
        assertFalse(message.signals.isEmpty());

        CANUtil_cornex.CanSignal signal = message.signals.getFirst();
        assertEquals("E2E_CheckSum_HmiSet_Pdu01_1", signal.name);
        assertEquals("Motorola MSB", signal.byteOrder);
        assertEquals(0, signal.startByte);
        assertEquals(7, signal.startBit);
        assertEquals(8, signal.lengthBits);
        assertEquals(BigDecimal.ONE, signal.resolution);
        assertEquals(CANUtil_cornex.NodeDirection.RX, signal.nodeDirections.get("AMP"));
    }

    @Test
    void discoversCanSheetsInWorkbook() {
        CANUtil_cornex.CanWorkbook workbook = CANUtil_cornex.parse(TEST_FILE);

        assertEquals("test.xlsx", workbook.name);
        assertEquals(1, workbook.sheets.size());
        assertEquals("CCU-CcuCANFD", workbook.sheets.getFirst().name);
    }
}
