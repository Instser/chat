package com.instser.chat.admin.query;

import com.instser.chat.admin.common.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
public class AdminAuditQuery extends PageQuery {

    private String operationType;
    private String orderNo;
    private Long operatorId;
    private String result;
    private String target;
    private String provider;
    private String action;
    private String status;
    private String traceId;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime createdFrom;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime createdTo;
}
