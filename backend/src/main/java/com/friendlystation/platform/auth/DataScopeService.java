package com.friendlystation.platform.auth;

import com.friendlystation.platform.station.StationDto;
import com.friendlystation.platform.station.StationService;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 数据范围解析。
 *
 * <p>平台管理员与超级管理员可访问全部数据；驿站管理员只访问自己所负责驿站范围内的数据。
 * 方法返回 {@code null} 表示「不做限制」，返回空集合表示「范围内没有可见数据」。</p>
 */
@Component
public class DataScopeService {
    private final StationService stationService;

    public DataScopeService(StationService stationService) {
        this.stationService = stationService;
    }

    /** 是否属于平台级角色（不受驿站范围限制）。 */
    public boolean isPlatformLevel(CurrentUser user) {
        return user == null || !"STATION_ADMIN".equals(user.role());
    }

    /** 当前账号负责的驿站。 */
    public List<StationDto> managedStations(CurrentUser user) {
        if (user == null || user.id() == null || isPlatformLevel(user)) {
            return List.of();
        }
        return stationService.listByManager(user.id());
    }

    /** 当前账号可见的驿站编号；平台级返回 null 表示不限制。 */
    public List<Long> stationIds(CurrentUser user) {
        if (isPlatformLevel(user)) {
            return null;
        }
        return managedStations(user).stream().map(StationDto::id).toList();
    }

    /** 当前账号可见的区域；平台级返回 null 表示不限制。 */
    public List<String> areas(CurrentUser user) {
        if (isPlatformLevel(user)) {
            return null;
        }
        return managedStations(user).stream()
                .map(StationDto::area)
                .filter(area -> area != null && !area.isBlank())
                .distinct()
                .toList();
    }
}
