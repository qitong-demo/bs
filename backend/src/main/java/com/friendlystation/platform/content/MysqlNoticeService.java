package com.friendlystation.platform.content;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.friendlystation.platform.common.BusinessException;
import com.friendlystation.platform.common.PageResult;
import com.friendlystation.platform.persistence.entity.Notice;
import com.friendlystation.platform.persistence.mapper.NoticeMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** mysql Profile 下的公告实现。 */
@Service
@Profile("mysql")
public class MysqlNoticeService implements NoticeService {
    private final NoticeMapper noticeMapper;

    public MysqlNoticeService(NoticeMapper noticeMapper) {
        this.noticeMapper = noticeMapper;
    }

    @Override
    public PageResult<Map<String, Object>> publicPage(long current, long size, String noticeType, String keyword) {
        return doPage(current, size, noticeType, keyword, "PUBLISHED");
    }

    @Override
    public PageResult<Map<String, Object>> adminPage(long current, long size, String noticeType, String keyword, String status) {
        return doPage(current, size, noticeType, keyword, status);
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
        Notice notice = new Notice();
        notice.setTitle(title);
        notice.setNoticeType(ContentSupport.textOrDefault(data.get("noticeType"), "通知"));
        notice.setTargetType(ContentSupport.textOrDefault(data.get("targetType"), "ALL"));
        notice.setContent(ContentSupport.trimText(data.get("content")));
        notice.setStatus(ContentSupport.textOrDefault(data.get("status"), "PUBLISHED"));
        noticeMapper.insert(notice);
        return toMap(require(notice.getId()));
    }

    @Override
    @Transactional
    public Map<String, Object> update(Long id, Map<String, Object> payload) {
        Notice notice = require(id);
        Map<String, Object> data = payload == null ? Map.of() : payload;
        if (data.containsKey("title")) {
            notice.setTitle(requireTitle(data.get("title")));
        }
        if (data.containsKey("noticeType")) {
            notice.setNoticeType(ContentSupport.textOrDefault(data.get("noticeType"), "通知"));
        }
        if (data.containsKey("targetType")) {
            notice.setTargetType(ContentSupport.textOrDefault(data.get("targetType"), "ALL"));
        }
        if (data.containsKey("content")) {
            notice.setContent(ContentSupport.trimText(data.get("content")));
        }
        if (data.containsKey("status")) {
            notice.setStatus(ContentSupport.textOrDefault(data.get("status"), "PUBLISHED"));
        }
        noticeMapper.updateById(notice);
        return toMap(require(id));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        require(id);
        noticeMapper.deleteById(id);
    }

    private PageResult<Map<String, Object>> doPage(long current, long size, String noticeType, String keyword, String status) {
        long safeCurrent = Math.max(current, 1);
        long safeSize = Math.min(Math.max(size, 1), 100);
        LambdaQueryWrapper<Notice> query = new LambdaQueryWrapper<Notice>().orderByDesc(Notice::getCreateTime).orderByDesc(Notice::getId);
        if (!ContentSupport.isBlank(status)) {
            query.eq(Notice::getStatus, status.trim());
        }
        if (!ContentSupport.isBlank(noticeType)) {
            query.eq(Notice::getNoticeType, noticeType.trim());
        }
        if (!ContentSupport.isBlank(keyword)) {
            String normalizedKeyword = keyword.trim();
            query.and(wrapper -> wrapper
                    .like(Notice::getTitle, normalizedKeyword)
                    .or()
                    .like(Notice::getContent, normalizedKeyword));
        }
        Page<Notice> result = noticeMapper.selectPage(new Page<>(safeCurrent, safeSize), query);
        List<Map<String, Object>> records = result.getRecords().stream().map(this::toMap).toList();
        return new PageResult<>(result.getCurrent(), result.getSize(), result.getTotal(), records);
    }

    private Notice require(Long id) {
        if (id == null) {
            throw new BusinessException("公告不存在");
        }
        Notice notice = noticeMapper.selectById(id);
        if (notice == null) {
            throw new BusinessException("公告不存在");
        }
        return notice;
    }

    private static String requireTitle(Object value) {
        String title = ContentSupport.trimText(value);
        if (title.isEmpty()) {
            throw new BusinessException("公告标题不能为空");
        }
        return title;
    }

    /** 转换为对外固定字段结构。 */
    private Map<String, Object> toMap(Notice notice) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", notice.getId());
        out.put("title", notice.getTitle());
        out.put("noticeType", notice.getNoticeType());
        out.put("targetType", ContentSupport.isBlank(notice.getTargetType()) ? "ALL" : notice.getTargetType());
        out.put("content", notice.getContent() == null ? "" : notice.getContent());
        out.put("status", notice.getStatus());
        out.put("statusLabel", ContentSupport.statusLabel(notice.getStatus()));
        out.put("date", ContentSupport.formatDate(notice.getCreateTime()));
        out.put("createTime", ContentSupport.formatDateTime(notice.getCreateTime()));
        out.put("source", "notice");
        return out;
    }
}
