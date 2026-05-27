package com.mpole.hana.digitaltwin.audit.dto;


import java.util.List;

public record AuditStatisticsResponse(
        Summary summary,
        List<DailyPoint> daily,
        List<MenuCount> topMenus,
        List<HourCount> usageByHour
) {
    public record Summary(
            long loginSuccessTotal,
            long loginFailTotal,
            long logoutTotal,
            long usageTotal,
            long loginSuccessToday,
            long usageToday,
            long loginFailToday
    ) {
    }

    public record DailyPoint(String day, long logins, long visits) {
    }
    public record MenuCount(String label, long count) {
    }
    public record HourCount(int hour, long count) {
    }
}
