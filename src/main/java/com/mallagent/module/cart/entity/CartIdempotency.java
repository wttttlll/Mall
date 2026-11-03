package com.mallagent.module.cart.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 添加购物车请求的幂等记录。
 */
@Data
@TableName("cart_idempotency")
public class CartIdempotency {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String idempotencyKey;

    private String requestHash;

    private Long skuId;

    private Integer quantity;

    /**
     * 本次插入生成的令牌；重复请求读到的是首次请求的令牌。
     */
    private String claimToken;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
