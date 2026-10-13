package com.friendlystation.platform.city;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

/**
 * 高德地图 Web 服务客户端（仅服务端使用）。
 *
 * <p>Web 服务 Key 通过环境变量 {@code AMAP_WEB_KEY} 注入，**绝不下发到前端**；
 * 未配置 Key 时所有方法返回空，调用方按“定位不可用”处理，不影响其他功能。</p>
 */
@Component
public class AmapClient {
    private static final Logger log = LoggerFactory.getLogger(AmapClient.class);

    private final String webKey;
    private final String baseUrl;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public AmapClient(
            @Value("${amap.web-key:}") String webKey,
            @Value("${amap.base-url:https://restapi.amap.com}") String baseUrl,
            @Value("${amap.timeout-seconds:5}") long timeoutSeconds,
            ObjectMapper objectMapper) {
        this.webKey = webKey;
        this.baseUrl = baseUrl;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(timeoutSeconds))
                .build();
    }

    /** 是否配置了可用的 Key。 */
    public boolean enabled() {
        return webKey != null && !webKey.isBlank();
    }

    /**
     * 逆地理编码：把经纬度解析成行政区信息。
     *
     * @return 成功时返回 {@code adcode} / {@code city} / {@code province} / {@code district}；未配置 Key 或调用失败返回空
     */
    public Optional<AmapRegeo> reverseGeocode(double lng, double lat) {
        if (!enabled()) {
            return Optional.empty();
        }
        String url = baseUrl + "/v3/geocode/regeo?location=" + lng + "," + lat
                + "&extensions=base&key=" + encode(webKey);
        return get(url).map(node -> {
            JsonNode component = node.path("regeocode").path("addressComponent");
            // 高德对直辖市返回的 city 为空数组，此时用 province 兜底（例如「北京市」）。
            String city = text(component.path("city"));
            if (city.isBlank()) {
                city = text(component.path("province"));
            }
            return new AmapRegeo(
                    text(component.path("adcode")),
                    city,
                    text(component.path("province")),
                    text(component.path("district")));
        });
    }

    /** 逆地理编码结果。 */
    public record AmapRegeo(String adcode, String city, String province, String district) {
    }

    private Optional<JsonNode> get(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(6))
                    .header("User-Agent", "friendly-station-platform/1.0")
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                log.warn("高德接口返回非 200：{}", response.statusCode());
                return Optional.empty();
            }
            JsonNode root = objectMapper.readTree(response.body());
            if (!"1".equals(root.path("status").asText())) {
                log.warn("高德接口返回失败：info={} infocode={}", root.path("info").asText(), root.path("infocode").asText());
                return Optional.empty();
            }
            return Optional.of(root);
        } catch (Exception exception) {
            // 外部服务不可用不应影响主流程，这里只记录日志并让调用方走兜底逻辑。
            log.warn("调用高德接口失败：{}", exception.getMessage());
            return Optional.empty();
        }
    }

    private static String text(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isArray()) {
            return "";
        }
        return node.asText("");
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
