package br.ramdex.metrics;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.lang.management.OperatingSystemMXBean;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Coleta métricas da JVM e do sistema operacional usando apenas os MXBeans do JDK. */
public final class RuntimeMetrics {

    private static final long MB = 1024 * 1024;

    private RuntimeMetrics() {
    }

    public static Map<String, Object> heap() {
        MemoryUsage heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("usedMb", toMb(heap.getUsed()));
        m.put("committedMb", toMb(heap.getCommitted()));
        m.put("maxMb", heap.getMax() < 0 ? null : toMb(heap.getMax()));
        m.put("usedPercent", heap.getMax() <= 0 ? null : round(100.0 * heap.getUsed() / heap.getMax()));
        return m;
    }

    public static Map<String, Object> nonHeap() {
        MemoryUsage nonHeap = ManagementFactory.getMemoryMXBean().getNonHeapMemoryUsage();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("usedMb", toMb(nonHeap.getUsed()));
        m.put("committedMb", toMb(nonHeap.getCommitted()));
        return m;
    }

    public static List<Map<String, Object>> garbageCollectors() {
        List<Map<String, Object>> collectors = new ArrayList<>();
        for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", gc.getName());
            m.put("collections", gc.getCollectionCount());
            m.put("timeMs", gc.getCollectionTime());
            collectors.add(m);
        }
        return collectors;
    }

    /** Memória física e CPU da máquina (ou VM). Usa a extensão com.sun.management, presente no OpenJDK. */
    public static Map<String, Object> system() {
        OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("availableProcessors", os.getAvailableProcessors());
        m.put("loadAverage", round(os.getSystemLoadAverage()));
        if (os instanceof com.sun.management.OperatingSystemMXBean ext) {
            m.put("totalMemoryMb", toMb(ext.getTotalMemorySize()));
            m.put("freeMemoryMb", toMb(ext.getFreeMemorySize()));
            m.put("totalSwapMb", toMb(ext.getTotalSwapSpaceSize()));
            m.put("freeSwapMb", toMb(ext.getFreeSwapSpaceSize()));
            m.put("processCpuPercent", percent(ext.getProcessCpuLoad()));
            m.put("systemCpuPercent", percent(ext.getCpuLoad()));
        }
        return m;
    }

    public static long uptimeMs() {
        return ManagementFactory.getRuntimeMXBean().getUptime();
    }

    public static double usedHeapMb() {
        return toMb(ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed());
    }

    private static double toMb(long bytes) {
        return round((double) bytes / MB);
    }

    private static Double percent(double load) {
        return load < 0 ? null : round(load * 100);
    }

    private static double round(double value) {
        return Math.round(value * 100) / 100.0;
    }
}
