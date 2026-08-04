package com.instser.chat.admin.query;

import com.instser.chat.admin.common.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
public class AdminOrderQuery extends PageQuery {

    private String orderNo;
    private Long userId;
    private Integer paymentStatus;
    private String paymentMethod;
    private Long packageId;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime createdFrom;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime createdTo;
}
