package cn.bcd.lib.spring.monitor.client;

import org.junit.jupiter.api.Test;
import oshi.hardware.*;
import oshi.software.os.FileSystem;
import oshi.software.os.OSFileStore;
import oshi.software.os.OperatingSystem;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

class MonitorUtilTest {
    @Test
    void measuresActualElapsedTimeAndEnumeratesDevicesOnlyOnce() {
        Fixture fixture = new Fixture();
        SystemData data = fixture.collect();
        // 1.5 秒内读取 3 KiB、写入 6 KiB，不能直接当作每秒速率。
        assertEquals(2, data.diskReadSpeed);
        assertEquals(4, data.diskWriteSpeed);
        assertEquals(2, data.netRecvSpeed);
        assertEquals(4, data.netSentSpeed);
        assertEquals(50, data.cpuUsePercent);
        assertEquals(50, data.memoryUsePercent);
        assertEquals(50, data.diskUsePercent);
        assertEquals(1, fixture.diskEnumerations);
        assertEquals(1, fixture.networkEnumerations);
        assertEquals(2, fixture.disk.refreshes);
        assertEquals(2, fixture.network.refreshes);
    }

    @Test
    void includesDeviceRefreshTimeInEachSampleInterval() {
        Fixture fixture = new Fixture();
        fixture.refreshNanos = 500_000_000L;
        SystemData data = fixture.collect();
        assertEquals(1.5, data.diskReadSpeed); // 3 KiB / 2 秒
        assertEquals(1.2, data.netRecvSpeed); // 3 KiB / 2.5 秒
    }

    @Test
    void skipsFailedRefreshAndResetCounters() {
        Fixture fixture = new Fixture();
        fixture.disk.secondRead = 0;
        fixture.network.afterValid = false;
        SystemData data = fixture.collect();
        assertEquals(0, data.diskReadSpeed);
        assertEquals(0, data.diskWriteSpeed);
        assertEquals(0, data.netRecvSpeed);
        assertEquals(0, data.netSentSpeed);
    }

    @Test
    void skipsDevicesWithoutValidBaseline() {
        Fixture fixture = new Fixture();
        fixture.disk.beforeValid = false;
        fixture.network.beforeValid = false;
        SystemData data = fixture.collect();
        assertEquals(0, data.diskReadSpeed);
        assertEquals(0, data.netRecvSpeed);
        assertEquals(1, fixture.disk.refreshes);
        assertEquals(1, fixture.network.refreshes);
    }

    @Test
    void handlesMissingCapacityAndNonFiniteCpuLoad() {
        Fixture fixture = new Fixture();
        fixture.total = 0;
        fixture.available = 100;
        fixture.cpuLoad = Double.NaN;
        SystemData data = fixture.collect();
        assertEquals(0, data.cpuUsePercent);
        assertEquals(0, data.memoryMax);
        assertEquals(0, data.memoryUse);
        assertEquals(0, data.memoryUsePercent);
        assertEquals(0, data.diskMax);
        assertEquals(0, data.diskUse);
        assertEquals(0, data.diskUsePercent);
    }

    @Test
    void clampsInvalidAvailableCapacityAndSkipsNonPositiveIntervals() {
        Fixture fixture = new Fixture();
        fixture.available = -1;
        fixture.waitNanos = 0;
        SystemData data = fixture.collect();
        assertEquals(100, data.memoryUsePercent);
        assertEquals(100, data.diskUsePercent);
        assertEquals(0, data.diskReadSpeed);
        assertEquals(0, data.netRecvSpeed);
    }

    /** 严格替身：意外调用硬件接口时直接失败，不依赖本机设备和真实等待。 */
    private static <T> T stub(Class<T> type, Function<String, Object> call) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (proxy, method, args) -> call.apply(method.getName())));
    }

    private static class Fixture {
        final AtomicLong clock = new AtomicLong();
        boolean after;
        long waitNanos = 1_500_000_000L;
        long refreshNanos;
        long total = 1024L * 1024 * 1024;
        long available = total / 2;
        double cpuLoad = 0.5;
        int diskEnumerations;
        int networkEnumerations;
        final Device disk = new Device();
        final Device network = new Device();

        final CentralProcessor processor = stub(CentralProcessor.class, method -> switch (method) {
            case "getPhysicalProcessorCount" -> 4;
            case "getLogicalProcessorCount" -> 8;
            case "getSystemCpuLoadTicks" -> new long[8];
            case "getSystemCpuLoadBetweenTicks" -> cpuLoad;
            default -> throw new AssertionError(method);
        });
        final GlobalMemory memory = stub(GlobalMemory.class, method -> capacity(method));
        final OSFileStore store = stub(OSFileStore.class, method -> capacity(method));
        final FileSystem fileSystem = stub(FileSystem.class, method -> {
            assertEquals("getFileStores", method);
            return List.of(store);
        });
        final OperatingSystem os = stub(OperatingSystem.class, method -> {
            assertEquals("getFileSystem", method);
            return fileSystem;
        });
        final HardwareAbstractionLayer hardware = stub(HardwareAbstractionLayer.class, method -> switch (method) {
            case "getProcessor" -> processor;
            case "getMemory" -> memory;
            case "getDiskStores" -> {
                assertEquals(1, ++diskEnumerations);
                yield List.of(stub(HWDiskStore.class, disk::call));
            }
            case "getNetworkIFs" -> {
                assertEquals(1, ++networkEnumerations);
                yield List.of(stub(NetworkIF.class, network::call));
            }
            default -> throw new AssertionError(method);
        });

        private Object capacity(String method) {
            return switch (method) {
                case "getTotal", "getTotalSpace" -> total;
                case "getAvailable", "getUsableSpace" -> available;
                default -> throw new AssertionError(method);
            };
        }

        SystemData collect() {
            return MonitorUtil.collect(hardware, os, clock::get, () -> {
                after = true;
                clock.addAndGet(waitNanos);
            });
        }

        class Device {
            boolean beforeValid = true;
            boolean afterValid = true;
            long secondRead = 4096;
            long secondWrite = 8192;
            int refreshes;

            Object call(String method) {
                return switch (method) {
                    case "updateAttributes" -> {
                        refreshes++;
                        if (after) {
                            clock.addAndGet(refreshNanos);
                        }
                        yield after ? afterValid : beforeValid;
                    }
                    case "getReadBytes", "getBytesRecv" -> after ? secondRead : 1024L;
                    case "getWriteBytes", "getBytesSent" -> after ? secondWrite : 2048L;
                    default -> throw new AssertionError(method);
                };
            }
        }
    }
}
