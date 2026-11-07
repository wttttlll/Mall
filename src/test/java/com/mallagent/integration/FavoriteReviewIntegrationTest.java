package com.mallagent.integration;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.mallagent.module.favorite.entity.UserFavorite;
import com.mallagent.module.favorite.mapper.UserFavoriteMapper;
import com.mallagent.module.review.entity.ProductReview;
import com.mallagent.module.review.mapper.ProductReviewMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

class FavoriteReviewIntegrationTest extends AbstractOrderIntegrationTest {

    @Autowired
    private UserFavoriteMapper userFavoriteMapper;

    @Autowired
    private ProductReviewMapper productReviewMapper;

    @Test
    @DisplayName("收藏、重复收藏、取消收藏幂等且不同用户数据隔离")
    void favoriteIsIdempotentAndUserIsolated() throws Exception {
        TokenPair admin = adminLogin();
        OrderUser first = registerOrderUser();
        OrderUser second = registerOrderUser();
        OrderProduct product = createOrderProduct(admin.accessToken(), "20.00", 10);

        MvcResult added = addFavorite(first.token(), product.spuId());
        assertThat(added.getResponse().getStatus()).isEqualTo(200);
        assertThat(body(added).path("data").path("favorite").asBoolean()).isTrue();
        assertThat(body(added).path("data").path("spuId").asLong()).isEqualTo(product.spuId());

        MvcResult repeated = addFavorite(first.token(), product.spuId());
        assertThat(repeated.getResponse().getStatus()).isEqualTo(200);
        assertThat(userFavoriteMapper.selectCount(new LambdaQueryWrapper<UserFavorite>()
                .eq(UserFavorite::getUserId, first.userId())
                .eq(UserFavorite::getSpuId, product.spuId()))).isEqualTo(1);

        JsonNode status = body(getJson("/api/v1/favorites/status?spuIds=" + product.spuId(),
                first.token()));
        assertThat(status.path("data").path("favoriteSpuIds").get(0).asLong())
                .isEqualTo(product.spuId());

        JsonNode firstList = body(getJson("/api/v1/favorites", first.token()));
        assertThat(firstList.path("data").path("total").asLong()).isEqualTo(1);
        assertThat(firstList.path("data").path("list").get(0).path("spuName").asText())
                .isNotBlank();

        JsonNode secondListBefore = body(getJson("/api/v1/favorites", second.token()));
        assertThat(secondListBefore.path("data").path("total").asLong()).isZero();
        assertThat(addFavorite(second.token(), product.spuId()).getResponse().getStatus())
                .isEqualTo(200);

        MvcResult removed = removeFavorite(first.token(), product.spuId());
        assertThat(removed.getResponse().getStatus()).isEqualTo(200);
        assertThat(body(removed).path("data").path("favorite").asBoolean()).isFalse();
        assertThat(body(getJson("/api/v1/favorites", first.token()))
                .path("data").path("total").asLong()).isZero();
        assertThat(body(getJson("/api/v1/favorites", second.token()))
                .path("data").path("total").asLong()).isEqualTo(1);

        // 重复取消同样幂等。
        assertThat(removeFavorite(first.token(), product.spuId()).getResponse().getStatus())
                .isEqualTo(200);
    }

    @Test
    @DisplayName("商品下架后收藏记录保留并标记不可用")
    void unavailableFavoriteKeepsRecordWithReason() throws Exception {
        TokenPair admin = adminLogin();
        OrderUser user = registerOrderUser();
        OrderProduct product = createOrderProduct(admin.accessToken(), "20.00", 10);
        assertThat(addFavorite(user.token(), product.spuId()).getResponse().getStatus()).isEqualTo(200);

        assertThat(offShelf(admin.accessToken(), product.spuId()).getResponse().getStatus())
                .isEqualTo(200);

        JsonNode list = body(getJson("/api/v1/favorites", user.token())).path("data").path("list");
        assertThat(list).hasSize(1);
        assertThat(list.get(0).path("available").asBoolean()).isFalse();
        assertThat(list.get(0).path("status").asText()).isEqualTo("OFF_SHELF");
        assertThat(list.get(0).path("unavailableReason").asText()).contains("下架");

        assertThat(removeFavorite(user.token(), product.spuId()).getResponse().getStatus())
                .isEqualTo(200);
    }

    @Test
    @DisplayName("已完成订单可以评价，重复评价只保留一条")
    void completedOrderCanBeReviewedIdempotently() throws Exception {
        TokenPair admin = adminLogin();
        OrderUser user = registerOrderUser();
        OrderProduct product = createOrderProduct(admin.accessToken(), "20.00", 10);
        Long addressId = createAddress(user.token());
        String orderNo = completedOrder(admin, user, product, addressId);
        assertThat(putJson("/api/v1/users/me", Map.of("nickname", "评测用户"), user.token())
                .getResponse().getStatus()).isEqualTo(200);

        MvcResult created = review(user.token(), orderNo, product.skuId(), 5, "商品很好，物流也快");
        assertThat(created.getResponse().getStatus()).isEqualTo(201);
        JsonNode firstReview = body(created).path("data");
        long reviewId = firstReview.path("id").asLong();
        assertThat(firstReview.path("rating").asInt()).isEqualTo(5);
        assertThat(firstReview.path("spuId").asLong()).isEqualTo(product.spuId());

        MvcResult repeated = review(user.token(), orderNo, product.skuId(), 3, "重复提交不应新增");
        assertThat(repeated.getResponse().getStatus()).isEqualTo(201);
        assertThat(body(repeated).path("data").path("id").asLong()).isEqualTo(reviewId);
        assertThat(productReviewMapper.selectCount(new LambdaQueryWrapper<ProductReview>()
                .eq(ProductReview::getOrderNo, orderNo))).isEqualTo(1);

        JsonNode publicReviews = body(getJson("/api/v1/products/" + product.spuId() + "/reviews",
                user.token()));
        assertThat(publicReviews.path("data").path("reviewCount").asLong()).isEqualTo(1);
        assertThat(publicReviews.path("data").path("averageRating").decimalValue())
                .isEqualByComparingTo("5.0");
        JsonNode publicReview = publicReviews.path("data").path("list").get(0);
        assertThat(publicReview.path("rating").asInt()).isEqualTo(5);
        assertThat(publicReview.path("displayName").asText()).isEqualTo("评**");
        assertThat(publicReview.has("userId")).isFalse();
        assertThat(publicReview.has("username")).isFalse();

        JsonNode mine = body(getJson("/api/v1/reviews/mine?orderNo=" + orderNo, user.token()));
        assertThat(mine.path("data").path("total").asLong()).isEqualTo(1);
        assertThat(mine.path("data").path("list").get(0).path("content").asText())
                .isEqualTo("商品很好，物流也快");
    }

