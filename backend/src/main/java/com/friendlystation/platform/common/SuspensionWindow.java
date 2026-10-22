package com.friendlystation.platform.common;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 驿站暂停服务窗口与预约时段的冲突判断。
 *
 * <p>预约只有“日期 + 时段”，暂停是一个时间区间。只要两者有交集就拒绝新预约：
 * 例如暂停 10:00-14:00，则 09:00-11:00、13:00-15:00 都会被拒。</p>
 */
public final class SuspensionWindow {
    private SuspensionWindow() {
    }

    public static boolean overlaps(LocalDateTime suspendedFrom, LocalDateTime suspendedTo,
                                   LocalDate appointDate, String slot) {
        if (suspendedFrom == null || suspendedTo == null || appointDate == null) {
            return false;
        }
        LocalTime[] bounds = BusinessHours.slotBounds(slot);
        if (bounds == null) {
            return false;
        }
        LocalDateTime start = LocalDateTime.of(appointDate, bounds[0]);
        LocalDateTime end = LocalDateTime.of(appointDate, bounds[1]);
        return start.isBefore(suspendedTo) && end.isAfter(suspendedFrom);
    }
}
