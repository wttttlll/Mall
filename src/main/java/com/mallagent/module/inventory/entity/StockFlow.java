package com.mallagent.module.inventory.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 库存流水。提交后不可修改、不可删除。
 *
 * <p>{@code adminBizNo} 是数据库生成列，仅 ADMIN_ADJUST 有值，由数据库唯一索引保证
 * 同一个管理端 Idempotency-Key 不会被不同 SKU/delta 复用；实体不映射该生成列。</p>
 */
@Data
@TableName("stock_flow")
public class StockFlow {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String bizType;

    private String bizNo;

    private String refBizNo;

    private Long skuId;

    private Integer quantity;

    private Integer changeAvailable;

    private Integer changeLocked;

    private Integer availableBefore;

    private Integer availableAfter;

    private Integer lockedBefore;

    private Integer lockedAfter;

    /**
     * 每次库存操作尝试生成的一次性令牌，仅用于辅助识别；不能单独作为成功依据。
     */
    private String requestToken;

    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT)
    private Long createdBy;
}
