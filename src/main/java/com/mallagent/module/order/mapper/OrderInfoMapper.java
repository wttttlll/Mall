package com.mallagent.module.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mallagent.module.order.entity.OrderInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单主记录 Mapper。
 */
@Mapper
public interface OrderInfoMapper extends BaseMapper<OrderInfo> {

    @Select("""
            SELECT id, order_no, user_id, status, total_amount, item_count,
                   receiver_name, receiver_phone, receiver_province, receiver_city,
                   receiver_district, receiver_detail_address, receiver_postal_code,
                   idempotency_key, paid_at, cancelled_at, created_at, updated_at,
                   created_by, updated_by, version
            FROM order_info
            WHERE order_no = #{orderNo}
            """)
    OrderInfo selectByOrderNo(@Param("orderNo") String orderNo);

    /**
     * 锁定订单行。支付与取消共用该互斥点。
     */
    @Select("""
            SELECT id, order_no, user_id, status, total_amount, item_count,
                   receiver_name, receiver_phone, receiver_province, receiver_city,
                   receiver_district, receiver_detail_address, receiver_postal_code,
                   idempotency_key, paid_at, cancelled_at, created_at, updated_at,
                   created_by, updated_by, version
            FROM order_info
            WHERE order_no = #{orderNo}
            FOR UPDATE
            """)
    OrderInfo selectByOrderNoForUpdate(@Param("orderNo") String orderNo);

    @Select("""
            SELECT id, order_no, user_id, status, total_amount, item_count,
                   receiver_name, receiver_phone, receiver_province, receiver_city,
                   receiver_district, receiver_detail_address, receiver_postal_code,
                   idempotency_key, paid_at, cancelled_at, created_at, updated_at,
                   created_by, updated_by, version
            FROM order_info
            WHERE user_id = #{userId} AND idempotency_key = #{idempotencyKey}
            """)
    OrderInfo selectByUserIdAndIdempotencyKey(@Param("userId") Long userId,
                                              @Param("idempotencyKey") String idempotencyKey);

    @Update("""
            UPDATE order_info
            SET total_amount = #{totalAmount},
                item_count = #{itemCount},
                version = version + 1,
                updated_at = CURRENT_TIMESTAMP(3),
                updated_by = #{userId}
            WHERE id = #{id} AND status = 'PENDING_PAYMENT'
            """)
    int updateTotals(@Param("id") Long id,
                     @Param("totalAmount") BigDecimal totalAmount,
                     @Param("itemCount") int itemCount,
                     @Param("userId") Long userId);

    @Update("""
            UPDATE order_info
            SET status = 'PAID',
                paid_at = #{paidAt},
                version = version + 1,
                updated_at = CURRENT_TIMESTAMP(3),
                updated_by = #{userId}
            WHERE order_no = #{orderNo} AND status = 'PENDING_PAYMENT'
            """)
    int markPaid(@Param("orderNo") String orderNo,
                 @Param("userId") Long userId,
                 @Param("paidAt") LocalDateTime paidAt);

    @Update("""
            UPDATE order_info
            SET status = 'CANCELLED',
                cancelled_at = #{cancelledAt},
                version = version + 1,
                updated_at = CURRENT_TIMESTAMP(3),
                updated_by = #{userId}
            WHERE order_no = #{orderNo} AND status = 'PENDING_PAYMENT'
            """)
    int markCancelled(@Param("orderNo") String orderNo,
                      @Param("userId") Long userId,
                      @Param("cancelledAt") LocalDateTime cancelledAt);
}
