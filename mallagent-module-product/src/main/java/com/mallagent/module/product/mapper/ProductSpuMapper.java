package com.mallagent.module.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mallagent.module.product.entity.ProductSpu;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;

@Mapper
public interface ProductSpuMapper extends BaseMapper<ProductSpu> {

    /**
     * 用户端可见商品分页：上架、分类启用、品牌为空或启用、至少一个启用 SKU。
     */
    @Select("""
            <script>
            SELECT s.*
            FROM product_spu s
            JOIN product_category c ON c.id = s.category_id
                AND c.deleted = 0
                AND c.status = 1
            LEFT JOIN product_brand b ON b.id = s.brand_id
                AND b.deleted = 0
            WHERE s.deleted = 0
              AND s.status = 'ON_SHELF'
              AND (s.brand_id IS NULL OR (b.id IS NOT NULL AND b.status = 1))
              AND EXISTS (
                  SELECT 1
                  FROM product_sku sku
                  WHERE sku.spu_id = s.id
                    AND sku.deleted = 0
                    AND sku.status = 1
              )
              <if test="categoryIds != null and categoryIds.size() > 0">
                AND s.category_id IN
                <foreach collection="categoryIds" item="categoryId" open="(" separator="," close=")">
                  #{categoryId}
                </foreach>
              </if>
              <if test="brandId != null">
                AND s.brand_id = #{brandId}
              </if>
              <if test="keyword != null and keyword != ''">
                AND (s.name LIKE CONCAT('%', #{keyword}, '%')
                     OR s.subtitle LIKE CONCAT('%', #{keyword}, '%'))
              </if>
            ORDER BY s.sort DESC, s.id DESC
            </script>
            """)
    IPage<ProductSpu> selectVisiblePage(Page<ProductSpu> page,
                                        @Param("categoryIds") Collection<Long> categoryIds,
                                        @Param("brandId") Long brandId,
                                        @Param("keyword") String keyword);
}