    @Test
    @DisplayName("未完成订单或非购买用户不能评价，资料只能修改当前用户")
    void reviewRequiresCompletedPurchaseAndProfileIsolation() throws Exception {
        TokenPair admin = adminLogin();
        OrderUser owner = registerOrderUser();
        OrderUser stranger = registerOrderUser();
        OrderProduct product = createOrderProduct(admin.accessToken(), "20.00", 10);
        Long addressId = createAddress(owner.token());
        assertThat(addCartItem(owner.token(), product.skuId(), 1).getResponse().getStatus())
                .isEqualTo(200);
        String orderNo = orderNo(createOrder(owner.token(), "order-" + UUID.randomUUID(), addressId));

        MvcResult pendingReview = review(owner.token(), orderNo, product.skuId(), 5, "未完成");
        assertThat(pendingReview.getResponse().getStatus()).isEqualTo(409);
        assertThat(body(pendingReview).path("code").asInt()).isEqualTo(20003);
        assertThat(pay(owner.token(), orderNo).getResponse().getStatus()).isEqualTo(200);

        MvcResult paidReview = review(owner.token(), orderNo, product.skuId(), 5, "未完成");
        assertThat(paidReview.getResponse().getStatus()).isEqualTo(409);
        assertThat(body(paidReview).path("code").asInt()).isEqualTo(20003);

        assertThat(ship(admin.accessToken(), orderNo, "顺丰速运", "SF-REVIEW").getResponse().getStatus())
                .isEqualTo(200);
        assertThat(confirmReceipt(owner.token(), orderNo).getResponse().getStatus()).isEqualTo(200);

        MvcResult strangerReview = review(stranger.token(), orderNo, product.skuId(), 5, "不是我的订单");
        assertThat(strangerReview.getResponse().getStatus()).isEqualTo(409);
        assertThat(body(strangerReview).path("code").asInt()).isEqualTo(20003);
        assertThat(productReviewMapper.selectCount(new LambdaQueryWrapper<ProductReview>()
                .eq(ProductReview::getOrderNo, orderNo))).isZero();

        assertThat(putJson("/api/v1/users/me", Map.of("nickname", "订单本人"), owner.token())
                .getResponse().getStatus()).isEqualTo(200);
        String strangerNickname = body(getJson("/api/v1/users/me", stranger.token()))
                .path("data").path("nickname").asText(null);
        assertThat(strangerNickname).isNotEqualTo("订单本人");
    }

    private MvcResult addFavorite(String token, Long spuId) throws Exception {
        return mockMvc.perform(post("/api/v1/favorites/" + spuId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)).andReturn();
    }

    private MvcResult removeFavorite(String token, Long spuId) throws Exception {
        return deleteJson("/api/v1/favorites/" + spuId, token);
    }

    private MvcResult review(String token, String orderNo, Long skuId, int rating, String content)
            throws Exception {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("orderNo", orderNo);
        request.put("skuId", skuId);
        request.put("rating", rating);
        request.put("content", content);
        return postJson("/api/v1/reviews", request, token);
    }

    private String completedOrder(TokenPair admin,
                                  OrderUser user,
                                  OrderProduct product,
                                  Long addressId) throws Exception {
        assertThat(addCartItem(user.token(), product.skuId(), 1).getResponse().getStatus())
                .isEqualTo(200);
        String orderNo = orderNo(createOrder(user.token(), "order-" + UUID.randomUUID(), addressId));
        assertThat(pay(user.token(), orderNo).getResponse().getStatus()).isEqualTo(200);
        assertThat(ship(admin.accessToken(), orderNo, "顺丰速运", "SF-" + UUID.randomUUID())
                .getResponse().getStatus()).isEqualTo(200);
        assertThat(confirmReceipt(user.token(), orderNo).getResponse().getStatus()).isEqualTo(200);
        return orderNo;
    }

    private MvcResult ship(String token, String orderNo, String company, String trackingNo)
            throws Exception {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("shippingCompany", company);
        request.put("trackingNo", trackingNo);
        return postJson("/api/v1/admin/orders/" + orderNo + "/ship", request, token);
    }

    private MvcResult confirmReceipt(String token, String orderNo) throws Exception {
        MockHttpServletRequestBuilder builder = post("/api/v1/orders/" + orderNo + "/confirm-receipt")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        return mockMvc.perform(builder).andReturn();
    }
}
