package com.instser.chat.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.instser.chat.entity.OperationAudit;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OperationAuditDao extends BaseMapper<OperationAudit> {
}
