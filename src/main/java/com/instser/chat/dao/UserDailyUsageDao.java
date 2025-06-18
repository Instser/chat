package com.instser.chat.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.instser.chat.entity.UserDailyUsage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

/**
 * 用户每日使用记录数据访问层
 *
 * @author instser
 * @since 2025-06-18
 */
@Mapper
public interface UserDailyUsageDao extends BaseMapper<UserDailyUsage> {

    /**
     * 获取用户指定日期的使用记录
     *
     * @param userId     用户ID
     * @param packageId  套餐ID
     * @param usageDate  使用日期
     * @return 使用记录
     */
    UserDailyUsage selectByUserAndDate(@Param("userId") Long userId, 
                                       @Param("packageId") Long packageId, 
                                       @Param("usageDate") LocalDate usageDate);

    /**
     * 增加用户当日使用次数
     *
     * @param userId     用户ID
     * @param packageId  套餐ID
     * @param usageDate  使用日期
     * @return 影响行数
     */
    int incrementUsageCount(@Param("userId") Long userId,
                           @Param("packageId") Long packageId,
                           @Param("usageDate") LocalDate usageDate);

    /**
     * 原子性插入或更新用户当日使用记录
     * 如果记录不存在则创建，如果存在则增加使用次数
     *
     * @param userId     用户ID
     * @param packageId  套餐ID
     * @param usageDate  使用日期
     * @param dailyLimit 每日限制次数
     * @return 影响行数
     */
    int insertOrIncrementUsage(@Param("userId") Long userId,
                              @Param("packageId") Long packageId,
                              @Param("usageDate") LocalDate usageDate,
                              @Param("dailyLimit") Integer dailyLimit);

    /**
     * 获取用户最近的使用记录
     *
     * @param userId 用户ID
     * @param days   天数
     * @return 使用记录列表
     */
    List<UserDailyUsage> selectRecentUsage(@Param("userId") Long userId, 
                                          @Param("days") Integer days);

    /**
     * 清理过期的使用记录（超过指定天数）
     *
     * @param beforeDate 清理此日期之前的记录
     * @return 清理的记录数
     */
    int deleteExpiredRecords(@Param("beforeDate") LocalDate beforeDate);
}
