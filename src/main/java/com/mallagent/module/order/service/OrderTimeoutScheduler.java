package com.mallagent.module.order.service;

import com.mallagent.module.order.config.OrderProperties;
import com.mallagent.module.order.mapper.OrderInfoMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 待支付订单超时扫描。
 *
 * <p>每次扫描只取一批候选订单，逐单在独立事务中锁定订单行并重新确认状态和创建时间，
 * 因此支持应用重启恢复、定时重复扫描以及多实例同时扫描而不会重复释放库存。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderTimeoutScheduler {

    private final OrderInfoMapper orderInfoMapper;
    private final OrderService orderService;
    private final OrderProperties properties;

    @Scheduled(
            fixedDelayString = "${mallagent.order.timeout-scan-interval-ms:60000}",
            initialDelayString = "${mallagent.order.timeout-scan-initial-delay-ms:10000}")
    public void scheduledScan() {
        try {
            scanExpiredOrders();
        } catch (Exception ex) {
            log.error("订单超时扫描失败", ex);
        }
    }

    /**
     * 执行一次扫描，返回本次成功取消的订单数。测试可直接调用。
     */
    public int scanExpiredOrders() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime cutoff = now.minusMinutes(properties.getTimeoutMinutes());
        List<String> orderNos = orderInfoMapper.selectExpiredPendingOrderNos(
                cutoff, properties.getTimeoutBatchSize());

        int cancelled = 0;
        for (String orderNo : orderNos) {
            try {
                if (orderService.cancelExpired(orderNo, now)) {
                    cancelled++;
                }
            } catch (Exception ex) {
                // 单笔失败不阻塞其他订单；下一轮扫描会重新尝试。
                log.warn("订单超时取消失败 orderNo={}", orderNo, ex);
            }
        }
        if (cancelled > 0) {
            log.info("订单超时取消完成 count={}", cancelled);
        }
        return cancelled;
    }
}
