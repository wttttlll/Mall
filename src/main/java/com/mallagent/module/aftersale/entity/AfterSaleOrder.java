package com.mallagent.module.aftersale.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 整单售后申请。active_order_no 为数据库生成列，不在实体中映射。
 */
@Data
@TableName("after_sale_order")
public class AfterSaleOrder {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String afterSaleNo;

    private String orderNo;

    private Long userId;

    private String status;

    private BigDecimal refundAmount;

    private String reason;

    private String description;

    private String rejectReason;

    private Long reviewedBy;

    private LocalDateTime reviewedAt;

    private Long completedBy;

    private LocalDateTime completedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
