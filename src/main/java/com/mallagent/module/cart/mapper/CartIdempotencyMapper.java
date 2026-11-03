package com.mallagent.module.cart.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mallagent.module.cart.entity.CartIdempotency;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 购物车添加幂等记录 Mapper。
 */
@Mapper
public interface CartIdempotencyMapper extends BaseMapper<CartIdempotency> {

    /**
     * 抢占幂等键。唯一索引保证同一用户同一 key 只有一条记录。
     */
    @Insert("""
            INSERT INTO cart_idempotency
                (user_id, idempotency_key, request_hash, sku_id, quantity, claim_token, created_at)
            VALUES
                (#{userId}, #{key}, #{requestHash}, #{skuId}, #{quantity}, #{claimToken}, CURRENT_TIMESTAMP(3))
            ON DUPLICATE KEY UPDATE id = id
            """)
    int claim(@Param("userId") Long userId,
              @Param("key") String key,
              @Param("requestHash") String requestHash,
              @Param("skuId") Long skuId,
              @Param("quantity") int quantity,
              @Param("claimToken") String claimToken);

    @Select("""
            SELECT id, user_id, idempotency_key, request_hash, sku_id, quantity, claim_token, created_at
            FROM cart_idempotency
            WHERE user_id = #{userId} AND idempotency_key = #{key}
            """)
    CartIdempotency selectByKey(@Param("userId") Long userId, @Param("key") String key);
}
