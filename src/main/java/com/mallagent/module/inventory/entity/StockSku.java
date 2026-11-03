package com.mallagent.module.inventory.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * SKU 库存主记录。
 *
 * <p>库存变更使用条件更新，不使用 MyBatis-Plus 乐观锁更新；
 * {@code version} 仅作为每次变更的审计版本号。</p>
 */
@Data
@TableName("stock_sku")
public class StockSku {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long skuId;

    private Integer availableStock;

    private Integer lockedStock;

    private Integer version;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableField(fill = FieldFill.INSERT)
    private Long createdBy;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updatedBy;
}
