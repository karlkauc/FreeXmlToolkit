package org.fxt.freexmltoolkit.controls.shell.editor;

import java.util.ArrayList;
import java.util.List;

import org.fxt.freexmltoolkit.domain.statistics.DailyStatistics;
import org.fxt.freexmltoolkit.service.UsageTrackingServiceImpl;

/** UI-free data source for the welcome dashboard trend sparkline. */
public final class WelcomeTrend {

    private WelcomeTrend() {
    }

    /**
     * Total activity per day for the last {@code days} days, oldest first.
     * Returns one entry per day (0 for days with no recorded activity).
     */
    public static List<Integer> dailyTotals(int days) {
        List<Integer> totals = new ArrayList<>();
        try {
            // getDailyStats returns most-recent-first; reverse to oldest-first.
            List<DailyStatistics> stats = UsageTrackingServiceImpl.getInstance().getDailyStats(days);
            for (int i = stats.size() - 1; i >= 0; i--) {
                totals.add(stats.get(i).getTotalActivity());
            }
        } catch (Throwable ignored) {
            // service unavailable → empty trend
        }
        return totals;
    }

    /** Normalizes a series to [0,1] (max→1, min→0); an all-equal series maps to all-0. */
    public static List<Double> normalize(List<Integer> raw) {
        List<Double> out = new ArrayList<>();
        if (raw == null || raw.isEmpty()) {
            return out;
        }
        int max = raw.stream().mapToInt(Integer::intValue).max().orElse(0);
        int min = raw.stream().mapToInt(Integer::intValue).min().orElse(0);
        int span = max - min;
        for (int v : raw) {
            out.add(span == 0 ? 0.0 : (double) (v - min) / span);
        }
        return out;
    }
}
