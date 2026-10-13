package com.friendlystation.platform.content;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.friendlystation.platform.common.BusinessException;
import com.friendlystation.platform.common.PageResult;
import com.friendlystation.platform.persistence.entity.News;
import com.friendlystation.platform.persistence.mapper.NewsMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** mysql Profile 下的资讯实现。 */
@Service
@Profile("mysql")
public class MysqlNewsService implements NewsService {
    private final NewsMapper newsMapper;

    public MysqlNewsService(NewsMapper newsMapper) {
        this.newsMapper = newsMapper;
    }

    @Override
    public PageResult<Map<String, Object>> publicPage(long current, long size, String category, String keyword) {
        return publicPage(current, size, category, keyword, null, null, null);
    }

    @Override
    public PageResult<Map<String, Object>> publicPage(long current, long size, String category, String keyword,
                                                      String cityCode, String sourceType, String platform) {
        return doPage(current, size, category, keyword, "PUBLISHED", cityCode, sourceType, platform);
    }

    @Override
    public PageResult<Map<String, Object>> adminPage(long current, long size, String category, String keyword, String status) {
        return doPage(current, size, category, keyword, status, null, null, null);
    }

    @Override
    public Map<String, Object> detail(Long id) {
        return toMap(require(id));
    }

    @Override
    @Transactional
    public Map<String, Object> create(Map<String, Object> payload) {
        Map<String, Object> data = payload == null ? Map.of() : payload;
        String title = requireTitle(data.get("title"));
        News news = new News();
        news.setTitle(title);
        news.setCategory(ContentSupport.textOrDefault(data.get("category"), "政策"));
        news.setContent(ContentSupport.trimText(data.get("content")));
        news.setViewCount(0);
        news.setStatus(ContentSupport.textOrDefault(data.get("status"), "PUBLISHED"));
        newsMapper.insert(news);
        return toMap(require(news.getId()));
    }

    @Override
    @Transactional
    public Map<String, Object> update(Long id, Map<String, Object> payload) {
        News news = require(id);
        Map<String, Object> data = payload == null ? Map.of() : payload;
        if (data.containsKey("title")) {
            news.setTitle(requireTitle(data.get("title")));
        }
        if (data.containsKey("category")) {
            news.setCategory(ContentSupport.textOrDefault(data.get("category"), "政策"));
        }
        if (data.containsKey("content")) {
            news.setContent(ContentSupport.trimText(data.get("content")));
        }
        if (data.containsKey("status")) {
            news.setStatus(ContentSupport.textOrDefault(data.get("status"), "PUBLISHED"));
        }
        news.setUpdateTime(LocalDateTime.now());
        newsMapper.updateById(news);
        return toMap(require(id));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        require(id);
        newsMapper.deleteById(id);
    }

