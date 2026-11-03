package com.mallagent.module.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mallagent.module.order.entity.OrderItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 订单明细 Mapper。
 */
@Mapper
public interface OrderItemMapper extends BaseMapper<OrderItem> {

    @Select("""
            SELECT id, order_id, sku_id, spu_id, sku_code, spu_name, image_url,
                   spec_text, unit_price, quantity, line_amount, created_at
            FROM order_item
            WHERE order_id = #{orderId}
            ORDER BY sku_id ASC
            """)
    List<OrderItem> selectByOrderId(@Param("orderId") Long orderId);
}
