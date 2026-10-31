package com.mallagent.module.product.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * SKU 与规格值的关联。
 */
@Data
@TableName("product_sku_spec")
public class ProductSkuSpec {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long skuId;

    private Long specId;

    private Long specValueId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
