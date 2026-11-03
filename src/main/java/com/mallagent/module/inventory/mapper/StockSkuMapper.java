package com.mallagent.module.inventory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mallagent.module.inventory.entity.StockSku;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.Collection;
import java.util.List;

/**
 * 库存主记录 Mapper。
 *
 * <p>所有库存变更使用条件更新，并通过影响行数判断成功与否；禁止先查询再判断后更新。</p>
 */
@Mapper
public interface StockSkuMapper extends BaseMapper<StockSku> {

    /**
     * 锁定库存行并读取真实库存状态。
     *
     * <p>必须在事务内调用。用于库存条件更新后读取受事务保护的变更后值，
     * 不能使用事务外的普通读取推算流水前后值。</p>
     */
    @Select("""
            SELECT id, sku_id, available_stock, locked_stock, version,
                   created_at, updated_at, created_by, updated_by
            FROM stock_sku
            WHERE sku_id = #{skuId}
            FOR UPDATE
            """)
    StockSku selectBySkuIdForUpdate(@Param("skuId") Long skuId);

    @Select("""
            SELECT id, sku_id, available_stock, locked_stock, version,
                   created_at, updated_at, created_by, updated_by
            FROM stock_sku
            WHERE sku_id = #{skuId}
            """)
    StockSku selectBySkuId(@Param("skuId") Long skuId);

    /**
     * 批量查询库存。调用方必须保证 skuIds 非空。
     */
    @Select("""
            <script>
            SELECT id, sku_id, available_stock, locked_stock, version,
                   created_at, updated_at, created_by, updated_by
            FROM stock_sku
            WHERE sku_id IN
            <foreach collection="skuIds" item="skuId" open="(" separator="," close=")">
                #{skuId}
            </foreach>
            </script>
            """)
    List<StockSku> selectBySkuIds(@Param("skuIds") Collection<Long> skuIds);

    /**
     * 下单锁定：可售库存减少，锁定库存增加。
     *
     * @return 影响行数；0 表示库存不足或记录不存在
     */
    @Update("""
            UPDATE stock_sku
            SET available_stock = available_stock - #{quantity},
                locked_stock = locked_stock + #{quantity},
                version = version + 1,
                updated_at = CURRENT_TIMESTAMP(3),
                updated_by = #{operatorId}
            WHERE sku_id = #{skuId}
              AND available_stock >= #{quantity}
              AND locked_stock <= 2147483647 - #{quantity}
            """)
    int lockStock(@Param("skuId") Long skuId,
                  @Param("quantity") int quantity,
                  @Param("operatorId") Long operatorId);

    /**
     * 支付确认扣减：只减少锁定库存。
     *
     * @return 影响行数；0 表示记录不存在或锁定库存不足
     */
    @Update("""
            UPDATE stock_sku
            SET locked_stock = locked_stock - #{quantity},
                version = version + 1,
                updated_at = CURRENT_TIMESTAMP(3),
                updated_by = #{operatorId}
            WHERE sku_id = #{skuId}
              AND locked_stock >= #{quantity}
            """)
    int deductLocked(@Param("skuId") Long skuId,
                     @Param("quantity") int quantity,
                     @Param("operatorId") Long operatorId);

    /**
     * 取消/超时释放：锁定库存回到可售库存。
     *
     * @return 影响行数；0 表示记录不存在或锁定库存不足
     */
    @Update("""
            UPDATE stock_sku
            SET locked_stock = locked_stock - #{quantity},
                available_stock = available_stock + #{quantity},
                version = version + 1,
                updated_at = CURRENT_TIMESTAMP(3),
                updated_by = #{operatorId}
            WHERE sku_id = #{skuId}
              AND locked_stock >= #{quantity}
            """)
    int releaseLocked(@Param("skuId") Long skuId,
                      @Param("quantity") int quantity,
                      @Param("operatorId") Long operatorId);

    /**
     * 售后退货回补：只增加可售库存。
     *
     * @return 影响行数；0 表示记录不存在
     */
    @Update("""
            UPDATE stock_sku
            SET available_stock = available_stock + #{quantity},
                version = version + 1,
                updated_at = CURRENT_TIMESTAMP(3),
                updated_by = #{operatorId}
            WHERE sku_id = #{skuId}
              AND available_stock <= 2147483647 - #{quantity}
            """)
    int restoreAvailable(@Param("skuId") Long skuId,
                         @Param("quantity") int quantity,
                         @Param("operatorId") Long operatorId);

    /**
     * 管理端调整可售库存。delta 允许为负数，但调整后不能为负。
     *
     * @return 影响行数；0 表示记录不存在或调整后为负
     */
    @Update("""
            UPDATE stock_sku
            SET available_stock = available_stock + #{delta},
                version = version + 1,
                updated_at = CURRENT_TIMESTAMP(3),
                updated_by = #{operatorId}
            WHERE sku_id = #{skuId}
              AND available_stock + #{delta} >= 0
            """)
    int adjustAvailable(@Param("skuId") Long skuId,
                        @Param("delta") int delta,
                        @Param("operatorId") Long operatorId);

    /**
     * 正向调整：库存记录不存在时初始化，存在时原子累加可售库存。
     *
     * <p>只用于 delta &gt; 0，不会造成负库存；唯一键 sku_id 保证只创建一条库存记录。
     * 并发初始化由数据库行锁和唯一索引兜底。</p>
     */
    @Insert("""
            INSERT INTO stock_sku
                (sku_id, available_stock, locked_stock, version,
                 created_at, updated_at, created_by, updated_by)
            VALUES
                (#{skuId}, #{delta}, 0, 0,
                 CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), #{operatorId}, #{operatorId})
            ON DUPLICATE KEY UPDATE
                available_stock = available_stock + VALUES(available_stock),
                version = version + 1,
                updated_at = CURRENT_TIMESTAMP(3),
                updated_by = #{operatorId}
            """)
    int upsertPositive(@Param("skuId") Long skuId,
                       @Param("delta") int delta,
                       @Param("operatorId") Long operatorId);
}
