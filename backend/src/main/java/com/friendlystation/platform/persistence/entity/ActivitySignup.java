package com.friendlystation.platform.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("activity_signup")
public class ActivitySignup {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long activityId;
    private Long userId;
    /** 报名人姓名，承办方据此联系。 */
    private String contactName;
    private String phone;
    /** 备注：如所在站点、可参加的时间等。 */
    private String remark;
    private String status;
    /** 承办方的审核反馈，会展示给报名人。 */
    private String reviewNote;
    private Long reviewerId;
    private LocalDateTime reviewTime;
    private LocalDateTime createTime;
}
