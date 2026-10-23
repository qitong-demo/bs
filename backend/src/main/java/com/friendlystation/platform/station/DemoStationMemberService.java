package com.friendlystation.platform.station;

import com.friendlystation.platform.auth.CredentialVersionService;
import com.friendlystation.platform.common.BusinessException;
import com.friendlystation.platform.common.FormatUtils;
import com.friendlystation.platform.persistence.entity.StationMember;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

@Service
@Profile("!mysql")
public class DemoStationMemberService implements StationMemberService {
    private final List<StationMember> members = new CopyOnWriteArrayList<>();
    private final AtomicLong ids = new AtomicLong(6000);
    private final StationService stationService;
    private final com.friendlystation.platform.auth.MockAccountService accountService;
    private final CredentialVersionService credentialVersionService;

    public DemoStationMemberService(StationService stationService,
                                    com.friendlystation.platform.auth.MockAccountService accountService,
                                    CredentialVersionService credentialVersionService) {
        this.stationService = stationService;
        this.accountService = accountService;
        this.credentialVersionService = credentialVersionService;
    }

    @Override
    public List<Map<String, Object>> list(long stationId, long operatorId) {
        requireOwner(stationId, operatorId);
        return members.stream().filter(item -> item.getStationId() == stationId).map(this::view).toList();
    }

    @Override
    public Map<String, Object> add(long stationId, Map<String, Object> request, long operatorId) {
        requireOwner(stationId, operatorId);
        String username = FormatUtils.text(request.get("username"), "");
        var account = accountService.findByUsername(username);
        if (account == null) {
            throw new BusinessException("该账号不存在，请先让工作人员注册");
        }
        if (account.id() == operatorId) {
            throw new BusinessException("不能把站长本人添加为成员");
        }
        if (members.stream().anyMatch(item -> item.getStationId() == stationId && item.getUserId() == account.id())) {
            throw new BusinessException("该账号已经是本站点成员");
        }
        StationMember member = new StationMember();
        member.setId(ids.incrementAndGet());
        member.setStationId(stationId);
        member.setUserId(account.id());
        member.setMemberRole("VOLUNTEER".equalsIgnoreCase(FormatUtils.text(request.get("memberRole"), "STAFF"))
                ? "VOLUNTEER" : "STAFF");
        member.setStatus("ACTIVE");
        member.setCreateTime(LocalDateTime.now());
        member.setUpdateTime(LocalDateTime.now());
        members.add(member);
        syncRole(account.id());
        credentialVersionService.bump(account.id());
        return view(member);
    }

    @Override
    public Map<String, Object> updateStatus(long id, String status, long operatorId) {
        StationMember member = require(id);
        requireOwner(member.getStationId(), operatorId);
        member.setStatus("DISABLED".equalsIgnoreCase(status) ? "DISABLED" : "ACTIVE");
        member.setUpdateTime(LocalDateTime.now());
        syncRole(member.getUserId());
        credentialVersionService.bump(member.getUserId());
        return view(member);
    }

    @Override
    public void remove(long id, long operatorId) {
        StationMember member = require(id);
        requireOwner(member.getStationId(), operatorId);
        members.remove(member);
        syncRole(member.getUserId());
        credentialVersionService.bump(member.getUserId());
    }

    @Override
    public List<Long> stationIdsOf(long userId) {
        return members.stream()
                .filter(item -> item.getUserId() == userId && "ACTIVE".equals(item.getStatus()))
                .map(StationMember::getStationId).distinct().toList();
    }

    private void requireOwner(long stationId, long operatorId) {
        StationDto station = stationService.detail(stationId);
        if (station.managerId() == null || station.managerId() != operatorId) {
            throw new com.friendlystation.platform.common.ForbiddenException("只有本站点负责人可以管理成员");
        }
    }

    private StationMember require(long id) {
        return members.stream().filter(item -> item.getId() == id).findFirst()
                .orElseThrow(() -> new BusinessException("成员不存在"));
    }

    private void syncRole(long userId) {
        StationMember active = members.stream()
                .filter(item -> item.getUserId() == userId && "ACTIVE".equals(item.getStatus()))
                .findFirst().orElse(null);
        accountService.updateRole(userId, active == null ? "USER"
                : ("VOLUNTEER".equals(active.getMemberRole()) ? "STATION_VOLUNTEER" : "STATION_STAFF"));
    }

    private Map<String, Object> view(StationMember member) {
        var account = accountService.findById(member.getUserId());
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("id", member.getId());
        out.put("stationId", member.getStationId());
        out.put("userId", member.getUserId());
        out.put("username", account == null ? "" : account.username());
        out.put("memberRole", member.getMemberRole());
        out.put("memberRoleLabel", "VOLUNTEER".equals(member.getMemberRole()) ? "志愿者" : "工作人员");
        out.put("status", member.getStatus());
        out.put("statusLabel", "ACTIVE".equals(member.getStatus()) ? "正常" : "已停用");
        out.put("createTime", FormatUtils.dateTime(member.getCreateTime()));
        return out;
    }
}
