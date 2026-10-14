package com.friendlystation.platform.content;

import com.friendlystation.platform.common.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 半自动采集：给定一个网页地址，抓取标题与摘要供管理员**核对后**再保存。
 *
 * <p>这是"半自动"而不是"自动"：只做机械提取，不做任何内容加工或改写。
 * 返回结果里带 {@code note} 明确告诉管理员这是机器抓的、需要核对——
 * 采集来的信息最终由人负责，程序不替人下判断。</p>
 *
 * <p>几处必须守住的边界：</p>
 * <ul>
 *   <li><b>SSRF 防护</b>：只允许 http/https，且解析出的 IP 不能是内网、回环、
 *       链路本地或云元数据地址。否则管理员（或被盗的管理员账号）可以借这个接口
 *       探测服务器内网。</li>
 *   <li><b>超时与体积上限</b>：避免被一个慢站点或超大页面拖死。</li>
 *   <li><b>不跟随到非目标协议</b>：重定向后重新校验地址。</li>
 * </ul>
 */
@Service
public class ActivitySourceFetcher {
    private static final Logger log = LoggerFactory.getLogger(ActivitySourceFetcher.class);

    /** 单页最多读 512KB——只要 head 里的信息，读全文既慢又没必要。 */
    private static final int MAX_BYTES = 512 * 1024;
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(6);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
    /** 最多跟 3 次重定向，每次都要重新做内网校验。 */
    private static final int MAX_REDIRECTS = 3;

    private static final Pattern TITLE = Pattern.compile("<title[^>]*>(.*?)</title>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern META_DESCRIPTION = Pattern.compile(
            "<meta[^>]+(?:name|property)\\s*=\\s*[\"'](?:description|og:description|twitter:description)[\"'][^>]*>",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern CONTENT_ATTR = Pattern.compile(
            "content\\s*=\\s*[\"'](.*?)[\"']", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern OG_SITE_NAME = Pattern.compile(
            "<meta[^>]+property\\s*=\\s*[\"']og:site_name[\"'][^>]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern CHARSET_IN_META = Pattern.compile(
            "<meta[^>]+charset\\s*=\\s*[\"']?([A-Za-z0-9_\\-]+)", Pattern.CASE_INSENSITIVE);

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    /**
     * 抓取页面标题与摘要。
     *
     * @return title / summary / siteName / url / note；抓不到的字段返回空串
     */
    public Map<String, Object> fetch(String rawUrl) {
        URI uri = validate(rawUrl);
        FetchResult result = fetchFollowing(uri, 0);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("url", result.uri().toString());
        out.put("title", cleanTitle(result.html()));
        out.put("summary", extractMeta(result.html(), META_DESCRIPTION));
        out.put("siteName", extractMeta(result.html(), OG_SITE_NAME));
        out.put("note", "以上标题与摘要由程序从网页自动抓取，未经人工核实。"
                + "请打开原文确认无误后再保存；主办方、活动日期、分类等字段需要你手工填写。");
        out.put("dataStatus", "网页自动抓取，待人工核对");
        return out;
    }

    // ------------------------------------------------------------ 抓取

    private record FetchResult(URI uri, String html) {
    }

    private FetchResult fetchFollowing(URI uri, int depth) {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(REQUEST_TIMEOUT)
                .header("User-Agent", "Mozilla/5.0 (compatible; FriendlyStationBot/1.0)")
                .header("Accept", "text/html,application/xhtml+xml")
                .GET()
                .build();
        try {
            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            int status = response.statusCode();
            // 3xx 手动跟随，每次重新校验目标地址，避免被重定向到内网
            if (status >= 300 && status < 400) {
                String location = response.headers().firstValue("location").orElse("");
                if (location.isBlank() || depth >= MAX_REDIRECTS) {
                    throw new BusinessException("该地址重定向次数过多或缺少目标地址");
                }
                return fetchFollowing(validate(uri.resolve(location).toString()), depth + 1);
            }
            if (status != 200) {
                throw new BusinessException("无法打开该地址（HTTP " + status + "），请确认链接可公开访问");
            }
            String contentType = response.headers().firstValue("content-type").orElse("");
            if (!contentType.isEmpty() && !contentType.toLowerCase().contains("html")) {
                throw new BusinessException("该地址不是网页（" + contentType + "），无法提取标题");
            }
            byte[] body = readLimited(response.body());
            return new FetchResult(uri, decode(body, contentType));
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            log.info("采集抓取失败 url={} 原因={}", uri, exception.getMessage());
            throw new BusinessException("抓取失败：" + exception.getClass().getSimpleName()
                    + "。请确认链接可公开访问，或改为手工填写");
        }
    }

    private static byte[] readLimited(InputStream stream) throws Exception {
        try (InputStream in = stream; ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
            byte[] chunk = new byte[8192];
            int total = 0;
            int read;
            while ((read = in.read(chunk)) > 0) {
                total += read;
                if (total > MAX_BYTES) {
                    // 只要 head 里的信息，读够就停，不必等整个页面
                    buffer.write(chunk, 0, read - (total - MAX_BYTES));
                    break;
                }
                buffer.write(chunk, 0, read);
            }
            return buffer.toByteArray();
        }
    }

    /**
     * 解码。
     *
     * <p>中文政府网站不少仍用 GBK/GB2312，不能一律按 UTF-8 解——
     * 那会把标题解成乱码，而乱码一旦被管理员误存就污染了数据。
     * 优先级：HTTP 头的 charset → 页面 meta 里的 charset → UTF-8。</p>
     */
    private static String decode(byte[] body, String contentType) {
        Charset charset = charsetFromContentType(contentType);
        if (charset == null) {
            // 先用 ASCII 安全地看一眼 meta，再定编码
            String head = new String(body, 0, Math.min(body.length, 4096), StandardCharsets.ISO_8859_1);
            Matcher matcher = CHARSET_IN_META.matcher(head);
            if (matcher.find()) {
                charset = toCharset(matcher.group(1));
            }
        }
        if (charset == null) {
            charset = StandardCharsets.UTF_8;
        }
        return new String(body, charset);
    }

    private static Charset charsetFromContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return null;
        }
        Matcher matcher = Pattern.compile("charset\\s*=\\s*[\"']?([A-Za-z0-9_\\-]+)", Pattern.CASE_INSENSITIVE)
                .matcher(contentType);
        return matcher.find() ? toCharset(matcher.group(1)) : null;
    }

