package com.mallagent.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

class CartIntegrationTest extends AbstractCartIntegrationTest {

    @Test
    @DisplayName("添加商品并查询购物车，金额与缓存正确")
    void addItemAndQueryCart() throws Exception {
        TokenPair admin = adminLogin();
        CartUser user = registerCartUser();
        ProductFixture product = createPublishedProduct(admin.accessToken(), "88.80");

        MvcResult result = addCartItem(user.token(), UUID.randomUUID().toString(), product.skuId(), 2);
        assertThat(result.getResponse().getStatus()).isEqualTo(200);

        JsonNode body = body(result);
        assertThat(body.path("code").asInt()).isZero();
        JsonNode item = itemBySku(body.path("data"), product.skuId());
        assertThat(item.path("price").decimalValue()).isEqualByComparingTo("88.80");
        assertThat(item.path("quantity").asInt()).isEqualTo(2);
        assertThat(item.path("selected").asBoolean()).isTrue();
        assertThat(item.path("amount").decimalValue()).isEqualByComparingTo("177.60");
        assertThat(item.path("available").asBoolean()).isTrue();

        JsonNode summary = body.path("data").path("summary");
        assertThat(summary.path("itemCount").asInt()).isEqualTo(1);
        assertThat(summary.path("totalQuantity").asInt()).isEqualTo(2);
        assertThat(summary.path("selectedQuantity").asInt()).isEqualTo(2);
        assertThat(summary.path("selectedAmount").decimalValue()).isEqualByComparingTo("177.60");
        assertThat(summary.path("totalAmount").decimalValue()).isEqualByComparingTo("177.60");
    }

    @Test
    @DisplayName("相同幂等键重复添加不会重复累加")
    void sameIdempotencyKeyDoesNotDoubleAdd() throws Exception {
        TokenPair admin = adminLogin();
        CartUser user = registerCartUser();
        ProductFixture product = createPublishedProduct(admin.accessToken(), "10.00");
        String key = UUID.randomUUID().toString();

        addCartItem(user.token(), key, product.skuId(), 2);
        MvcResult second = addCartItem(user.token(), key, product.skuId(), 2);

        assertThat(second.getResponse().getStatus()).isEqualTo(200);
        assertThat(body(second).path("data").path("summary").path("totalQuantity").asInt()).isEqualTo(2);
        assertThat(cartItemMapper.selectQuantity(user.userId(), product.skuId())).isEqualTo(2);
    }

    @Test
    @DisplayName("不同幂等键重复添加合并数量")
    void differentIdempotencyKeysMergeQuantity() throws Exception {
        TokenPair admin = adminLogin();
        CartUser user = registerCartUser();
        ProductFixture product = createPublishedProduct(admin.accessToken(), "10.00");

        addCartItem(user.token(), UUID.randomUUID().toString(), product.skuId(), 2);
        MvcResult second = addCartItem(user.token(), UUID.randomUUID().toString(), product.skuId(), 3);

        assertThat(body(second).path("data").path("summary").path("totalQuantity").asInt()).isEqualTo(5);
        assertThat(cartItemMapper.selectQuantity(user.userId(), product.skuId())).isEqualTo(5);
    }

    @Test
    @DisplayName("相同幂等键不同请求返回冲突")
    void sameIdempotencyKeyWithDifferentPayloadConflicts() throws Exception {
        TokenPair admin = adminLogin();
        CartUser user = registerCartUser();
        ProductFixture product = createPublishedProduct(admin.accessToken(), "10.00");
        String key = UUID.randomUUID().toString();

        addCartItem(user.token(), key, product.skuId(), 2);
        MvcResult conflict = addCartItem(user.token(), key, product.skuId(), 3);

        assertThat(conflict.getResponse().getStatus()).isEqualTo(409);
        assertThat(body(conflict).path("code").asInt()).isEqualTo(13010);
        assertThat(cartItemMapper.selectQuantity(user.userId(), product.skuId())).isEqualTo(2);
    }

