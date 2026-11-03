package com.mallagent.module.order.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单明细。商品名称、价格、规格等为下单时快照。
 */
@Data
@TableName("order_item")
public class OrderItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orderId;

    private Long skuId;

    private Long spuId;

    private String skuCode;

    private String spuName;

    private String imageUrl;

    private String specText;

    private BigDecimal unitPrice;

    private Integer quantity;

    private BigDecimal lineAmount;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