    private static Charset toCharset(String name) {
        try {
            return Charset.forName(name.trim());
        } catch (Exception exception) {
            return null;
        }
    }

    // ------------------------------------------------------------ 抽取

    /** 标题去掉常见站点后缀，并压缩空白。 */
    private static String cleanTitle(String html) {
        Matcher matcher = TITLE.matcher(html);
        if (!matcher.find()) {
            return "";
        }
        String title = unescape(matcher.group(1)).replaceAll("\\s+", " ").trim();
        // 政府网站标题常带 "_中国政府网" / "- 杭州市人民政府" 这类后缀，去掉更干净
        title = title.replaceAll("[_\\-—|]\\s*[^_\\-—|]{0,20}(中国政府网|人民政府|总工会|人力资源和社会保障局|日报|新闻网)\\s*$", "").trim();
        // 去掉结尾孤零零的分隔符（部分站点标题以 " - " 收尾）
        title = title.replaceAll("[\\s_\\-—|]+$", "").trim();
        return title;
    }

    private static String extractMeta(String html, Pattern blockPattern) {
        Matcher block = blockPattern.matcher(html);
        if (!block.find()) {
            return "";
        }
        Matcher content = CONTENT_ATTR.matcher(block.group(0));
        return content.find() ? unescape(content.group(1)).replaceAll("\\s+", " ").trim() : "";
    }

    /** 实体里既有命名的也有数字的（中工网标题就用 &#32; 表示空格），两种都要处理。 */
    private static final Pattern NUMERIC_ENTITY = Pattern.compile("&#(x?)([0-9A-Fa-f]+);");

    private static String unescape(String text) {
        String out = text.replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&apos;", "'")
                .replace("&ldquo;", "“")
                .replace("&rdquo;", "”")
                .replace("&middot;", "·")
                .replace("&mdash;", "—")
                .replace("&ndash;", "–");
        // 数字实体：&#32; 与 &#x20; 两种写法
        Matcher matcher = NUMERIC_ENTITY.matcher(out);
        StringBuilder builder = new StringBuilder();
        while (matcher.find()) {
            int codePoint;
            try {
                codePoint = matcher.group(1).isEmpty()
                        ? Integer.parseInt(matcher.group(2))
                        : Integer.parseInt(matcher.group(2), 16);
            } catch (NumberFormatException exception) {
                continue;
            }
            matcher.appendReplacement(builder,
                    Matcher.quoteReplacement(new String(Character.toChars(codePoint))));
        }
        matcher.appendTail(builder);
        return builder.toString();
    }

    // ------------------------------------------------------------ 校验

    /**
     * 校验并解析地址。这一层是防 SSRF 的关键：**在建立连接之前**就要确认目标不是内网。
     */
    static URI validate(String rawUrl) {
        String text = rawUrl == null ? "" : rawUrl.trim();
        if (text.isEmpty()) {
            throw new BusinessException("请填写要采集的网页地址");
        }
        URI uri;
        try {
            uri = URI.create(text);
        } catch (Exception exception) {
            throw new BusinessException("网页地址格式不正确");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw new BusinessException("只支持 http 或 https 地址");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new BusinessException("网页地址缺少主机名");
        }
        assertPublicHost(host);
        return uri;
    }

    /** 拒绝内网、回环、链路本地与云元数据地址。 */
    private static void assertPublicHost(String host) {
        try {
            for (InetAddress address : InetAddress.getAllByName(host)) {
                if (address.isLoopbackAddress() || address.isSiteLocalAddress()
                        || address.isLinkLocalAddress() || address.isAnyLocalAddress()
                        || address.isMulticastAddress()) {
                    throw new BusinessException("该地址指向内网，出于安全考虑不允许采集");
                }
                byte[] bytes = address.getAddress();
                // 169.254.169.254 等云元数据地址，部分实现不算 link-local，单独拦一次
                if (bytes.length == 4 && (bytes[0] & 0xFF) == 169 && (bytes[1] & 0xFF) == 254) {
                    throw new BusinessException("该地址指向内网，出于安全考虑不允许采集");
                }
            }
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException("无法解析该地址的主机名");
        }
    }
}
