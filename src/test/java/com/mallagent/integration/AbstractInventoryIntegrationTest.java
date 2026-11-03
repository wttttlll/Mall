package com.mallagent.integration;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mallagent.module.inventory.api.InventoryStockApi;
import com.mallagent.module.inventory.api.dto.StockCommand;
import com.mallagent.module.inventory.api.dto.StockItem;
import com.mallagent.module.inventory.api.dto.StockRestoreCommand;
import com.mallagent.module.inventory.dto.admin.StockAdjustRequest;
import com.mallagent.module.inventory.dto.admin.StockResponse;
import com.mallagent.module.inventory.entity.StockFlow;
import com.mallagent.module.inventory.entity.StockSku;
import com.mallagent.module.inventory.mapper.StockFlowMapper;
import com.mallagent.module.inventory.mapper.StockSkuMapper;
import com.mallagent.module.inventory.service.InventoryAdminService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * 库存管理端测试基类，复用商品测试夹具。
 */
abstract class AbstractInventoryIntegrationTest extends AbstractProductIntegrationTest {

    @Autowired
    protected StockSkuMapper stockSkuMapper;

    @Autowired
    protected StockFlowMapper stockFlowMapper;

    @Autowired
    protected InventoryAdminService inventoryAdminService;

    @Autowired
    protected InventoryStockApi inventoryStockApi;

    protected Long createInventorySku(TokenPair admin) throws Exception {
        String suffix = uniqueSuffix();
        Long categoryId = createCategory(admin.accessToken(), "inv_cat_" + suffix, 0L, 1);
        Long spuId = createSpu(admin.accessToken(), "inv_spu_" + suffix, categoryId, null,
                "https://example.com/inv-main-" + suffix + ".png");
        return createSku(admin.accessToken(), spuId, "INV-SKU-" + suffix, "10.00", List.of());
    }

    protected MvcResult adjust(String token, String idempotencyKey, Long skuId, int delta, String remark)
            throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("skuId", skuId);
        body.put("delta", delta);
        body.put("remark", remark);

        MockHttpServletRequestBuilder builder = post("/api/v1/admin/inventory/stocks/adjust")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body));
        if (idempotencyKey != null) {
            builder.header("Idempotency-Key", idempotencyKey);
        }
        return performAuthenticated(builder, token);
    }

    protected StockResponse adjustDirect(String idempotencyKey, Long skuId, int delta, String remark) {
        return inventoryAdminService.adjust(idempotencyKey, new StockAdjustRequest(skuId, delta, remark));
    }

    protected void lock(String bizNo, List<StockItem> items) {
        inventoryStockApi.lock(new StockCommand(bizNo, items));
    }

    protected void deduct(String bizNo, List<StockItem> items) {
        inventoryStockApi.deduct(new StockCommand(bizNo, items));
    }

    protected void release(String bizNo, List<StockItem> items) {
        inventoryStockApi.release(new StockCommand(bizNo, items));
    }

    protected void restore(String bizNo, String refBizNo, List<StockItem> items) {
        inventoryStockApi.restore(new StockRestoreCommand(bizNo, refBizNo, items));
    }

    protected List<StockFlow> orderLockFlows(String bizNo) {
        return stockFlowMapper.selectOrderLockFlows(bizNo);
    }

    protected List<StockFlow> terminalFlows(String bizNo) {
        return stockFlowMapper.selectTerminalFlows(bizNo);
    }

    protected List<StockFlow> restoreFlows(String bizNo) {
        return stockFlowMapper.selectRestoreFlows(bizNo);
    }

    protected StockSku stock(Long skuId) {
        return stockSkuMapper.selectBySkuId(skuId);
    }

    protected List<StockFlow> flows(Long skuId) {
        return stockFlowMapper.selectList(new LambdaQueryWrapper<StockFlow>()
                .eq(StockFlow::getSkuId, skuId)
                .orderByAsc(StockFlow::getId));
    }

    protected TokenPair operatorToken(TokenPair admin) throws Exception {
        String username = uniqueUsername();
        register(username);
        assignRole(admin.accessToken(), username, "OPERATOR");
        return login(username, TEST_PASSWORD);
    }

    protected MvcResult performAuthenticated(MockHttpServletRequestBuilder builder, String token) throws Exception {
        if (token != null) {
            builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return mockMvc.perform(builder).andReturn();
    }
}
