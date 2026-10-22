package com.friendlystation.platform.station;

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
 * 内存演示模式（非 mysql profile）的驿站种子数据。
 *
 * <p>数据来自 <code>classpath:db/stations.json</code>，与 MySQL 初始化脚本
 * <code>db/stations.sql</code> 由同一个生成脚本产出，保证两种运行方式看到同样的驿站。</p>
 */
@Component
@Profile("!mysql")
public class MockStationData {
    private static final String RESOURCE = "db/stations.json";
    private static final String DEFAULT_TYPE = "未分类";

    /** 服务项目种子：id 由内存实现自行分配，stationId 与驿站保持一致。 */
    public record ServiceSeed(long id, long stationId, String serviceName, String serviceType) {
    }

    private final List<StationDto> stations;
    private final List<ServiceSeed> serviceSeeds;
    private final String datasetNote;

    /** 便于单元测试直接构造。 */
    public MockStationData() {
        this(new ObjectMapper());
    }

    public MockStationData(ObjectMapper objectMapper) {
        ClassPathResource resource = new ClassPathResource(RESOURCE);
        if (!resource.exists()) {
            throw new IllegalStateException("缺少驿站数据文件 " + RESOURCE
                    + "，请先执行 node scripts/generate-station-data.mjs 生成后再启动。");
        }
        List<StationDto> parsedStations = new ArrayList<>();
        List<ServiceSeed> parsedSeeds = new ArrayList<>();
        String note = "";
        long serviceId = 0;
        try (InputStream input = resource.getInputStream()) {
            JsonNode root = objectMapper.readTree(input);
            note = root.path("note").asText("");
            for (JsonNode node : root.path("stations")) {
                long id = node.path("id").asLong();
                List<String> serviceNames = new ArrayList<>();
                for (JsonNode service : node.path("serviceItems")) {
                    String name = service.path("name").asText("");
                    if (name.isBlank()) {
                        continue;
                    }
                    serviceNames.add(name);
                    parsedSeeds.add(new ServiceSeed(++serviceId, id, name, service.path("type").asText("GENERAL")));
                }
                parsedStations.add(new StationDto(
                        id,
                        node.path("cityCode").asText(""),
                        node.path("stationName").asText(""),
                        node.path("stationType").asText(DEFAULT_TYPE),
                        node.path("area").asText(""),
                        node.path("address").asText(""),
                        node.path("longitude").isNumber() ? node.path("longitude").asDouble() : null,
                        node.path("latitude").isNumber() ? node.path("latitude").asDouble() : null,
                        "",
                        node.path("openTime").asText(""),
                        "—",
                        serviceNames,
                        node.path("status").asText("OPEN"),
                        node.path("managerId").isNumber() ? node.path("managerId").asLong() : null,
                        new StationSource(
                                node.path("sourceName").asText(""),
                                node.path("sourceUrl").asText(""),
                                node.path("sourcePublishedAt").asText(""),
                                node.path("verifiedAt").asText(""),
                                node.path("dataStatus").asText("UNKNOWN"),
                                node.path("dataNote").asText(""),
                                node.path("geocodeSource").asText(""),
                                node.path("geocodeAt").asText(""),
                                node.path("businessSource").asText(""),
                                node.path("businessSyncedAt").asText("")),
                        null,
                        null,
                        "",
                        ""));
            }
        } catch (Exception exception) {
            throw new IllegalStateException("解析驿站数据文件失败：" + RESOURCE, exception);
        }
        this.stations = List.copyOf(parsedStations);
        this.serviceSeeds = List.copyOf(parsedSeeds);
        this.datasetNote = note;
    }

    /** 种子驿站（不可变）。 */
    public List<StationDto> stations() {
        return stations;
    }

    /** 服务项目种子（不可变）。 */
    public List<ServiceSeed> serviceSeeds() {
        return serviceSeeds;
    }

    /** 数据文件自带的口径说明。 */
    public String datasetNote() {
        return datasetNote;
    }

    /** 按驿站编号索引服务名称，便于列表与详情复用。 */
    public Map<Long, List<String>> serviceNamesByStation() {
        Map<Long, List<String>> grouped = new LinkedHashMap<>();
        for (ServiceSeed seed : serviceSeeds) {
            grouped.computeIfAbsent(seed.stationId(), key -> new ArrayList<>()).add(seed.serviceName());
        }
        return grouped;
    }
}
