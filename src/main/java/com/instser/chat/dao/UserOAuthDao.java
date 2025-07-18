package com.instser.chat.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.instser.chat.entity.UserOAuth;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * OAuth2用户绑定数据访问层
 *
 * @author instser
 * @since 2025-07-16
 */
@Mapper
public interface UserOAuthDao extends BaseMapper<UserOAuth> {

    /**
     * 根据提供商和第三方用户ID查找OAuth绑定
     */
    @Select("SELECT * FROM t_user_oauth WHERE provider = #{provider} AND provider_id = #{providerId} AND deleted = 0")
    UserOAuth findByProviderAndProviderId(@Param("provider") String provider, @Param("providerId") String providerId);

    /**
     * 根据用户ID和提供商查找OAuth绑定
     */
    @Select("SELECT * FROM t_user_oauth WHERE user_id = #{userId} AND provider = #{provider} AND deleted = 0")
    UserOAuth findByUserIdAndProvider(@Param("userId") Long userId, @Param("provider") String provider);

    /**
     * 根据用户ID查找所有OAuth绑定
     */
    @Select("SELECT * FROM t_user_oauth WHERE user_id = #{userId} AND deleted = 0 ORDER BY create_time DESC")
    List<UserOAuth> findByUserId(@Param("userId") Long userId);

    /**
     * 根据邮箱和提供商查找OAuth绑定
     */
    @Select("SELECT * FROM t_user_oauth WHERE email = #{email} AND provider = #{provider} AND deleted = 0")
    UserOAuth findByEmailAndProvider(@Param("email") String email, @Param("provider") String provider);

    /**
     * 统计用户的OAuth绑定数量
     */
    @Select("SELECT COUNT(*) FROM t_user_oauth WHERE user_id = #{userId} AND status = 1 AND deleted = 0")
    int countByUserId(@Param("userId") Long userId);

    /**
     * 根据提供商统计绑定数量
     */
    @Select("SELECT COUNT(*) FROM t_user_oauth WHERE provider = #{provider} AND status = 1 AND deleted = 0")
    int countByProvider(@Param("provider") String provider);
}