    @Test
    @DisplayName("缺少 Idempotency-Key 返回参数错误")
    void missingIdempotencyKeyReturnsBadRequest() throws Exception {
        TokenPair admin = adminLogin();
        CartUser user = registerCartUser();
        ProductFixture product = createPublishedProduct(admin.accessToken(), "10.00");

        MvcResult result = addCartItem(user.token(), null, product.skuId(), 1);

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(body(result).path("code").asInt()).isEqualTo(13009);
        assertThat(cartItemMapper.selectQuantity(user.userId(), product.skuId())).isNull();
    }

    @Test
    @DisplayName("添加不存在的 SKU 返回 404")
    void addingMissingSkuIsRejected() throws Exception {
        CartUser user = registerCartUser();

        MvcResult result = addCartItem(user.token(), UUID.randomUUID().toString(), 999_999_999L, 1);

        assertThat(result.getResponse().getStatus()).isEqualTo(404);
        assertThat(body(result).path("code").asInt()).isEqualTo(13001);
        assertThat(cartItemMapper.selectByUser(user.userId())).isEmpty();
    }

    @Test
    @DisplayName("添加已下架商品返回 409")
    void addingOffShelfProductIsRejected() throws Exception {
        TokenPair admin = adminLogin();
        CartUser user = registerCartUser();
        ProductFixture product = createPublishedProduct(admin.accessToken(), "10.00");
        assertThat(offShelf(admin.accessToken(), product.spuId()).getResponse().getStatus()).isEqualTo(200);

        MvcResult result = addCartItem(user.token(), UUID.randomUUID().toString(), product.skuId(), 1);

        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(body(result).path("code").asInt()).isEqualTo(13006);
        assertThat(cartItemMapper.selectByUser(user.userId())).isEmpty();
    }

