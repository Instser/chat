package com.instser.chat.vo;

import lombok.Data;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;

/**
 * 套餐信息请求VO类
 *
 * @author instser
 * @since 2024-01-01
 */
@Data
public class PackageInfoRequestVO {

    /**

     */
    @NotNull(message = "套餐ID不能为空")
    private Integer packageId;

    /**
     * 货币类型
     */
    @NotBlank(message = "货币类型不能为空")
    private String currency;
}
