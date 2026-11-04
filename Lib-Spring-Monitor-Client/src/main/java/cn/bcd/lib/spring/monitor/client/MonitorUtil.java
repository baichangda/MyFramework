package cn.bcd.lib.spring.monitor.client;

import cn.bcd.lib.base.util.FloatUtil;
import oshi.SystemInfo;
import oshi.hardware.*;
import oshi.software.os.OSFileStore;
import oshi.software.os.OperatingSystem;
import oshi.util.Util;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

/** 系统监控信息采集工具，容量使用 GiB，速率使用 KiB/s。 */
public class MonitorUtil {
    private static final double GB = 1024d * 1024 * 1024;
    private static final double KB = 1024d;

    // 复用系统信息，设备和采样快照仍由每次调用单独持有。
    private static final SystemInfo SYSTEM_INFO = new SystemInfo();

    /** 同步采集，包含约一秒的采样等待。 */
    public static SystemData collect() {
        return collect(SYSTEM_INFO.getHardware(), SYSTEM_INFO.getOperatingSystem(),
                System::nanoTime, () -> Util.sleep(1000));
    }

    // 注入时钟和等待动作，使测试无需依赖真实硬件或实际等待一秒。
    static SystemData collect(HardwareAbstractionLayer hardware, OperatingSystem os,
                              LongSupplier clock, Runnable pause) {
        SystemData data = new SystemData();
        CentralProcessor processor = hardware.getProcessor();
        data.setPhysicalProcessorNum(processor.getPhysicalProcessorCount());
        data.setLogicalProcessorNum(processor.getLogicalProcessorCount());
        long[] cpuTicks = processor.getSystemCpuLoadTicks();

        GlobalMemory memory = hardware.getMemory();
        double memoryTotal = Math.max(0, memory.getTotal());
        double memoryUsed = used(memoryTotal, memory.getAvailable());
        data.setMemoryMax(FloatUtil.round(memoryTotal / GB, 2));
        data.setMemoryUse(FloatUtil.round(memoryUsed / GB, 2));
        data.setMemoryUsePercent(percent(memoryUsed, memoryTotal));

        double diskTotal = 0;
        double diskUsed = 0;
        for (OSFileStore store : os.getFileSystem().getFileStores()) {
            double total = Math.max(0, store.getTotalSpace());
            diskTotal += total;
            diskUsed += used(total, store.getUsableSpace());
        }
        data.setDiskMax(FloatUtil.round(diskTotal / GB, 2));
        data.setDiskUse(FloatUtil.round(diskUsed / GB, 2));
        data.setDiskUsePercent(percent(diskUsed, diskTotal));

        // 每次调用保留自己的设备和快照。采样期间只刷新同一设备，新增设备下次再统计。
        List<IoSample> disks = new ArrayList<>();
        for (HWDiskStore disk : hardware.getDiskStores()) {
            sample(disks, disk::updateAttributes, disk::getReadBytes, disk::getWriteBytes, clock);
        }
        List<IoSample> networks = new ArrayList<>();
        for (NetworkIF network : hardware.getNetworkIFs()) {
            sample(networks, network::updateAttributes, network::getBytesRecv, network::getBytesSent, clock);
        }

        pause.run();
        double cpuLoad = processor.getSystemCpuLoadBetweenTicks(cpuTicks);
        data.setCpuUsePercent(percent(Double.isFinite(cpuLoad) ? Math.clamp(cpuLoad, 0, 1) : 0, 1));
        IoSpeed diskSpeed = speed(disks, clock);
        data.setDiskReadSpeed(diskSpeed.read());
        data.setDiskWriteSpeed(diskSpeed.write());
        IoSpeed networkSpeed = speed(networks, clock);
        data.setNetRecvSpeed(networkSpeed.read());
        data.setNetSentSpeed(networkSpeed.write());
        return data;
    }

    /** 不可用容量或系统瞬时异常不应产生负用量、超出 100% 的占比或 NaN。 */
    private static double used(double total, double available) {
        return total - Math.clamp(available, 0, total);
    }

    private static double percent(double value, double total) {
        return total > 0 ? FloatUtil.round(value * 100 / total, 2) : 0;
    }

    /** 记录成功刷新的基线；设备无法读取时不参与本次速率统计。 */
    private static void sample(List<IoSample> samples, BooleanSupplier refresh,
                               LongSupplier read, LongSupplier write, LongSupplier clock) {
        if (refresh.getAsBoolean()) {
            long readBytes = read.getAsLong();
            long writeBytes = write.getAsLong();
            long nanos = clock.getAsLong();
            if (readBytes >= 0 && writeBytes >= 0) {
                samples.add(new IoSample(refresh, read, write, readBytes, writeBytes, nanos));
            }
        }
    }

    /** 逐设备按单调时钟的真实间隔计算速率，再汇总，避免总计数相减带来的热插拔尖峰。 */
    private static IoSpeed speed(List<IoSample> samples, LongSupplier clock) {
        double readSpeed = 0;
        double writeSpeed = 0;
        for (IoSample sample : samples) {
            if (!sample.refresh().getAsBoolean()) {
                continue;
            }
            long read = sample.read().getAsLong();
            long write = sample.write().getAsLong();
            long elapsed = clock.getAsLong() - sample.nanos();
            // 设备移除、统计重置或无有效时间间隔时，丢弃该设备的本次增量。
            if (elapsed <= 0 || read < sample.readBytes() || write < sample.writeBytes()) {
                continue;
            }
            double seconds = elapsed / 1_000_000_000d;
            readSpeed += (read - sample.readBytes()) / KB / seconds;
            writeSpeed += (write - sample.writeBytes()) / KB / seconds;
        }
        return new IoSpeed(FloatUtil.round(readSpeed, 2), FloatUtil.round(writeSpeed, 2));
    }

    private record IoSample(BooleanSupplier refresh, LongSupplier read, LongSupplier write,
                            long readBytes, long writeBytes, long nanos) {
    }

    private record IoSpeed(double read, double write) {
    }
}
