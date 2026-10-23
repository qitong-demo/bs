package com.friendlystation.platform.station;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.friendlystation.platform.auth.CredentialVersionService;
import com.friendlystation.platform.common.BusinessException;
import com.friendlystation.platform.common.FormatUtils;
import com.friendlystation.platform.common.PageResult;
import com.friendlystation.platform.persistence.entity.StationApplication;
import com.friendlystation.platform.persistence.entity.SysRole;
import com.friendlystation.platform.persistence.entity.SysUser;
import com.friendlystation.platform.persistence.entity.SysUserRole;
import com.friendlystation.platform.persistence.mapper.StationApplicationMapper;
import com.friendlystation.platform.persistence.mapper.SysRoleMapper;
import com.friendlystation.platform.persistence.mapper.SysUserMapper;
import com.friendlystation.platform.persistence.mapper.SysUserRoleMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** MySQL profile 下的驿站入驻申请。 */
@Service
@Profile("mysql")
public class MysqlStationApplicationService implements StationApplicationService {
    private final StationApplicationMapper applicationMapper;
    private final StationService stationService;
    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final CredentialVersionService credentialVersionService;
    private final com.friendlystation.platform.notification.NotificationService notificationService;

    public MysqlStationApplicationService(StationApplicationMapper applicationMapper,
                                          StationService stationService,
                                          SysUserMapper userMapper,
                                          SysRoleMapper roleMapper,
                                          SysUserRoleMapper userRoleMapper,
                                          CredentialVersionService credentialVersionService,
                                          com.friendlystation.platform.notification.NotificationService notificationService) {
        this.applicationMapper = applicationMapper;
        this.stationService = stationService;
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.userRoleMapper = userRoleMapper;
        this.credentialVersionService = credentialVersionService;
        this.notificationService = notificationService;
    }

    @Override
    @Transactional
    public Map<String, Object> submit(Map<String, Object> request, long userId) {
        Long pending = applicationMapper.selectCount(new LambdaQueryWrapper<StationApplication>()
                .eq(StationApplication::getApplicantId, userId)
                .eq(StationApplication::getStatus, "PENDING"));
        if (pending != null && pending > 0) {
            throw new BusinessException("你已有一条待审核的入驻申请，请等待平台处理");
        }
        StationApplication item = new StationApplication();
        item.setApplicantId(userId);
        item.setStationName(required(request.get("stationName"), "请填写驿站名称"));
        item.setArea(required(request.get("area"), "请填写所属区域"));
        item.setAddress(required(request.get("address"), "请填写详细地址"));
        item.setContactName(FormatUtils.text(request.get("contactName"), ""));
        item.setContactPhone(FormatUtils.text(request.get("contactPhone"), ""));
        item.setOpenTime(FormatUtils.text(request.get("openTime"), ""));
        item.setStationType(FormatUtils.text(request.get("stationType"), ""));
        item.setCityCode(FormatUtils.text(request.get("cityCode"), "330100"));
        item.setDescription(FormatUtils.text(request.get("description"), ""));
        item.setLicenseUrl(FormatUtils.text(request.get("licenseUrl"), ""));
        item.setPhotoUrls(FormatUtils.text(request.get("photoUrls"), ""));
        item.setStatus("PENDING");
        item.setCreateTime(LocalDateTime.now());
        item.setUpdateTime(LocalDateTime.now());
        applicationMapper.insert(item);
        notifyPlatformAdmins(item);
        return view(item);
    }

    @Override
    public List<Map<String, Object>> mine(long userId) {
        return applicationMapper.selectList(new LambdaQueryWrapper<StationApplication>()
                        .eq(StationApplication::getApplicantId, userId)
                        .orderByDesc(StationApplication::getCreateTime)
                        .orderByDesc(StationApplication::getId))
                .stream().map(this::view).toList();
    }

