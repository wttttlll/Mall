package com.mallagent.module.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mallagent.module.payment.entity.PaymentOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 模拟支付单 Mapper。
 */
@Mapper
public interface PaymentOrderMapper extends BaseMapper<PaymentOrder> {

    @Select("""
            SELECT id, payment_no, order_no, user_id, amount, channel, status, paid_at, created_at
            FROM payment_order
            WHERE order_no = #{orderNo}
            """)
    PaymentOrder selectByOrderNo(@Param("orderNo") String orderNo);
}
