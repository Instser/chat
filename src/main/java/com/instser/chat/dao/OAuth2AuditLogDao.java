package com.instser.chat.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.instser.chat.entity.OAuth2AuditLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * OAuth2审计日志数据访问层
 *
 * @author instser
 * @since 2025-07-16
 */
@Mapper
public interface OAuth2AuditLogDao extends BaseMapper<OAuth2AuditLog> {

    /**
     * 根据用户ID查找审计日志
     */
    @Select("SELECT * FROM t_oauth2_audit_log WHERE user_id = #{userId} ORDER BY create_time DESC LIMIT #{limit}")
    List<OAuth2AuditLog> findByUserIdOrderByCreateTimeDesc(@Param("userId") Long userId, @Param("limit") int limit);

    /**
     * 根据提供商和状态查找审计日志
     */
    @Select("SELECT * FROM t_oauth2_audit_log WHERE provider = #{provider} AND status = #{status} ORDER BY create_time DESC LIMIT #{limit}")
    List<OAuth2AuditLog> findByProviderAndStatus(@Param("provider") String provider, @Param("status") String status, @Param("limit") int limit);

    /**
     * 统计指定时间范围内的登录次数
     */
    @Select("SELECT COUNT(*) FROM t_oauth2_audit_log WHERE action = 'LOGIN' AND status = 'SUCCESS' AND create_time BETWEEN #{startTime} AND #{endTime}")
    int countSuccessfulLogins(@Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime);

    /**
     * 统计指定时间范围内的失败次数
     */
    @Select("SELECT COUNT(*) FROM t_oauth2_audit_log WHERE action = 'LOGIN' AND status = 'FAILED' AND create_time BETWEEN #{startTime} AND #{endTime}")
    int countFailedLogins(@Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime);

    /**
     * 根据IP地址查找最近的审计日志
     */
    @Select("SELECT * FROM t_oauth2_audit_log WHERE client_ip = #{clientIp} ORDER BY create_time DESC LIMIT #{limit}")
    List<OAuth2AuditLog> findByClientIp(@Param("clientIp") String clientIp, @Param("limit") int limit);
}
