package cn.bcd.lib.vehicle.can.util;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class CANUtil_cornexTest {
    @Test
    void testCount() throws IOException {
        String dir = "C:\\Users\\Cornex\\Desktop\\CCU\\矩阵";
        try (Stream<Path> stream = Files.list(Paths.get(dir))) {
            List<Path> list = stream.filter(e -> {
                try {
                    return !Files.isDirectory(e)
                            && e.toString().endsWith(".xlsx")
                            && !Files.isHidden(e);
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                }
            }).toList();
            int ccuSendCount = 0;
            Set<String> set = new HashSet<>();
            for (Path path : list) {
                CANUtil_cornex.CanWorkbook workbook = CANUtil_cornex.parse(path.toFile());
                for (CANUtil_cornex.CanSheet sheet : workbook.sheets) {
                    List<CANUtil_cornex.CanMessage> messages = sheet.messages;
                    int count = 0;
                    for (CANUtil_cornex.CanMessage canMessage : messages) {
                        for (CANUtil_cornex.CanSignal canSignal : canMessage.signals) {
                            count++;
                            CANUtil_cornex.NodeDirection nodeDirection = canSignal.nodeDirections.get("CCU");
                            if (nodeDirection == CANUtil_cornex.NodeDirection.TX) {
                                if (!set.contains(canSignal.name)) {
                                    set.add(canSignal.name);
                                    ccuSendCount++;
                                }
                            }
                        }
                    }
                    System.out.println(workbook.name + " " + sheet.name + " " + count);
                }
            }
            System.out.println("ccuSendCount: "+ ccuSendCount);
        }
    }
}