    @Override
    public PageResult<Map<String, Object>> page(long current, long size, String status, String keyword) {
        long safeCurrent = Math.max(current, 1);
        long safeSize = Math.min(Math.max(size, 1), 50);
        LambdaQueryWrapper<StationApplication> query = new LambdaQueryWrapper<>();
        if (status != null && !status.isBlank()) {
            query.eq(StationApplication::getStatus, status.trim().toUpperCase());
        }
        if (keyword != null && !keyword.isBlank()) {
            String word = keyword.trim();
            query.and(wrapper -> wrapper.like(StationApplication::getStationName, word)
                    .or().like(StationApplication::getContactName, word)
                    .or().like(StationApplication::getContactPhone, word)
                    .or().like(StationApplication::getAddress, word));
        }
        query.last("ORDER BY FIELD(status,'PENDING','APPROVED','REJECTED','CANCELLED'), create_time DESC, id DESC");
        Page<StationApplication> result = applicationMapper.selectPage(new Page<>(safeCurrent, safeSize), query);
        return new PageResult<>(result.getCurrent(), result.getSize(), result.getTotal(),
                result.getRecords().stream().map(this::view).toList());
    }

    @Override
    @Transactional
    public Map<String, Object> approve(long id, long reviewerId, String reviewNote) {
        StationApplication item = applicationMapper.selectOne(new LambdaQueryWrapper<StationApplication>()
                .eq(StationApplication::getId, id).last("FOR UPDATE"));
        if (item == null) {
            throw new BusinessException("入驻申请不存在");
        }
        if (!"PENDING".equals(item.getStatus())) {
            throw new BusinessException("该申请已处理，不能重复审核");
        }
        StationDto station = stationService.create(new StationCreateRequest(
                item.getStationName(), item.getArea(), item.getAddress(), item.getContactPhone(),
                item.getOpenTime(), "OPEN", item.getStationType(), item.getCityCode()));
        stationService.assignManager(station.id(), item.getApplicantId());
        grantStationAdmin(item.getApplicantId());
        item.setStatus("APPROVED");
        item.setReviewNote(FormatUtils.text(reviewNote, "审核通过"));
        item.setReviewerId(reviewerId);
        item.setCreatedStationId(station.id());
        item.setUpdateTime(LocalDateTime.now());
        applicationMapper.updateById(item);
        notificationService.push(item.getApplicantId(), "SYSTEM", "驿站入驻审核通过",
                "「" + item.getStationName() + "」已创建，请重新登录后进入驿站管理工作台。",
                "", "STATION_APPLICATION", id);
        return view(item);
    }

    @Override
    @Transactional
    public Map<String, Object> reject(long id, long reviewerId, String reviewNote) {
        String note = FormatUtils.text(reviewNote, "");
        if (note.isBlank()) {
            throw new BusinessException("请填写未通过原因，申请人需要知道原因");
        }
        int updated = applicationMapper.update(null, new LambdaUpdateWrapper<StationApplication>()
                .eq(StationApplication::getId, id)
                .eq(StationApplication::getStatus, "PENDING")
                .set(StationApplication::getStatus, "REJECTED")
                .set(StationApplication::getReviewNote, note)
                .set(StationApplication::getReviewerId, reviewerId)
                .set(StationApplication::getUpdateTime, LocalDateTime.now()));
        if (updated == 0) {
            throw new BusinessException("该申请已处理，不能重复审核");
        }
        StationApplication item = applicationMapper.selectById(id);
        notificationService.push(item.getApplicantId(), "SYSTEM", "驿站入驻审核未通过",
                "「" + item.getStationName() + "」未通过：" + note, "", "STATION_APPLICATION", id);
        return view(item);
    }

    @Override
    @Transactional
    public Map<String, Object> cancel(long id, long userId) {
        int updated = applicationMapper.update(null, new LambdaUpdateWrapper<StationApplication>()
                .eq(StationApplication::getId, id)
                .eq(StationApplication::getApplicantId, userId)
                .eq(StationApplication::getStatus, "PENDING")
                .set(StationApplication::getStatus, "CANCELLED")
                .set(StationApplication::getUpdateTime, LocalDateTime.now()));
        if (updated == 0) {
            throw new BusinessException("只有待审核的本人申请可以撤回");
        }
        return view(applicationMapper.selectById(id));
    }