    /**
     * 分页查询。分城市展示规则：
     * <ul>
     *   <li><b>不限城市</b>（city_code IS NULL）的内容在所有城市都可见；</li>
     *   <li><b>城市相关</b>的内容只在该城市可见，且**排到最前面**；</li>
     *   <li>其他城市的内容不出现。</li>
     * </ul>
     * 排序用「是否属于当前城市」优先，其次按发布日期倒序。
     */
    private PageResult<Map<String, Object>> doPage(long current, long size, String category, String keyword,
                                                   String status, String cityCode, String sourceType, String platform) {
        long safeCurrent = Math.max(current, 1);
        long safeSize = Math.min(Math.max(size, 1), 100);
        LambdaQueryWrapper<News> query = new LambdaQueryWrapper<>();
        if (!ContentSupport.isBlank(status)) {
            query.eq(News::getStatus, status.trim());
        }
        if (!ContentSupport.isBlank(category)) {
            query.eq(News::getCategory, category.trim());
        }
        if (!ContentSupport.isBlank(sourceType)) {
            query.eq(News::getSourceType, sourceType.trim().toUpperCase());
        }
        // 按平台筛选：骑手可以只看美团、京东这类自己关心的平台动态，
        // 不区分这条是平台自己发的、还是官方媒体报道该平台。
        if (!ContentSupport.isBlank(platform)) {
            query.eq(News::getPlatform, platform.trim());
        }
        // 城市过滤：本城市 + 不限城市。没传城市时不加限制（管理端与旧客户端行为不变）。
        if (!ContentSupport.isBlank(cityCode)) {
            String code = cityCode.trim();
            query.and(wrapper -> wrapper.isNull(News::getCityCode).or().eq(News::getCityCode, code));
        }
        if (!ContentSupport.isBlank(keyword)) {
            String normalizedKeyword = keyword.trim();
            query.and(wrapper -> wrapper
                    .like(News::getTitle, normalizedKeyword)
                    .or()
                    .like(News::getContent, normalizedKeyword)
                    .or()
                    .like(News::getPublisher, normalizedKeyword));
        }
        // 本城市的内容排前面：用「city_code 是否不等于当前城市」升序实现（false 在前）。
        // 紧接着按来源发布日期倒序，最后用 id 兜底保证顺序稳定。
        if (!ContentSupport.isBlank(cityCode)) {
            String code = cityCode.trim();
            query.last("ORDER BY (city_code IS NULL OR city_code <> '" + code.replace("'", "") + "') ASC, "
                    + "published_at IS NULL ASC, published_at DESC, id DESC");
        } else {
            query.orderByDesc(News::getPublishedAt).orderByDesc(News::getId);
        }
        Page<News> result = newsMapper.selectPage(new Page<>(safeCurrent, safeSize), query);
        List<Map<String, Object>> records = result.getRecords().stream().map(this::toMap).toList();
        return new PageResult<>(result.getCurrent(), result.getSize(), result.getTotal(), records);
    }

    private News require(Long id) {
        if (id == null) {
            throw new BusinessException("资讯不存在");
        }
        News news = newsMapper.selectById(id);
        if (news == null) {
            throw new BusinessException("资讯不存在");
        }
        return news;
    }

    private static String requireTitle(Object value) {
        String title = ContentSupport.trimText(value);
        if (title.isEmpty()) {
            throw new BusinessException("资讯标题不能为空");
        }
        return title;
    }

    /** 转换为对外固定字段结构。 */
    private Map<String, Object> toMap(News news) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", news.getId());
        out.put("title", news.getTitle());
        out.put("category", news.getCategory());
        out.put("content", news.getContent() == null ? "" : news.getContent());
        out.put("viewCount", news.getViewCount() == null ? 0 : news.getViewCount());
        out.put("status", news.getStatus());
        out.put("statusLabel", ContentSupport.statusLabel(news.getStatus()));
        out.put("date", ContentSupport.formatDate(news.getCreateTime()));
        out.put("createTime", ContentSupport.formatDateTime(news.getCreateTime()));
        // 来源与城市信息：来源类型区分官方/平台，城市为空表示不限城市
        out.put("sourceType", news.getSourceType() == null ? "OFFICIAL" : news.getSourceType());
        out.put("sourceTypeLabel", "PLATFORM".equalsIgnoreCase(news.getSourceType()) ? "平台" : "官方");
        out.put("publisher", news.getPublisher() == null ? "" : news.getPublisher());
        out.put("platform", news.getPlatform() == null ? "" : news.getPlatform());
        out.put("cityCode", news.getCityCode() == null ? "" : news.getCityCode());
        out.put("national", news.getCityCode() == null || news.getCityCode().isBlank());
        out.put("cityLabel", news.getCityCode() == null || news.getCityCode().isBlank() ? "全国" : "");
        out.put("publishedAt", news.getPublishedAt() == null ? "" : news.getPublishedAt().toString());
        out.put("sourceUrl", news.getSourceUrl() == null ? "" : news.getSourceUrl());
        out.put("source", "news");
        return out;
    }
}
