package com.instser.chat.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.instser.chat.entity.PaymentAudit;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 支付审计日志数据访问层
 *
 * @author instser
 * @since 2025-06-29
 */
@Mapper
public interface PaymentAuditDao extends BaseMapper<PaymentAudit> {

    /**
     * 根据订单号查询审计日志
     *
     * @param orderNo 订单号
     * @return 审计日志列表
     */
    @Select("SELECT * FROM t_payment_audit WHERE order_no = #{orderNo} AND deleted = 0 ORDER BY create_time DESC")
    List<PaymentAudit> selectByOrderNo(String orderNo);

    /**
     * 根据操作类型查询审计日志
     *
     * @param operationType 操作类型
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 审计日志列表
     */
    @Select("SELECT * FROM t_payment_audit WHERE operation_type = #{operationType} " +
            "AND create_time >= #{startTime} AND create_time <= #{endTime} " +
            "AND deleted = 0 ORDER BY create_time DESC")
    List<PaymentAudit> selectByOperationType(String operationType, LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 根据操作人查询审计日志
     *
     * @param operatorId 操作人ID
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 审计日志列表
     */
    @Select("SELECT * FROM t_payment_audit WHERE operator_id = #{operatorId} " +
            "AND create_time >= #{startTime} AND create_time <= #{endTime} " +
            "AND deleted = 0 ORDER BY create_time DESC")
    List<PaymentAudit> selectByOperator(Long operatorId, LocalDateTime startTime, LocalDateTime endTime);
}
