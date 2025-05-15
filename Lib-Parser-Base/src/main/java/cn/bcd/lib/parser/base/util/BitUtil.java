package cn.bcd.lib.parser.base.util;

import cn.bcd.lib.parser.base.anno.F_bit_num;

import java.util.ArrayList;
import java.util.Comparator;

public class BitUtil {

    public static class Signal {
        public final String name;
        public final int startBit;
        public final int length;

        //计算值
        //计算出翻转后的结束位
        public int reserve_endBit;
        //计算出翻转后的起始位
        public int reserve_startBit;
        //计算字段前应该跳过位
        public int skip_before;

        public Signal(String name, int startBit, int length) {
            this.name = name;
            this.startBit = startBit;
            this.length = length;
        }

        @Override
        public String toString() {
            return "{" +
                    "name=\"" + name + "\"" +
                    ", skip_before=" + skip_before +
                    ", length=" + length +
                    ", startBit=" + startBit +
                    ", reserve_startBit=" + reserve_endBit +
                    ", reserve_endBit=" + reserve_startBit +
                    '}';
        }
    }

    /**
     * 按照CAN矩阵 Motorola_LSB 信号规则
     * 计算出配合{@link F_bit_num}使用的相应属性
     *
     * @param signals
     * @return
     */
    public static Signal[] calc_Motorola_LSB(Signal[] signals) {
        ArrayList<Signal> list = new ArrayList<>();
        //计算结束位、翻转起始位
        for (Signal signal : signals) {
            int row = signal.startBit / 8 + 1;
            int leave = signal.startBit % 8;
            signal.reserve_endBit = row * 8 - leave - 1;
            signal.reserve_startBit = signal.reserve_endBit - signal.length + 1;
            list.add(signal);
        }

        //排序后计算skip
        list.sort(Comparator.comparing(e -> e.reserve_startBit));
        int pos = 0;
        for (Signal signal : list) {
            int skip = signal.reserve_startBit - pos;
            if (skip > 0) {
                signal.skip_before = skip;
                pos += skip;
            }
            pos += signal.length;
        }

        return list.toArray(new Signal[0]);
    }

    public static void main(String[] args) {
        Signal[] res = calc_Motorola_LSB(new Signal[]{
                new Signal("EMS_EngineSpeedValid", 2, 1),
                new Signal("EMS_ThrottlePositionValid", 3, 1),
                new Signal("EMS_IntakeAirTempValid", 5, 1),
                new Signal("EMS_EngineLimpHome", 6, 1),
                new Signal("EMS_IdleSpeedStatus", 7, 1),
                new Signal("EMS_EngineSpeed", 16, 16),
                new Signal("EMS_ThrottlePosition", 24, 8),
                new Signal("EMS_IntakeAirTemp", 40, 8),
        });
        for (Signal signal : res) {
            System.out.println(signal);
        }
    }
}
