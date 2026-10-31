package com.mallagent.module.product.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 商品 SPU 图片。
 */
@Data
@TableName("product_spu_image")
public class ProductSpuImage {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long spuId;

    private String imageUrl;

    private Integer sort;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
