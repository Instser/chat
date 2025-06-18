package com.instser.chat.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.instser.chat.entity.UserAccount;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户账户数据访问层
 *
 * @author instser
 * @since 2025-06-18
 */
@Mapper
public interface UserAccountDao extends BaseMapper<UserAccount> {

    /**
     * 根据用户ID获取账户信息
     *
     * @param userId 用户ID
     * @return 用户账户信息
     */
    UserAccount selectByUserId(@Param("userId") Long userId);

    /**
     * 更新用户套餐信息
     *
     * @param userId           用户ID
     * @param packageId        套餐ID
     * @param packageExpireTime 套餐过期时间
     * @param chatCountRemaining 剩余聊天次数
     * @return 影响行数
     */
    int updatePackageInfo(@Param("userId") Long userId,
                         @Param("packageId") Long packageId,
                         @Param("packageExpireTime") LocalDateTime packageExpireTime,
                         @Param("chatCountRemaining") Integer chatCountRemaining);

    /**
     * 原子性减少剩余聊天次数（用于总次数限制模式）
     *
     * @param userId 用户ID
     * @param count  减少的次数
     * @return 影响行数
     */
    int decrementChatCount(@Param("userId") Long userId, @Param("count") Integer count);

    /**
     * 增加总聊天次数统计
     *
     * @param userId 用户ID
     * @param count  增加的次数
     * @return 影响行数
     */
    int incrementTotalChatCount(@Param("userId") Long userId, @Param("count") Integer count);

    /**
     * 获取过期的用户账户列表
     *
     * @param beforeTime 过期时间点
     * @param limit      限制数量
     * @return 过期账户列表
     */
    List<UserAccount> selectExpiredAccounts(@Param("beforeTime") LocalDateTime beforeTime,
                                           @Param("limit") Integer limit);

    /**
     * 批量清理过期套餐信息
     *
     * @param beforeTime 过期时间点
     * @return 清理的记录数
     */
    int cleanExpiredPackages(@Param("beforeTime") LocalDateTime beforeTime);

    /**
     * 检查用户是否存在账户记录
     *
     * @param userId 用户ID
     * @return 记录数量
     */
    int countByUserId(@Param("userId") Long userId);

    /**
     * 创建或更新用户账户信息（原子性操作）
     *
     * @param userId           用户ID
     * @param packageId        套餐ID
     * @param packageExpireTime 套餐过期时间
     * @param chatCountRemaining 剩余聊天次数
     * @return 影响行数
     */
    int insertOrUpdateAccount(@Param("userId") Long userId,
                             @Param("packageId") Long packageId,
                             @Param("packageExpireTime") LocalDateTime packageExpireTime,
                             @Param("chatCountRemaining") Integer chatCountRemaining);
}
