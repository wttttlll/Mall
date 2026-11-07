package com.mallagent.module.aftersale.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mallagent.module.aftersale.entity.AfterSaleOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface AfterSaleOrderMapper extends BaseMapper<AfterSaleOrder> {

    @Select("""
            SELECT id, after_sale_no, order_no, user_id, status, refund_amount,
                   reason, description, reject_reason, reviewed_by, reviewed_at,
                   completed_by, completed_at, created_at, updated_at
            FROM after_sale_order
            WHERE after_sale_no = #{afterSaleNo}
            """)
    AfterSaleOrder selectByAfterSaleNo(@Param("afterSaleNo") String afterSaleNo);

    @Select("""
            SELECT id, after_sale_no, order_no, user_id, status, refund_amount,
                   reason, description, reject_reason, reviewed_by, reviewed_at,
                   completed_by, completed_at, created_at, updated_at
            FROM after_sale_order
            WHERE after_sale_no = #{afterSaleNo}
            FOR UPDATE
            """)
    AfterSaleOrder selectByAfterSaleNoForUpdate(@Param("afterSaleNo") String afterSaleNo);

    @Select("""
            SELECT id, after_sale_no, order_no, user_id, status, refund_amount,
                   reason, description, reject_reason, reviewed_by, reviewed_at,
                   completed_by, completed_at, created_at, updated_at
            FROM after_sale_order
            WHERE order_no = #{orderNo}
              AND status IN ('APPLIED', 'APPROVED', 'COMPLETED')
            ORDER BY id DESC
            LIMIT 1
            """)
    AfterSaleOrder selectActiveByOrderNo(@Param("orderNo") String orderNo);

    @Update("""
            UPDATE after_sale_order
            SET status = 'APPROVED',
                reviewed_by = #{operatorId},
                reviewed_at = #{reviewedAt},
                updated_at = CURRENT_TIMESTAMP(3)
            WHERE after_sale_no = #{afterSaleNo} AND status = 'APPLIED'
            """)
    int markApproved(@Param("afterSaleNo") String afterSaleNo,
                     @Param("operatorId") Long operatorId,
                     @Param("reviewedAt") LocalDateTime reviewedAt);

    @Update("""
            UPDATE after_sale_order
            SET status = 'REJECTED',
                reject_reason = #{rejectReason},
                reviewed_by = #{operatorId},
                reviewed_at = #{reviewedAt},
                updated_at = CURRENT_TIMESTAMP(3)
            WHERE after_sale_no = #{afterSaleNo} AND status = 'APPLIED'
            """)
    int markRejected(@Param("afterSaleNo") String afterSaleNo,
                     @Param("operatorId") Long operatorId,
                     @Param("rejectReason") String rejectReason,
                     @Param("reviewedAt") LocalDateTime reviewedAt);

    @Update("""
            UPDATE after_sale_order
            SET status = 'COMPLETED',
                completed_by = #{operatorId},
                completed_at = #{completedAt},
                updated_at = CURRENT_TIMESTAMP(3)
            WHERE after_sale_no = #{afterSaleNo} AND status = 'APPROVED'
            """)
    int markCompleted(@Param("afterSaleNo") String afterSaleNo,
                      @Param("operatorId") Long operatorId,
                      @Param("completedAt") LocalDateTime completedAt);
}
