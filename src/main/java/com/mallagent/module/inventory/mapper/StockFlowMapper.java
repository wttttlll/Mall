package com.mallagent.module.inventory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mallagent.module.inventory.entity.StockFlow;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 库存流水 Mapper。
 *
 * <p>流水占位与库存变更必须在同一事务内完成。流水占位使用普通 INSERT；
 * 业务唯一键冲突由调用方捕获 DuplicateKeyException 后读取已有流水比较，
 * 其他 SQL 错误必须继续抛出，不能当作幂等成功。</p>
 */
@Mapper
public interface StockFlowMapper extends BaseMapper<StockFlow> {

    /**
     * 插入流水占位。
     *
     * <p>普通 INSERT，不忽略任何 SQL 错误。业务唯一键冲突会抛出 DuplicateKeyException，
     * 调用方必须捕获后读取已有流水并严格比较数量/变化量。</p>
     */
    @Insert("""
            INSERT INTO stock_flow
                (biz_type, biz_no, ref_biz_no, sku_id, quantity,
                 change_available, change_locked, request_token, remark, created_by)
            VALUES
                (#{flow.bizType}, #{flow.bizNo}, #{flow.refBizNo}, #{flow.skuId}, #{flow.quantity},
                 #{flow.changeAvailable}, #{flow.changeLocked}, #{flow.requestToken},
                 #{flow.remark}, #{flow.createdBy})
            """)
    int claim(@Param("flow") StockFlow flow);

    @Select("""
            SELECT id, biz_type, biz_no, ref_biz_no, sku_id, quantity,
                   change_available, change_locked, available_before, available_after,
                   locked_before, locked_after, request_token, remark, created_at, created_by
            FROM stock_flow
            WHERE biz_type = #{bizType} AND biz_no = #{bizNo} AND sku_id = #{skuId}
            """)
    StockFlow selectByBizKey(@Param("bizType") String bizType,
                             @Param("bizNo") String bizNo,
                             @Param("skuId") Long skuId);

    /**
     * 管理端调整幂等键查询。admin_biz_no 唯一索引保证一个 Key 只有一条流水。
     */
    @Select("""
            SELECT id, biz_type, biz_no, ref_biz_no, sku_id, quantity,
                   change_available, change_locked, available_before, available_after,
                   locked_before, locked_after, request_token, remark, created_at, created_by
            FROM stock_flow
            WHERE biz_type = 'ADMIN_ADJUST' AND biz_no = #{bizNo}
            LIMIT 1
            """)
    StockFlow selectAdminAdjustByBizNo(@Param("bizNo") String bizNo);

    /**
     * 查询订单全部 ORDER_LOCK 流水，按 sku_id 升序；不加锁。
     *
     * <p>用于 lock 操作完成占位后校验完整 SKU 集合。</p>
     */
    @Select("""
            SELECT id, biz_type, biz_no, ref_biz_no, sku_id, quantity,
                   change_available, change_locked, available_before, available_after,
                   locked_before, locked_after, request_token, remark, created_at, created_by
            FROM stock_flow
            WHERE biz_type = 'ORDER_LOCK' AND biz_no = #{bizNo}
            ORDER BY sku_id
            """)
    List<StockFlow> selectOrderLockFlows(@Param("bizNo") String bizNo);

    /**
     * 锁定订单全部 ORDER_LOCK 流水行，按 sku_id 升序获取行锁。
     *
     * <p>这是订单 + SKU 生命周期操作的统一互斥点；deduct/release/restore 必须先调用本方法。
     * 必须在事务内使用。</p>
     */
    @Select("""
            SELECT id, biz_type, biz_no, ref_biz_no, sku_id, quantity,
                   change_available, change_locked, available_before, available_after,
                   locked_before, locked_after, request_token, remark, created_at, created_by
            FROM stock_flow
            WHERE biz_type = 'ORDER_LOCK' AND biz_no = #{bizNo}
            ORDER BY sku_id
            FOR UPDATE
            """)
    List<StockFlow> selectOrderLockRowsForUpdate(@Param("bizNo") String bizNo);

    /**
     * 查询订单终态流水（扣减/释放），按 sku_id 升序。
     */
    @Select("""
            SELECT id, biz_type, biz_no, ref_biz_no, sku_id, quantity,
                   change_available, change_locked, available_before, available_after,
                   locked_before, locked_after, request_token, remark, created_at, created_by
            FROM stock_flow
            WHERE biz_type IN ('ORDER_DEDUCT', 'ORDER_RELEASE') AND biz_no = #{bizNo}
            ORDER BY sku_id
            """)
    List<StockFlow> selectTerminalFlows(@Param("bizNo") String bizNo);

    /**
     * 查询订单扣减流水，用于售后回补校验。
     */
    @Select("""
            SELECT id, biz_type, biz_no, ref_biz_no, sku_id, quantity,
                   change_available, change_locked, available_before, available_after,
                   locked_before, locked_after, request_token, remark, created_at, created_by
            FROM stock_flow
            WHERE biz_type = 'ORDER_DEDUCT' AND biz_no = #{bizNo}
            ORDER BY sku_id
            """)
    List<StockFlow> selectDeductFlows(@Param("bizNo") String bizNo);

    /**
     * 查询同一售后单的全部 RETURN_RESTORE 流水，按 sku_id 升序。
     */
    @Select("""
            SELECT id, biz_type, biz_no, ref_biz_no, sku_id, quantity,
                   change_available, change_locked, available_before, available_after,
                   locked_before, locked_after, request_token, remark, created_at, created_by
            FROM stock_flow
            WHERE biz_type = 'RETURN_RESTORE' AND biz_no = #{bizNo}
            ORDER BY sku_id
            """)
    List<StockFlow> selectRestoreFlows(@Param("bizNo") String bizNo);

    /**
     * 统计已生效的售后回补数量。
     *
     * <p>只统计 available_after 已回填的流水，当前尚未完成的占位行不会计入，
     * 避免把本次数量重复计算。</p>
     */
    @Select("""
            SELECT COALESCE(SUM(quantity), 0)
            FROM stock_flow
            WHERE biz_type = 'RETURN_RESTORE'
              AND ref_biz_no = #{refBizNo}
              AND sku_id = #{skuId}
              AND available_after IS NOT NULL
            """)
    long sumFinalizedRestoreQuantity(@Param("refBizNo") String refBizNo,
                                     @Param("skuId") Long skuId);

    /**
     * 回填流水变更前后值。仅允许回填一次，防止重复提交。
     *
     * @return 影响行数；0 表示流水不存在或已经回填
     */
    @Update("""
            UPDATE stock_flow
            SET available_before = #{availableBefore},
                available_after = #{availableAfter},
                locked_before = #{lockedBefore},
                locked_after = #{lockedAfter}
            WHERE id = #{id}
              AND available_after IS NULL
            """)
    int finalize(@Param("id") Long id,
                 @Param("availableBefore") Integer availableBefore,
                 @Param("availableAfter") Integer availableAfter,
                 @Param("lockedBefore") Integer lockedBefore,
                 @Param("lockedAfter") Integer lockedAfter);
}
