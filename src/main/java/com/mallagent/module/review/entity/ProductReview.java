package com.mallagent.module.review.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 商品评价。订单明细唯一，保存下单时的商品快照用于公开与个人列表展示。
 */
@Data
@TableName("product_review")
public class ProductReview {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String orderNo;

    private Long orderItemId;

    private Long spuId;

    private Long skuId;

    private String spuName;

    private String skuCode;

    private String imageUrl;

    private Integer rating;

    private String content;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
