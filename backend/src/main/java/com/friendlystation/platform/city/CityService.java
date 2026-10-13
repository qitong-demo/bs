package com.friendlystation.platform.city;

import java.util.List;

/**
 * 城市服务：平台是全国性的新就业群体友好驿站平台，驿站等业务数据按城市归属。
 */
public interface CityService {
    /**
     * 平台已覆盖的城市列表，按 sort_no 升序；每个城市带已收录的驿站数量。
     * 列表第一个元素即平台默认城市，前端在用户拒绝定位且无历史选择时使用。
     */
    List<CityDto> list();

    /**
     * 按经纬度找出距离最近的城市，用于浏览器定位后判断用户所在城市。
     *
     * @return 最近的城市；平台没有任何城市数据时返回 null
     */
    CityDto nearest(Double lng, Double lat);
}
