package com.mallagent.module.order.service;

import com.mallagent.module.order.entity.OrderInfo;
import com.mallagent.module.order.mapper.OrderInfoMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 幂等键冲突后的独立只读查询。
 *
 * <p>使用独立 READ_COMMITTED 事务读取并发事务已提交的订单，避免当前事务快照或行锁等待
 * 导致误判，也避免多个重复请求在唯一索引上互相死锁。</p>
 */
@Service
@RequiredArgsConstructor
public class OrderIdempotencyLookupService {

    private final OrderInfoMapper orderInfoMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW,
            isolation = Isolation.READ_COMMITTED, readOnly = true)
    public OrderInfo findByIdempotencyKey(Long userId, String idempotencyKey) {
        return orderInfoMapper.selectByUserIdAndIdempotencyKey(userId, idempotencyKey);
    }
}
