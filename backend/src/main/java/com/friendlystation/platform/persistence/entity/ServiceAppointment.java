package com.friendlystation.platform.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("service_appointment")
public class ServiceAppointment {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long stationId;
    private Long serviceId;
    /** 用户自填的预约事项：想提前联系驿站确认什么。 */
    private String serviceName;
    /** 联系电话，便于驿站回电确认是否可行。 */
    private String contactPhone;
    private LocalDate appointDate;
    private String appointTime;
    private String status;
    private String auditReason;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