    @Override
    public long pendingCount() {
        Long count = applicationMapper.selectCount(new LambdaQueryWrapper<StationApplication>()
                .eq(StationApplication::getStatus, "PENDING"));
        return count == null ? 0 : count;
    }

    private void grantStationAdmin(long userId) {
        SysRole role = roleMapper.selectOne(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getRoleCode, "STATION_ADMIN"));
        if (role == null) {
            throw new BusinessException("系统缺少驿站管理员角色，无法完成入驻");
        }
        userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, userId));
        SysUserRole link = new SysUserRole();
        link.setUserId(userId);
        link.setRoleId(role.getId());
        userRoleMapper.insert(link);
        // 角色变化后旧令牌仍是 USER，必须让用户重新登录才能进入驿站端。
        credentialVersionService.bump(userId);
    }

    private void notifyPlatformAdmins(StationApplication item) {
        SysRole role = roleMapper.selectOne(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getRoleCode, "PLATFORM_ADMIN"));
        if (role == null) {
            return;
        }
        List<Long> ids = userRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>()
                        .eq(SysUserRole::getRoleId, role.getId()))
                .stream().map(SysUserRole::getUserId).filter(Objects::nonNull).distinct().toList();
        String link = "/admin?tab=stationApplications&applicationId=" + item.getId();
        for (Long adminId : ids) {
            notificationService.push(adminId, "SYSTEM", "新的驿站入驻申请",
                    item.getStationName() + " · " + item.getArea(), link,
                    "STATION_APPLICATION", item.getId());
        }
    }

    private Map<String, Object> view(StationApplication item) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", item.getId());
        out.put("applicantId", item.getApplicantId());
        out.put("applicantName", userName(item.getApplicantId()));
        out.put("stationName", item.getStationName());
        out.put("area", item.getArea());
        out.put("address", item.getAddress());
        out.put("contactName", item.getContactName() == null ? "" : item.getContactName());
        out.put("contactPhone", item.getContactPhone() == null ? "" : item.getContactPhone());
        out.put("openTime", item.getOpenTime() == null ? "" : item.getOpenTime());
        out.put("stationType", item.getStationType() == null ? "" : item.getStationType());
        out.put("cityCode", item.getCityCode() == null ? "" : item.getCityCode());
        out.put("description", item.getDescription() == null ? "" : item.getDescription());
        out.put("licenseUrl", item.getLicenseUrl() == null ? "" : item.getLicenseUrl());
        out.put("photoUrls", item.getPhotoUrls() == null ? "" : item.getPhotoUrls());
        out.put("status", item.getStatus());
        out.put("statusLabel", statusLabel(item.getStatus()));
        out.put("reviewNote", item.getReviewNote() == null ? "" : item.getReviewNote());
        out.put("reviewerId", item.getReviewerId() == null ? 0L : item.getReviewerId());
        out.put("createdStationId", item.getCreatedStationId() == null ? 0L : item.getCreatedStationId());
        out.put("createTime", FormatUtils.dateTime(item.getCreateTime()));
        out.put("updateTime", FormatUtils.dateTime(item.getUpdateTime()));
        return out;
    }

    private String userName(Long id) {
        if (id == null) {
            return "";
        }
        SysUser user = userMapper.selectById(id);
        if (user == null) {
            return "";
        }
        return user.getRealName() == null || user.getRealName().isBlank()
                ? user.getUsername() : user.getRealName();
    }

    private static String statusLabel(String status) {
        return switch (status == null ? "" : status) {
            case "APPROVED" -> "已通过";
            case "REJECTED" -> "未通过";
            case "CANCELLED" -> "已撤回";
            default -> "待审核";
        };
    }

    private static String required(Object value, String message) {
        String text = FormatUtils.text(value, "");
        if (text.isBlank()) {
            throw new BusinessException(message);
        }
        return text;
    }
}
