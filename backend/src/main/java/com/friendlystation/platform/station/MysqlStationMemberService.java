package com.friendlystation.platform.station;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.friendlystation.platform.auth.AccountService;
import com.friendlystation.platform.auth.CredentialVersionService;
import com.friendlystation.platform.common.BusinessException;
import com.friendlystation.platform.common.FormatUtils;
import com.friendlystation.platform.persistence.entity.StationMember;
import com.friendlystation.platform.persistence.entity.SysRole;
import com.friendlystation.platform.persistence.entity.SysUserRole;
import com.friendlystation.platform.persistence.mapper.StationMemberMapper;
import com.friendlystation.platform.persistence.mapper.SysRoleMapper;
import com.friendlystation.platform.persistence.mapper.SysUserRoleMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Profile("mysql")
public class MysqlStationMemberService implements StationMemberService {
    private final StationMemberMapper memberMapper;
    private final StationService stationService;
    private final AccountService accountService;
    private final SysRoleMapper roleMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final CredentialVersionService credentialVersionService;

    public MysqlStationMemberService(StationMemberMapper memberMapper, StationService stationService,
                                     AccountService accountService, SysRoleMapper roleMapper,
                                     SysUserRoleMapper userRoleMapper,
                                     CredentialVersionService credentialVersionService) {
        this.memberMapper = memberMapper;
        this.stationService = stationService;
        this.accountService = accountService;
        this.roleMapper = roleMapper;
        this.userRoleMapper = userRoleMapper;
        this.credentialVersionService = credentialVersionService;
    }

    @Override
    public List<Map<String, Object>> list(long stationId, long operatorId) {
        requireOwner(stationId, operatorId);
        return memberMapper.selectList(new LambdaQueryWrapper<StationMember>()
                        .eq(StationMember::getStationId, stationId)
                        .orderByAsc(StationMember::getStatus)
                        .orderByDesc(StationMember::getId))
                .stream().map(this::view).toList();
    }

    @Override
    @Transactional
    public Map<String, Object> add(long stationId, Map<String, Object> request, long operatorId) {
        requireOwner(stationId, operatorId);
        String username = FormatUtils.text(request.get("username"), "");
        if (username.isBlank()) {
            throw new BusinessException("请填写已注册的账号");
        }
        var account = accountService.findByUsername(username);
        if (account == null) {
            throw new BusinessException("该账号不存在，请先让工作人员注册");
        }
        if (account.id() == operatorId) {
            throw new BusinessException("不能把站长本人添加为成员");
        }
        Long exists = memberMapper.selectCount(new LambdaQueryWrapper<StationMember>()
                .eq(StationMember::getStationId, stationId)
                .eq(StationMember::getUserId, account.id()));
        if (exists != null && exists > 0) {
            throw new BusinessException("该账号已经是本站点成员");
        }
        String role = "VOLUNTEER".equalsIgnoreCase(FormatUtils.text(request.get("memberRole"), "STAFF"))
                ? "VOLUNTEER" : "STAFF";
        StationMember member = new StationMember();
        member.setStationId(stationId);
        member.setUserId(account.id());
        member.setMemberRole(role);
        member.setStatus("ACTIVE");
        member.setCreateTime(LocalDateTime.now());
        member.setUpdateTime(LocalDateTime.now());
        memberMapper.insert(member);
        grantRole(account.id(), role);
        credentialVersionService.bump(account.id());
        return view(member);
    }

    @Override
    @Transactional
    public Map<String, Object> updateStatus(long id, String status, long operatorId) {
        StationMember member = requireMember(id);
        requireOwner(member.getStationId(), operatorId);
        member.setStatus("DISABLED".equalsIgnoreCase(status) ? "DISABLED" : "ACTIVE");
        member.setUpdateTime(LocalDateTime.now());
        memberMapper.updateById(member);
        syncRole(member.getUserId());
        credentialVersionService.bump(member.getUserId());
        return view(member);
    }

    @Override
    @Transactional
    public void remove(long id, long operatorId) {
        StationMember member = requireMember(id);
        requireOwner(member.getStationId(), operatorId);
        memberMapper.deleteById(id);
        syncRole(member.getUserId());
        credentialVersionService.bump(member.getUserId());
    }

    @Override
    public List<Long> stationIdsOf(long userId) {
        return memberMapper.selectList(new LambdaQueryWrapper<StationMember>()
                        .eq(StationMember::getUserId, userId)
                        .eq(StationMember::getStatus, "ACTIVE"))
                .stream().map(StationMember::getStationId).distinct().toList();
    }

    private void requireOwner(long stationId, long operatorId) {
        StationDto station = stationService.detail(stationId);
        if (station.managerId() == null || station.managerId() != operatorId) {
            throw new com.friendlystation.platform.common.ForbiddenException("只有本站点负责人可以管理成员");
        }
    }

    private StationMember requireMember(long id) {
        StationMember member = memberMapper.selectById(id);
        if (member == null) {
            throw new BusinessException("成员不存在");
        }
        return member;
    }

    private void grantRole(long userId, String memberRole) {
        String roleCode = "VOLUNTEER".equals(memberRole) ? "STATION_VOLUNTEER" : "STATION_STAFF";
        SysRole role = roleMapper.selectOne(new LambdaQueryWrapper<SysRole>().eq(SysRole::getRoleCode, roleCode));
        if (role == null) {
            throw new BusinessException("系统缺少成员角色：" + roleCode);
        }
        userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, userId));
        SysUserRole link = new SysUserRole();
        link.setUserId(userId);
        link.setRoleId(role.getId());
        userRoleMapper.insert(link);
    }

    private void syncRole(long userId) {
        List<StationMember> active = memberMapper.selectList(new LambdaQueryWrapper<StationMember>()
                .eq(StationMember::getUserId, userId)
                .eq(StationMember::getStatus, "ACTIVE"));
        if (active.isEmpty()) {
            SysRole role = roleMapper.selectOne(new LambdaQueryWrapper<SysRole>().eq(SysRole::getRoleCode, "USER"));
            if (role != null) {
                userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, userId));
                SysUserRole link = new SysUserRole();
                link.setUserId(userId);
                link.setRoleId(role.getId());
                userRoleMapper.insert(link);
            }
            return;
        }
        grantRole(userId, active.get(0).getMemberRole());
    }

    private Map<String, Object> view(StationMember member) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", member.getId());
        out.put("stationId", member.getStationId());
        out.put("userId", member.getUserId());
        out.put("username", accountService.usernameOf(member.getUserId()));
        out.put("memberRole", member.getMemberRole());
        out.put("memberRoleLabel", "VOLUNTEER".equals(member.getMemberRole()) ? "志愿者" : "工作人员");
        out.put("status", member.getStatus());
        out.put("statusLabel", "ACTIVE".equals(member.getStatus()) ? "正常" : "已停用");
        out.put("createTime", FormatUtils.dateTime(member.getCreateTime()));
        return out;
    }
}
