package com.mallagent.integration;

import com.mallagent.module.inventory.api.dto.StockItem;
import com.mallagent.module.inventory.dto.admin.StockAdjustRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证捕获 DuplicateKeyException 后，在已有外层事务中仍能正常提交且不重复变更。
 */
class InventoryTransactionBoundaryIntegrationTest extends AbstractInventoryIntegrationTest {

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    @DisplayName("管理端重复请求在外层事务中幂等并正常提交")
    void adminDuplicateInsideOuterTransactionCommits() throws Exception {
        TokenPair admin = adminLogin();
        Long skuId = createInventorySku(admin);
        String key = "outer-admin-" + UUID.randomUUID();
        StockAdjustRequest request = new StockAdjustRequest(skuId, 5, "外层事务");

        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        transaction.executeWithoutResult(status -> {
            inventoryAdminService.adjust(key, request);
            inventoryAdminService.adjust(key, request);
        });

        assertThat(stock(skuId).getAvailableStock()).isEqualTo(5);
        assertThat(flows(skuId)).hasSize(1);
    }

    @Test
    @DisplayName("lock 重复请求在外层事务中幂等并正常提交")
    void lockDuplicateInsideOuterTransactionCommits() throws Exception {
        TokenPair admin = adminLogin();
        Long skuId = createInventorySku(admin);
        adjustDirect("outer-lock-seed-" + UUID.randomUUID(), skuId, 10, "初始库存");
        String bizNo = "ORDER-" + UUID.randomUUID();
        List<StockItem> items = List.of(new StockItem(skuId, 2));

        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.executeWithoutResult(status -> {
            lock(bizNo, items);
            lock(bizNo, items);
        });

        assertThat(stock(skuId).getAvailableStock()).isEqualTo(8);
        assertThat(stock(skuId).getLockedStock()).isEqualTo(2);
        assertThat(orderLockFlows(bizNo)).hasSize(1);
    }

    @Test
    @DisplayName("deduct 重复请求在外层事务中幂等并正常提交")
    void deductDuplicateInsideOuterTransactionCommits() throws Exception {
        TokenPair admin = adminLogin();
        Long skuId = createInventorySku(admin);
        adjustDirect("outer-deduct-seed-" + UUID.randomUUID(), skuId, 10, "初始库存");
        String bizNo = "ORDER-" + UUID.randomUUID();
        List<StockItem> items = List.of(new StockItem(skuId, 2));
        lock(bizNo, items);

        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.executeWithoutResult(status -> {
            deduct(bizNo, items);
            deduct(bizNo, items);
        });

        assertThat(stock(skuId).getAvailableStock()).isEqualTo(8);
        assertThat(stock(skuId).getLockedStock()).isZero();
        assertThat(terminalFlows(bizNo)).hasSize(1);
    }

    @Test
    @DisplayName("release 重复请求在外层事务中幂等并正常提交")
    void releaseDuplicateInsideOuterTransactionCommits() throws Exception {
        TokenPair admin = adminLogin();
        Long skuId = createInventorySku(admin);
        adjustDirect("outer-release-seed-" + UUID.randomUUID(), skuId, 10, "初始库存");
        String bizNo = "ORDER-" + UUID.randomUUID();
        List<StockItem> items = List.of(new StockItem(skuId, 2));
        lock(bizNo, items);

        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.executeWithoutResult(status -> {
            release(bizNo, items);
            release(bizNo, items);
        });

        assertThat(stock(skuId).getAvailableStock()).isEqualTo(10);
        assertThat(stock(skuId).getLockedStock()).isZero();
        assertThat(terminalFlows(bizNo)).hasSize(1);
    }

    @Test
    @DisplayName("restore 重复请求在外层事务中幂等并正常提交")
    void restoreDuplicateInsideOuterTransactionCommits() throws Exception {
        TokenPair admin = adminLogin();
        Long skuId = createInventorySku(admin);
        adjustDirect("outer-restore-seed-" + UUID.randomUUID(), skuId, 10, "初始库存");
        String orderNo = "ORDER-" + UUID.randomUUID();
        List<StockItem> items = List.of(new StockItem(skuId, 2));
        lock(orderNo, items);
        deduct(orderNo, items);
        String aftersaleNo = "AFTER-" + UUID.randomUUID();

        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.executeWithoutResult(status -> {
            restore(aftersaleNo, orderNo, items);
            restore(aftersaleNo, orderNo, items);
        });

        assertThat(stock(skuId).getAvailableStock()).isEqualTo(10);
        assertThat(stock(skuId).getLockedStock()).isZero();
        assertThat(restoreFlows(aftersaleNo)).hasSize(1);
    }

    @Test
    @DisplayName("外层事务失败时库存与流水整体回滚")
    void outerTransactionFailureRollsBackInventory() throws Exception {
        TokenPair admin = adminLogin();
        Long skuId = createInventorySku(admin);
        adjustDirect("outer-rollback-seed-" + UUID.randomUUID(), skuId, 10, "初始库存");
        String orderNo = "ORDER-" + UUID.randomUUID();
        List<StockItem> items = List.of(new StockItem(skuId, 2));

        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            lock(orderNo, items);
            deduct(orderNo, items);
            throw new IllegalStateException("force rollback");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(stock(skuId).getAvailableStock()).isEqualTo(10);
        assertThat(stock(skuId).getLockedStock()).isZero();
        assertThat(orderLockFlows(orderNo)).isEmpty();
        assertThat(terminalFlows(orderNo)).isEmpty();
        assertThat(flows(skuId)).hasSize(1);
    }
}
