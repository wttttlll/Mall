package com.mallagent.module.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mallagent.module.payment.entity.PaymentRefund;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface PaymentRefundMapper extends BaseMapper<PaymentRefund> {

    @Select("""
            SELECT id, refund_no, after_sale_no, order_no, payment_no, user_id,
                   amount, channel, status, refunded_at, created_at
            FROM payment_refund
            WHERE after_sale_no = #{afterSaleNo}
            LIMIT 1
            """)
    PaymentRefund selectByAfterSaleNo(@Param("afterSaleNo") String afterSaleNo);

    @Select("""
            SELECT id, refund_no, after_sale_no, order_no, payment_no, user_id,
                   amount, channel, status, refunded_at, created_at
            FROM payment_refund
            WHERE order_no = #{orderNo}
            LIMIT 1
            """)
    PaymentRefund selectByOrderNo(@Param("orderNo") String orderNo);
}
