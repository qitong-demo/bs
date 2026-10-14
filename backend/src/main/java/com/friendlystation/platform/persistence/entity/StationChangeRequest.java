package com.friendlystation.platform.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 驿站信息变更申请。
 *
 * <p>驿站最清楚自己门口在哪，但坐标是权威数据（现有 1247/1344 条由高德 POI 派生并经三重校验），
 * 不能自改，因此**位置变更走审批**；营业时间与联系电话按需求可直接改（需邮箱验证）。</p>
 */
@Data
@TableName("station_change_request")
public class StationChangeRequest {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long stationId;
    private Long applicantId;
    /** LOCATION 位置 / BUSINESS_HOURS 营业时间 / PHONE 联系电话 / ADDRESS 地址 / OTHER 其他 */
    private String changeType;
    /** 变更前的值，留痕用。 */
    private String oldValue;
    private String newValue;
    /** 申请理由，必填——审批人需要据此判断。 */
    private String reason;
    /** PENDING 待审批 / APPROVED 已通过 / REJECTED 已驳回 / CANCELLED 已撤回 */
    private String status;
    private Long reviewerId;
    private String reviewNote;
    private LocalDateTime reviewTime;
    private LocalDateTime createTime;
}
