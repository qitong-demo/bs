package com.friendlystation.platform.city;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 内存演示模式（非 mysql profile）的城市种子数据。
 *
 * <p>与驿站共用 <code>classpath:db/stations.json</code>：文件里的 <code>cities</code> 数组是城市清单，
 * <code>stations</code> 数组按 <code>cityCode</code> 归属到城市，驿站数量在这里统计出来。</p>
 */
@Component
@Profile("!mysql")
public class MockCityData {
    private static final String RESOURCE = "db/stations.json";

    private final List<CityDto> cities;

    public MockCityData() {
        this(new ObjectMapper());
    }

    public MockCityData(ObjectMapper objectMapper) {
        ClassPathResource resource = new ClassPathResource(RESOURCE);
        if (!resource.exists()) {
            throw new IllegalStateException("缺少城市与驿站数据文件 " + RESOURCE
                    + "，请先执行 node scripts/generate-station-data.mjs 生成后再启动。");
        }
        List<CityDto> parsed = new ArrayList<>();
        try (InputStream input = resource.getInputStream()) {
            JsonNode root = objectMapper.readTree(input);
            Map<String, Long> stationCounts = new LinkedHashMap<>();
            for (JsonNode station : root.path("stations")) {
                String code = station.path("cityCode").asText("");
                if (!code.isBlank()) {
                    stationCounts.merge(code, 1L, Long::sum);
                }
            }
            for (JsonNode node : root.path("cities")) {
                String code = node.path("cityCode").asText("");
                if (code.isBlank()) {
                    continue;
                }
                parsed.add(new CityDto(
                        code,
                        node.path("cityName").asText(""),
                        node.path("province").asText(""),
                        node.path("centerLng").isNumber() ? node.path("centerLng").asDouble() : null,
                        node.path("centerLat").isNumber() ? node.path("centerLat").asDouble() : null,
                        stationCounts.getOrDefault(code, 0L)));
            }
        } catch (Exception exception) {
            throw new IllegalStateException("解析城市数据失败：" + RESOURCE, exception);
        }
        this.cities = List.copyOf(parsed);
    }

    /** 城市列表（不可变），顺序与数据文件一致。 */
    public List<CityDto> cities() {
        return cities;
    }
}
