package com.instser.chat.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.instser.chat.entity.Package;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 套餐数据访问层
 *
 * @author instser
 * @since 2024-01-01
 */
@Mapper
public interface PackageDao extends BaseMapper<Package> {

    /**
     * 查询所有启用的套餐
     *
     * @return 套餐列表
     */
    @Select("SELECT * FROM t_package WHERE is_enabled = 1 AND deleted = 0 ORDER BY sort_order ASC, create_time DESC")
    List<Package> selectEnabledPackages();

    /**
     * 根据套餐类型查询套餐
     *
     * @param packageType 套餐类型
     * @return 套餐列表
     */
    @Select("SELECT * FROM t_package WHERE package_type = #{packageType} AND is_enabled = 1 AND deleted = 0 ORDER BY sort_order ASC")
    List<Package> selectByPackageType(String packageType);
}
