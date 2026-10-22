package com.friendlystation.platform.notification;

import com.friendlystation.platform.common.PageResult;

import java.util.List;
import java.util.Map;

/** 站内通知：业务状态变化后写给用户的消息。 */
public interface NotificationService {

    /** 写入一条通知。失败不应阻断业务，但调用方仍按同事务处理。 */
    void push(long userId, String type, String title, String content, String link,
              String bizType, Long bizId);

    PageResult<Map<String, Object>> page(long userId, long current, long size);

    long unreadCount(long userId);

    /** 标记单条已读；只能操作自己的通知。 */
    void markRead(long userId, long id);

    void markAllRead(long userId);

    /** 查询某用户的通知（内存实现给工作流复用，MySQL 实现不暴露）。 */
    List<Map<String, Object>> list(long userId);
}
