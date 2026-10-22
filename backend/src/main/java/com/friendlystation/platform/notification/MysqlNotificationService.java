package com.friendlystation.platform.notification;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.friendlystation.platform.common.BusinessException;
import com.friendlystation.platform.common.FormatUtils;
import com.friendlystation.platform.common.PageResult;
import com.friendlystation.platform.persistence.entity.Notification;
import com.friendlystation.platform.persistence.mapper.NotificationMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** MySQL 版站内通知。 */
@Service
@Profile("mysql")
public class MysqlNotificationService implements NotificationService {
    private final NotificationMapper notificationMapper;

    public MysqlNotificationService(NotificationMapper notificationMapper) {
        this.notificationMapper = notificationMapper;
    }

    @Override
    public void push(long userId, String type, String title, String content, String link,
                     String bizType, Long bizId) {
        if (userId <= 0 || title == null || title.isBlank()) {
            return;
        }
        Notification item = new Notification();
        item.setUserId(userId);
        item.setType(type == null ? "SYSTEM" : type);
        item.setTitle(title.trim());
        item.setContent(content == null ? "" : content.trim());
        item.setLink(link == null ? "" : link.trim());
        item.setReadFlag(0);
        item.setBizType(bizType);
        item.setBizId(bizId);
        item.setCreateTime(LocalDateTime.now());
        notificationMapper.insert(item);
    }

    @Override
    public PageResult<Map<String, Object>> page(long userId, long current, long size) {
        long safeCurrent = Math.max(current, 1);
        long safeSize = Math.min(Math.max(size, 1), 100);
        Page<Notification> result = notificationMapper.selectPage(new Page<>(safeCurrent, safeSize),
                new LambdaQueryWrapper<Notification>()
                        .eq(Notification::getUserId, userId)
                        .orderByDesc(Notification::getCreateTime)
                        .orderByDesc(Notification::getId));
        List<Map<String, Object>> records = result.getRecords().stream().map(this::toMap).toList();
        return new PageResult<>(result.getCurrent(), result.getSize(), result.getTotal(), records);
    }

    @Override
    public long unreadCount(long userId) {
        Long count = notificationMapper.selectCount(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .eq(Notification::getReadFlag, 0));
        return count == null ? 0L : count;
    }

    @Override
    public void markRead(long userId, long id) {
        int updated = notificationMapper.update(null, new LambdaUpdateWrapper<Notification>()
                .eq(Notification::getId, id)
                .eq(Notification::getUserId, userId)
                .eq(Notification::getReadFlag, 0)
                .set(Notification::getReadFlag, 1));
        if (updated == 0 && notificationMapper.selectCount(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getId, id).eq(Notification::getUserId, userId)) == 0) {
            throw new BusinessException("通知不存在");
        }
    }

    @Override
    public void markAllRead(long userId) {
        notificationMapper.update(null, new LambdaUpdateWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .eq(Notification::getReadFlag, 0)
                .set(Notification::getReadFlag, 1));
    }

    @Override
    public List<Map<String, Object>> list(long userId) {
        return notificationMapper.selectList(new LambdaQueryWrapper<Notification>()
                        .eq(Notification::getUserId, userId)
                        .orderByDesc(Notification::getCreateTime)
                        .orderByDesc(Notification::getId)
                        .last("LIMIT 100"))
                .stream().map(this::toMap).toList();
    }

    private Map<String, Object> toMap(Notification item) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", item.getId());
        out.put("type", item.getType());
        out.put("title", item.getTitle());
        out.put("content", item.getContent() == null ? "" : item.getContent());
        out.put("link", item.getLink() == null ? "" : item.getLink());
        out.put("read", item.getReadFlag() != null && item.getReadFlag() == 1);
        out.put("bizType", item.getBizType() == null ? "" : item.getBizType());
        out.put("bizId", item.getBizId() == null ? 0L : item.getBizId());
        out.put("createTime", FormatUtils.dateTime(item.getCreateTime()));
        return out;
    }
}