    @Test
    @DisplayName("修改数量与删除条目")
    void updateQuantityAndDeleteItem() throws Exception {
        TokenPair admin = adminLogin();
        CartUser user = registerCartUser();
        ProductFixture product = createPublishedProduct(admin.accessToken(), "10.00");
        addCartItem(user.token(), UUID.randomUUID().toString(), product.skuId(), 2);

        MvcResult updated = updateCartItem(user.token(), product.skuId(), 5);
        assertThat(updated.getResponse().getStatus()).isEqualTo(200);
        assertThat(body(updated).path("data").path("summary").path("totalQuantity").asInt()).isEqualTo(5);

        MvcResult deleted = deleteCartItem(user.token(), product.skuId());
        assertThat(deleted.getResponse().getStatus()).isEqualTo(200);
        assertThat(body(deleted).path("data").path("items").size()).isZero();
        assertThat(cartItemMapper.selectQuantity(user.userId(), product.skuId())).isNull();

        MvcResult deletedAgain = deleteCartItem(user.token(), product.skuId());
        assertThat(deletedAgain.getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("单条勾选与全选影响汇总")
    void selectionAffectsSummary() throws Exception {
        TokenPair admin = adminLogin();
        CartUser user = registerCartUser();
        ProductFixture first = createPublishedProduct(admin.accessToken(), "10.00");
        ProductFixture second = createPublishedProduct(admin.accessToken(), "20.00");

        addCartItem(user.token(), UUID.randomUUID().toString(), first.skuId(), 1);
        addCartItem(user.token(), UUID.randomUUID().toString(), second.skuId(), 2);

        MvcResult unselectAll = selectAllCartItems(user.token(), false);
        JsonNode unselected = body(unselectAll).path("data").path("summary");
        assertThat(unselected.path("selectedQuantity").asInt()).isZero();
        assertThat(unselected.path("selectedAmount").decimalValue()).isEqualByComparingTo("0.00");

        MvcResult selectFirst = setCartItemSelected(user.token(), first.skuId(), true);
        JsonNode selected = body(selectFirst).path("data").path("summary");
        assertThat(selected.path("selectedQuantity").asInt()).isEqualTo(1);
        assertThat(selected.path("selectedAmount").decimalValue()).isEqualByComparingTo("10.00");

        MvcResult selectAll = selectAllCartItems(user.token(), true);
        JsonNode allSelected = body(selectAll).path("data").path("summary");
        assertThat(allSelected.path("selectedQuantity").asInt()).isEqualTo(3);
        assertThat(allSelected.path("selectedAmount").decimalValue()).isEqualByComparingTo("50.00");
    }

    @Test
    @DisplayName("商品改价后购物车返回当前价格")
    void priceChangeIsReflected() throws Exception {
        TokenPair admin = adminLogin();
        CartUser user = registerCartUser();
        ProductFixture product = createPublishedProduct(admin.accessToken(), "88.80");
        addCartItem(user.token(), UUID.randomUUID().toString(), product.skuId(), 1);

        assertThat(offShelf(admin.accessToken(), product.spuId()).getResponse().getStatus()).isEqualTo(200);
        MvcResult updated = putJson("/api/v1/admin/product/spus/" + product.spuId()
                        + "/skus/" + product.skuId(),
                Map.of("price", new BigDecimal("99.90"),
                        "marketPrice", new BigDecimal("199.00"),
                        "imageUrl", "https://example.com/sku.png",
                        "specValueIds", List.of(),
                        "status", 1),
                admin.accessToken());
        assertThat(updated.getResponse().getStatus()).isEqualTo(200);
        assertThat(publish(admin.accessToken(), product.spuId()).getResponse().getStatus()).isEqualTo(200);

        JsonNode cart = cart(user.token());
        JsonNode item = itemBySku(cart.path("data"), product.skuId());
        assertThat(item.path("price").decimalValue()).isEqualByComparingTo("99.90");
        assertThat(item.path("available").asBoolean()).isTrue();
        assertThat(item.path("amount").decimalValue()).isEqualByComparingTo("99.90");
    }

    @Test
    @DisplayName("商品下架后条目保留但不可购买")
    void offShelfItemIsUnavailable() throws Exception {
        TokenPair admin = adminLogin();
        CartUser user = registerCartUser();
        ProductFixture product = createPublishedProduct(admin.accessToken(), "10.00");
        addCartItem(user.token(), UUID.randomUUID().toString(), product.skuId(), 2);

        assertThat(offShelf(admin.accessToken(), product.spuId()).getResponse().getStatus()).isEqualTo(200);

        JsonNode cart = cart(user.token());
        JsonNode item = itemBySku(cart.path("data"), product.skuId());
        assertThat(item.path("available").asBoolean()).isFalse();
        assertThat(item.path("unavailableReason").asText()).contains("下架");
        assertThat(item.path("amount").decimalValue()).isEqualByComparingTo("0.00");
        assertThat(cart.path("data").path("summary").path("selectedAmount").decimalValue())
                .isEqualByComparingTo("0.00");
        assertThat(cart.path("data").path("summary").path("totalAmount").decimalValue())
                .isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("SKU 删除后条目标记为不存在")
    void deletedSkuIsUnavailable() throws Exception {
        TokenPair admin = adminLogin();
        CartUser user = registerCartUser();
        ProductFixture product = createPublishedProduct(admin.accessToken(), "10.00");
        addCartItem(user.token(), UUID.randomUUID().toString(), product.skuId(), 1);

        assertThat(offShelf(admin.accessToken(), product.spuId()).getResponse().getStatus()).isEqualTo(200);
        MvcResult deleted = deleteJson("/api/v1/admin/product/spus/" + product.spuId()
                + "/skus/" + product.skuId(), admin.accessToken());
        assertThat(deleted.getResponse().getStatus()).isEqualTo(200);

        JsonNode cart = cart(user.token());
        JsonNode item = itemBySku(cart.path("data"), product.skuId());
        assertThat(item.path("available").asBoolean()).isFalse();
        assertThat(item.path("unavailableReason").asText()).contains("SKU 不存在");
        assertThat(item.path("amount").decimalValue()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("用户只能操作自己的购物车")
    void userIsolation() throws Exception {
        TokenPair admin = adminLogin();
        CartUser first = registerCartUser();
        CartUser second = registerCartUser();
        ProductFixture product = createPublishedProduct(admin.accessToken(), "10.00");
        addCartItem(first.token(), UUID.randomUUID().toString(), product.skuId(), 2);

        JsonNode secondCart = cart(second.token());
        assertThat(secondCart.path("data").path("items").size()).isZero();

        MvcResult deleteByOther = deleteCartItem(second.token(), product.skuId());
        assertThat(deleteByOther.getResponse().getStatus()).isEqualTo(200);

        JsonNode firstCart = cart(first.token());
        assertThat(itemBySku(firstCart.path("data"), product.skuId()).path("quantity").asInt()).isEqualTo(2);
    }

    @Test
    @DisplayName("未登录访问购物车返回 401")
    void unauthenticatedReturnsUnauthorized() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/cart")).andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("超过单件数量上限时回滚")
    void quantityCapIsEnforced() throws Exception {
        TokenPair admin = adminLogin();
        CartUser user = registerCartUser();
        ProductFixture product = createPublishedProduct(admin.accessToken(), "10.00");
        addCartItem(user.token(), UUID.randomUUID().toString(), product.skuId(), 99);

        MvcResult exceeded = addCartItem(user.token(), UUID.randomUUID().toString(), product.skuId(), 2);

        assertThat(exceeded.getResponse().getStatus()).isEqualTo(400);
        assertThat(body(exceeded).path("code").asInt()).isEqualTo(13004);
        assertThat(cartItemMapper.selectQuantity(user.userId(), product.skuId())).isEqualTo(99);
    }

    @Test
    @DisplayName("幂等重试不受商品后续下架影响")
    void idempotentRetryAfterOffShelf() throws Exception {
        TokenPair admin = adminLogin();
        CartUser user = registerCartUser();
        ProductFixture product = createPublishedProduct(admin.accessToken(), "10.00");
        String key = UUID.randomUUID().toString();
        addCartItem(user.token(), key, product.skuId(), 2);

        assertThat(offShelf(admin.accessToken(), product.spuId()).getResponse().getStatus()).isEqualTo(200);

        MvcResult retry = addCartItem(user.token(), key, product.skuId(), 2);

        assertThat(retry.getResponse().getStatus()).isEqualTo(200);
        assertThat(body(retry).path("data").path("summary").path("totalQuantity").asInt()).isEqualTo(2);
        assertThat(cartItemMapper.selectQuantity(user.userId(), product.skuId())).isEqualTo(2);
    }

    @Test
    @DisplayName("客户端传入价格和金额不会生效")
    void clientSuppliedPriceIsIgnored() throws Exception {
        TokenPair admin = adminLogin();
        CartUser user = registerCartUser();
        ProductFixture product = createPublishedProduct(admin.accessToken(), "88.80");

        MvcResult result = addCartItemWithBody(user.token(), UUID.randomUUID().toString(),
                Map.of("skuId", product.skuId(),
                        "quantity", 2,
                        "price", new BigDecimal("0.01"),
                        "amount", new BigDecimal("0.02")));

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode item = itemBySku(body(result).path("data"), product.skuId());
        assertThat(item.path("price").decimalValue()).isEqualByComparingTo("88.80");
        assertThat(item.path("amount").decimalValue()).isEqualByComparingTo("177.60");
        assertThat(item.path("quantity").asInt()).isEqualTo(2);
    }

    @Test
    @DisplayName("数量超限回滚时幂等记录一并回滚，允许同 key 重试")
    void idempotencyRecordRollsBackOnCapFailure() throws Exception {
        TokenPair admin = adminLogin();
        CartUser user = registerCartUser();
        ProductFixture product = createPublishedProduct(admin.accessToken(), "10.00");
        addCartItem(user.token(), UUID.randomUUID().toString(), product.skuId(), 98);

        String key = UUID.randomUUID().toString();
        MvcResult exceeded = addCartItem(user.token(), key, product.skuId(), 2);
        assertThat(exceeded.getResponse().getStatus()).isEqualTo(400);
        assertThat(body(exceeded).path("code").asInt()).isEqualTo(13004);

        assertThat(updateCartItem(user.token(), product.skuId(), 50).getResponse().getStatus()).isEqualTo(200);
        MvcResult retry = addCartItem(user.token(), key, product.skuId(), 2);

        assertThat(retry.getResponse().getStatus()).isEqualTo(200);
        assertThat(cartItemMapper.selectQuantity(user.userId(), product.skuId())).isEqualTo(52);
    }

    private JsonNode itemBySku(JsonNode cartData, Long skuId) {
        for (JsonNode item : cartData.path("items")) {
            if (item.path("skuId").asLong() == skuId) {
                return item;
            }
        }
        throw new AssertionError("购物车中不存在 SKU: " + skuId);
    }
}
