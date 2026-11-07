package com.mallagent.module.review.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mallagent.module.review.entity.ProductReview;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ProductReviewMapper extends BaseMapper<ProductReview> {

    @Select("""
            SELECT id, user_id, order_no, order_item_id, spu_id, sku_id,
                   spu_name, sku_code, image_url, rating, content, created_at
            FROM product_review
            WHERE order_item_id = #{orderItemId}
            LIMIT 1
            """)
    ProductReview selectByOrderItemId(@Param("orderItemId") Long orderItemId);

    @Select("""
            SELECT COALESCE(AVG(rating), 0)
            FROM product_review
            WHERE spu_id = #{spuId}
            """)
    Double selectAverageRating(@Param("spuId") Long spuId);
}
