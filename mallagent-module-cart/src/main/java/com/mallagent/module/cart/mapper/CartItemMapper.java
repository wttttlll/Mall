package com.mallagent.module.cart.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mallagent.module.cart.entity.CartItem;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 购物车条目 Mapper。添加使用唯一索引 + 原子 upsert，避免并发丢更新。
 */
@Mapper
public interface CartItemMapper extends BaseMapper<CartItem> {

    /**
     * 原子添加：不存在则插入，存在则累加数量。
     */
    @Insert("""
            INSERT INTO cart_item
                (user_id, sku_id, quantity, selected, created_at, updated_at, created_by, updated_by, version)
            VALUES
                (#{userId}, #{skuId}, #{quantity}, 1, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3),
                 #{userId}, #{userId}, 0)
            ON DUPLICATE KEY UPDATE
                quantity = quantity + VALUES(quantity),
                updated_at = CURRENT_TIMESTAMP(3),
                updated_by = #{userId},
                version = version + 1
            """)
    int upsertAdd(@Param("userId") Long userId,
                  @Param("skuId") Long skuId,
                  @Param("quantity") int quantity);

    @Select("SELECT quantity FROM cart_item WHERE user_id = #{userId} AND sku_id = #{skuId}")
    Integer selectQuantity(@Param("userId") Long userId, @Param("skuId") Long skuId);

    @Select("SELECT COUNT(*) FROM cart_item WHERE user_id = #{userId}")
    long countItems(@Param("userId") Long userId);

    @Update("""
            UPDATE cart_item
            SET quantity = #{quantity},
                updated_at = CURRENT_TIMESTAMP(3),
                updated_by = #{userId},
                version = version + 1
            WHERE user_id = #{userId} AND sku_id = #{skuId}
            """)
    int updateQuantity(@Param("userId") Long userId,
                       @Param("skuId") Long skuId,
                       @Param("quantity") int quantity);

    @Update("""
            UPDATE cart_item
            SET selected = #{selected},
                updated_at = CURRENT_TIMESTAMP(3),
                updated_by = #{userId},
                version = version + 1
            WHERE user_id = #{userId} AND sku_id = #{skuId}
            """)
    int updateSelected(@Param("userId") Long userId,
                       @Param("skuId") Long skuId,
                       @Param("selected") int selected);

    @Update("""
            UPDATE cart_item
            SET selected = #{selected},
                updated_at = CURRENT_TIMESTAMP(3),
                updated_by = #{userId},
                version = version + 1
            WHERE user_id = #{userId}
            """)
    int updateAllSelected(@Param("userId") Long userId, @Param("selected") int selected);

    @Delete("DELETE FROM cart_item WHERE user_id = #{userId} AND sku_id = #{skuId}")
    int deleteItem(@Param("userId") Long userId, @Param("skuId") Long skuId);

    @Select("""
            SELECT id, user_id, sku_id, quantity, selected,
                   created_at, updated_at, created_by, updated_by, version
            FROM cart_item
            WHERE user_id = #{userId}
            ORDER BY created_at ASC, id ASC
            """)
    List<CartItem> selectByUser(@Param("userId") Long userId);
}
