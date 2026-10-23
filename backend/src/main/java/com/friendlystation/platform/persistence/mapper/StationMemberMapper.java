package com.friendlystation.platform.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.friendlystation.platform.persistence.entity.StationMember;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface StationMemberMapper extends BaseMapper<StationMember> {
}
