package com.friendlystation.platform.station;

import com.friendlystation.platform.auth.CredentialVersionService;
import com.friendlystation.platform.common.BusinessException;
import com.friendlystation.platform.common.FormatUtils;
import com.friendlystation.platform.common.PageResult;
import com.friendlystation.platform.persistence.entity.StationApplication;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/** 内存版驿站入驻申请，供默认演示 profile 使用。 */
@Service
@Profile("!mysql")
public class DemoStationApplicationService implements StationApplicationService {
    private final List<StationApplication> items = new CopyOnWriteArrayList<>();
    private final AtomicLong ids = new AtomicLong(5000);

    private final StationService stationService;
    private final com.friendlystation.platform.auth.MockAccountService accountService;
    private final CredentialVersionService credentialVersionService;
    private final com.friendlystation.platform.notification.NotificationService notificationService;

    public DemoStationApplicationService(StationService stationService,
                                         com.friendlystation.platform.auth.MockAccountService accountService,
                                         CredentialVersionService credentialVersionService,
                                         com.friendlystation.platform.notification.NotificationService notificationService) {
        this.stationService = stationService;
        this.accountService = accountService;
        this.credentialVersionService = credentialVersionService;
        this.notificationService = notificationService;
    }

    @Override
    public synchronized Map<String, Object> submit(Map<String, Object> request, long userId) {
        boolean pending = items.stream()
                .anyMatch(item -> item.getApplicantId() == userId && "PENDING".equals(item.getStatus()));
        if (pending) {
            throw new BusinessException("你已有一条待审核的入驻申请，请等待平台处理");
        }
        StationApplication item = new StationApplication();
        item.setId(ids.incrementAndGet());
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
        items.add(item);
        notifyPlatformAdmins(item);
        return view(item);
    }

    @Override
    public List<Map<String, Object>> mine(long userId) {
        return items.stream()
                .filter(item -> item.getApplicantId() != null && item.getApplicantId() == userId)
                .sorted(Comparator.comparing(StationApplication::getId).reversed())
                .map(this::view).toList();
    }

    @Override
    public PageResult<Map<String, Object>> page(long current, long size, String status, String keyword) {
        long safeCurrent = Math.max(current, 1);
        long safeSize = Math.min(Math.max(size, 1), 50);
        String normalizedStatus = status == null ? "" : status.trim().toUpperCase();
        String word = keyword == null ? "" : keyword.trim();
        List<StationApplication> filtered = items.stream()
                .filter(item -> normalizedStatus.isBlank() || normalizedStatus.equals(item.getStatus()))
                .filter(item -> word.isBlank()
                        || contains(item.getStationName(), word)
                        || contains(item.getContactName(), word)
                        || contains(item.getContactPhone(), word)
                        || contains(item.getAddress(), word))
                .sorted(Comparator.comparingInt((StationApplication item) -> statusOrder(item.getStatus()))
                        .thenComparing(StationApplication::getCreateTime,
                                Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(StationApplication::getId, Comparator.reverseOrder()))
                .toList();
        int from = (int) Math.min((safeCurrent - 1) * safeSize, filtered.size());
        int to = (int) Math.min(from + safeSize, filtered.size());
        return new PageResult<>(safeCurrent, safeSize, filtered.size(),
                new ArrayList<>(filtered.subList(from, to)).stream().map(this::view).toList());
    }

    @Override
    public synchronized Map<String, Object> approve(long id, long reviewerId, String reviewNote) {
        StationApplication item = byId(id);
        if (!"PENDING".equals(item.getStatus())) {
            throw new BusinessException("该申请已处理，不能重复审核");
        }
        StationDto station = stationService.create(new StationCreateRequest(
                item.getStationName(), item.getArea(), item.getAddress(), item.getContactPhone(),
                item.getOpenTime(), "OPEN", item.getStationType(), item.getCityCode()));
        stationService.assignManager(station.id(), item.getApplicantId());
        accountService.updateRole(item.getApplicantId(), "STATION_ADMIN");
        credentialVersionService.bump(item.getApplicantId());
        item.setStatus("APPROVED");
        item.setReviewNote(FormatUtils.text(reviewNote, "审核通过"));
        item.setReviewerId(reviewerId);
        item.setCreatedStationId(station.id());
        item.setUpdateTime(LocalDateTime.now());
        notificationService.push(item.getApplicantId(), "SYSTEM", "驿站入驻审核通过",
                "「" + item.getStationName() + "」已创建，请重新登录后进入驿站管理工作台。",
                "", "STATION_APPLICATION", id);
        return view(item);
    }

    @Override
    public synchronized Map<String, Object> reject(long id, long reviewerId, String reviewNote) {
        StationApplication item = byId(id);
        if (!"PENDING".equals(item.getStatus())) {
            throw new BusinessException("该申请已处理，不能重复审核");
        }
        String note = FormatUtils.text(reviewNote, "");
        if (note.isBlank()) {
            throw new BusinessException("请填写未通过原因，申请人需要知道原因");
        }
        item.setStatus("REJECTED");
        item.setReviewNote(note);
        item.setReviewerId(reviewerId);
        item.setUpdateTime(LocalDateTime.now());
        notificationService.push(item.getApplicantId(), "SYSTEM", "驿站入驻审核未通过",
                "「" + item.getStationName() + "」未通过：" + note, "", "STATION_APPLICATION", id);
        return view(item);
    }

    @Override
    public synchronized Map<String, Object> cancel(long id, long userId) {
        StationApplication item = byId(id);
        if (item.getApplicantId() == null || item.getApplicantId() != userId) {
            throw new BusinessException("只能撤回本人的入驻申请");
        }
        if (!"PENDING".equals(item.getStatus())) {
            throw new BusinessException("只有待审核的申请可以撤回");
        }
        item.setStatus("CANCELLED");
        item.setUpdateTime(LocalDateTime.now());
        return view(item);
    }

    @Override
    public long pendingCount() {
        return items.stream().filter(item -> "PENDING".equals(item.getStatus())).count();
    }

    private void notifyPlatformAdmins(StationApplication item) {
        String link = "/admin?tab=stationApplications&applicationId=" + item.getId();
        for (var user : accountService.list()) {
            if (!"PLATFORM_ADMIN".equals(user.role())) {
                continue;
            }
            notificationService.push(user.id(), "SYSTEM", "新的驿站入驻申请",
                    item.getStationName() + " · " + item.getArea(), link,
                    "STATION_APPLICATION", item.getId());
        }
    }

    private StationApplication byId(long id) {
        return items.stream().filter(item -> item.getId() != null && item.getId() == id).findFirst()
                .orElseThrow(() -> new BusinessException("入驻申请不存在"));
    }

    private Map<String, Object> view(StationApplication item) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", item.getId());
        out.put("applicantId", item.getApplicantId());
        var applicant = item.getApplicantId() == null ? null : accountService.findById(item.getApplicantId());
        out.put("applicantName", applicant == null ? "" : applicant.realName());
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

    private static boolean contains(String value, String word) {
        return value != null && value.contains(word);
    }

    private static int statusOrder(String status) {
        return switch (status == null ? "" : status) {
            case "PENDING" -> 0;
            case "APPROVED" -> 1;
            case "REJECTED" -> 2;
            default -> 3;
        };
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
