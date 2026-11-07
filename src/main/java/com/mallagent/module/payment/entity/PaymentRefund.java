package com.mallagent.module.payment.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 模拟退款单。一个订单最多一条退款记录。
 */
@Data
@TableName("payment_refund")
public class PaymentRefund {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String refundNo;

    private String afterSaleNo;

    private String orderNo;

    private String paymentNo;

    private Long userId;

    private BigDecimal amount;

    private String channel;

    private String status;

    private LocalDateTime refundedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
