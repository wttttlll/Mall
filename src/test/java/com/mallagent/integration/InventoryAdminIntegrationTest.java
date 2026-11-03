package com.mallagent.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.mallagent.module.inventory.entity.StockFlow;
import com.mallagent.module.inventory.entity.StockSku;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class InventoryAdminIntegrationTest extends AbstractInventoryIntegrationTest {

    @Test
    @DisplayName("首次正向调整初始化库存并记录完整流水")
    void firstPositiveAdjustInitializesStock() throws Exception {
        TokenPair admin = adminLogin();
        Long skuId = createInventorySku(admin);
        String key = "init-" + UUID.randomUUID();

        MvcResult result = adjust(admin.accessToken(), key, skuId, 10, "首次入库");
        assertThat(result.getResponse().getStatus()).isEqualTo(200);

        JsonNode data = body(result).path("data");
        assertThat(data.path("skuId").asLong()).isEqualTo(skuId);
        assertThat(data.path("availableStock").asInt()).isEqualTo(10);
        assertThat(data.path("lockedStock").asInt()).isZero();
        assertThat(data.path("totalStock").asInt()).isEqualTo(10);

        StockSku stock = stock(skuId);
        assertThat(stock).isNotNull();
        assertThat(stock.getAvailableStock()).isEqualTo(10);
        assertThat(stock.getLockedStock()).isZero();

        List<StockFlow> flows = flows(skuId);
        assertThat(flows).hasSize(1);
        StockFlow flow = flows.get(0);
        assertThat(flow.getBizType()).isEqualTo("ADMIN_ADJUST");
        assertThat(flow.getBizNo()).isEqualTo(key);
        assertThat(flow.getQuantity()).isEqualTo(10);
        assertThat(flow.getChangeAvailable()).isEqualTo(10);
        assertThat(flow.getChangeLocked()).isZero();
        assertThat(flow.getAvailableBefore()).isZero();
        assertThat(flow.getAvailableAfter()).isEqualTo(10);
        assertThat(flow.getLockedBefore()).isZero();
        assertThat(flow.getLockedAfter()).isZero();
        assertThat(flow.getRemark()).isEqualTo("首次入库");

        JsonNode detail = body(getJson("/api/v1/admin/inventory/stocks/" + skuId,
                admin.accessToken())).path("data");
        assertThat(detail.path("availableStock").asInt()).isEqualTo(10);
        assertThat(detail.path("lockedStock").asInt()).isZero();
    }

    @Test
    @DisplayName("正向和负向调整累积，流水前后值准确")
    void positiveAndNegativeAdjustments() throws Exception {
        TokenPair admin = adminLogin();
        Long skuId = createInventorySku(admin);

        assertThat(adjust(admin.accessToken(), "add-" + UUID.randomUUID(), skuId, 10, "入库").getResponse().getStatus())
                .isEqualTo(200);
        assertThat(adjust(admin.accessToken(), "add-" + UUID.randomUUID(), skuId, 5, "补货").getResponse().getStatus())
                .isEqualTo(200);
        assertThat(adjust(admin.accessToken(), "sub-" + UUID.randomUUID(), skuId, -3, "出库").getResponse().getStatus())
                .isEqualTo(200);

        StockSku stock = stock(skuId);
        assertThat(stock.getAvailableStock()).isEqualTo(12);
        assertThat(stock.getLockedStock()).isZero();

        List<StockFlow> flows = flows(skuId);
        assertThat(flows).hasSize(3);
        assertThat(flows.get(0).getAvailableBefore()).isZero();
        assertThat(flows.get(0).getAvailableAfter()).isEqualTo(10);
        assertThat(flows.get(1).getAvailableBefore()).isEqualTo(10);
        assertThat(flows.get(1).getAvailableAfter()).isEqualTo(15);
        assertThat(flows.get(2).getAvailableBefore()).isEqualTo(15);
        assertThat(flows.get(2).getAvailableAfter()).isEqualTo(12);

        int availableDelta = flows.stream().mapToInt(StockFlow::getChangeAvailable).sum();
        int lockedDelta = flows.stream().mapToInt(StockFlow::getChangeLocked).sum();
        assertThat(availableDelta).isEqualTo(stock.getAvailableStock());
        assertThat(lockedDelta).isEqualTo(stock.getLockedStock());
    }

    @Test
    @DisplayName("delta=0 拒绝")
    void deltaZeroIsRejected() throws Exception {
        TokenPair admin = adminLogin();
        Long skuId = createInventorySku(admin);

        MvcResult result = adjust(admin.accessToken(), "zero-" + UUID.randomUUID(), skuId, 0, "零调整");

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(body(result).path("code").asInt()).isEqualTo(14005);
        assertThat(stock(skuId)).isNull();
        assertThat(flows(skuId)).isEmpty();
    }

    @Test
    @DisplayName("调整后为负库存拒绝")
    void negativeBelowZeroIsRejected() throws Exception {
        TokenPair admin = adminLogin();
        Long skuId = createInventorySku(admin);
        assertThat(adjust(admin.accessToken(), "plus-" + UUID.randomUUID(), skuId, 5, "入库")
                .getResponse().getStatus()).isEqualTo(200);

        MvcResult result = adjust(admin.accessToken(), "minus-" + UUID.randomUUID(), skuId, -6, "超减");

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(body(result).path("code").asInt()).isEqualTo(14006);
        assertThat(stock(skuId).getAvailableStock()).isEqualTo(5);
        assertThat(flows(skuId)).hasSize(1);
    }

    @Test
    @DisplayName("不存在 SKU 拒绝")
    void missingSkuIsRejected() throws Exception {
        TokenPair admin = adminLogin();

        MvcResult result = adjust(admin.accessToken(), "missing-" + UUID.randomUUID(), 999_999_999L, 1, null);

        assertThat(result.getResponse().getStatus()).isEqualTo(404);
        assertThat(body(result).path("code").asInt()).isEqualTo(14008);
        assertThat(flows(999_999_999L)).isEmpty();
    }

    @Test
    @DisplayName("缺少 Idempotency-Key 拒绝")
    void missingIdempotencyKeyIsRejected() throws Exception {
        TokenPair admin = adminLogin();
        Long skuId = createInventorySku(admin);

        MvcResult result = adjust(admin.accessToken(), null, skuId, 10, "无幂等键");

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(body(result).path("code").asInt()).isEqualTo(14007);
        assertThat(stock(skuId)).isNull();
    }

    @Test
    @DisplayName("相同 Key 相同请求幂等成功")
    void sameKeySameRequestIsIdempotent() throws Exception {
        TokenPair admin = adminLogin();
        Long skuId = createInventorySku(admin);
        String key = "idem-" + UUID.randomUUID();

        assertThat(adjust(admin.accessToken(), key, skuId, 10, "入库").getResponse().getStatus())
                .isEqualTo(200);
        MvcResult second = adjust(admin.accessToken(), key, skuId, 10, "入库");

        assertThat(second.getResponse().getStatus()).isEqualTo(200);
        assertThat(stock(skuId).getAvailableStock()).isEqualTo(10);
        assertThat(flows(skuId)).hasSize(1);
    }

    @Test
    @DisplayName("相同 Key 不同 delta 冲突")
    void sameKeyDifferentDeltaConflicts() throws Exception {
        TokenPair admin = adminLogin();
        Long skuId = createInventorySku(admin);
        String key = "delta-" + UUID.randomUUID();
        adjust(admin.accessToken(), key, skuId, 10, "入库");

        MvcResult conflict = adjust(admin.accessToken(), key, skuId, 11, "入库");

        assertThat(conflict.getResponse().getStatus()).isEqualTo(409);
        assertThat(body(conflict).path("code").asInt()).isEqualTo(14004);
        assertThat(stock(skuId).getAvailableStock()).isEqualTo(10);
        assertThat(flows(skuId)).hasSize(1);
    }

    @Test
    @DisplayName("相同 Key 不同 remark 冲突")
    void sameKeyDifferentRemarkConflicts() throws Exception {
        TokenPair admin = adminLogin();
        Long skuId = createInventorySku(admin);
        String key = "remark-" + UUID.randomUUID();
        adjust(admin.accessToken(), key, skuId, 10, "备注A");

        MvcResult conflict = adjust(admin.accessToken(), key, skuId, 10, "备注B");

        assertThat(conflict.getResponse().getStatus()).isEqualTo(409);
        assertThat(body(conflict).path("code").asInt()).isEqualTo(14004);
        assertThat(stock(skuId).getAvailableStock()).isEqualTo(10);
        assertThat(flows(skuId)).hasSize(1);
    }

    @Test
    @DisplayName("相同 Key 不同 SKU 冲突，不能依赖 biz_no + sku_id 唯一")
    void sameKeyDifferentSkuConflicts() throws Exception {
        TokenPair admin = adminLogin();
        Long firstSku = createInventorySku(admin);
        Long secondSku = createInventorySku(admin);
        String key = "sku-" + UUID.randomUUID();
        adjust(admin.accessToken(), key, firstSku, 10, "入库");

        MvcResult conflict = adjust(admin.accessToken(), key, secondSku, 10, "入库");

        assertThat(conflict.getResponse().getStatus()).isEqualTo(409);
        assertThat(body(conflict).path("code").asInt()).isEqualTo(14004);
        assertThat(stock(firstSku).getAvailableStock()).isEqualTo(10);
        assertThat(stock(secondSku)).isNull();
        assertThat(flows(secondSku)).isEmpty();
    }

    @Test
    @DisplayName("失败调整回滚，不留下流水")
    void failedAdjustRollsBackFlow() throws Exception {
        TokenPair admin = adminLogin();
        Long skuId = createInventorySku(admin);

        MvcResult result = adjust(admin.accessToken(), "uninit-" + UUID.randomUUID(), skuId, -1, "未初始化扣减");

        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(body(result).path("code").asInt()).isEqualTo(14002);
        assertThat(stock(skuId)).isNull();
        assertThat(flows(skuId)).isEmpty();
    }

    @Test
    @DisplayName("OPERATOR 可查询但不可调整")
    void operatorCanQueryButCannotAdjust() throws Exception {
        TokenPair admin = adminLogin();
        Long skuId = createInventorySku(admin);
        adjust(admin.accessToken(), "op-init-" + UUID.randomUUID(), skuId, 10, "入库");
        TokenPair operator = operatorToken(admin);

        assertThat(getJson("/api/v1/admin/inventory/stocks", operator.accessToken())
                .getResponse().getStatus()).isEqualTo(200);
        assertThat(getJson("/api/v1/admin/inventory/stocks/" + skuId, operator.accessToken())
                .getResponse().getStatus()).isEqualTo(200);
        assertThat(getJson("/api/v1/admin/inventory/flows", operator.accessToken())
                .getResponse().getStatus()).isEqualTo(200);

        MvcResult denied = adjust(operator.accessToken(), "op-adjust-" + UUID.randomUUID(), skuId, 1, "越权");
        assertThat(denied.getResponse().getStatus()).isEqualTo(403);
        assertThat(stock(skuId).getAvailableStock()).isEqualTo(10);
    }

    @Test
    @DisplayName("库存与流水分页筛选")
    void pageStocksAndFlows() throws Exception {
        TokenPair admin = adminLogin();
        Long firstSku = createInventorySku(admin);
        Long secondSku = createInventorySku(admin);
        String firstKey = "page-" + UUID.randomUUID();
        String secondKey = "page-" + UUID.randomUUID();
        adjust(admin.accessToken(), firstKey, firstSku, 10, "A");
        adjust(admin.accessToken(), secondKey, secondSku, 20, "B");

        JsonNode stockPage = body(getJson(
                "/api/v1/admin/inventory/stocks?pageNum=1&pageSize=1", admin.accessToken())).path("data");
        assertThat(stockPage.path("total").asLong()).isGreaterThanOrEqualTo(2);
        assertThat(stockPage.path("list").size()).isEqualTo(1);

        JsonNode firstStock = body(getJson(
                "/api/v1/admin/inventory/stocks?pageNum=1&pageSize=10&skuId=" + firstSku,
                admin.accessToken())).path("data");
        assertThat(firstStock.path("total").asLong()).isEqualTo(1);
        assertThat(firstStock.path("list").get(0).path("skuId").asLong()).isEqualTo(firstSku);

        JsonNode flowPage = body(getJson(
                "/api/v1/admin/inventory/flows?pageNum=1&pageSize=10&skuId=" + firstSku
                        + "&bizType=ADMIN_ADJUST&bizNo=" + firstKey,
                admin.accessToken())).path("data");
        assertThat(flowPage.path("total").asLong()).isEqualTo(1);
        assertThat(flowPage.path("list").get(0).path("availableAfter").asInt()).isEqualTo(10);

        MvcResult invalidType = getJson(
                "/api/v1/admin/inventory/flows?bizType=NOT_A_TYPE", admin.accessToken());
        assertThat(invalidType.getResponse().getStatus()).isEqualTo(400);

        MvcResult missingStock = getJson(
                "/api/v1/admin/inventory/stocks/999999999", admin.accessToken());
        assertThat(missingStock.getResponse().getStatus()).isEqualTo(404);
        assertThat(body(missingStock).path("code").asInt()).isEqualTo(14001);
    }
}
