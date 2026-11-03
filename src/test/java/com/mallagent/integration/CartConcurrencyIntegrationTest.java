package com.mallagent.integration;

import com.mallagent.common.exception.BusinessException;
import com.mallagent.module.cart.dto.cart.AddCartItemRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class CartConcurrencyIntegrationTest extends AbstractCartIntegrationTest {

    @Test
    @DisplayName("并发添加同一 SKU 不丢数量、不产生重复行")
    void concurrentAddSameSku() throws Exception {
        TokenPair admin = adminLogin();
        CartUser user = registerCartUser();
        ProductFixture product = createPublishedProduct(admin.accessToken(), "10.00");

        int threads = 20;
        runConcurrently(threads, index -> cartService.addItem(
                user.userId(),
                UUID.randomUUID().toString(),
                new AddCartItemRequest(product.skuId(), 1)));

        assertThat(cartItemMapper.selectQuantity(user.userId(), product.skuId())).isEqualTo(threads);
        assertThat(cartItemMapper.selectByUser(user.userId())).hasSize(1);
    }

    @Test
    @DisplayName("并发使用同一幂等键只添加一次")
    void concurrentSameIdempotencyKey() throws Exception {
        TokenPair admin = adminLogin();
        CartUser user = registerCartUser();
        ProductFixture product = createPublishedProduct(admin.accessToken(), "10.00");
        String key = UUID.randomUUID().toString();

        runConcurrently(10, index -> cartService.addItem(
                user.userId(),
                key,
                new AddCartItemRequest(product.skuId(), 1)));

        assertThat(cartItemMapper.selectQuantity(user.userId(), product.skuId())).isEqualTo(1);
    }

    @Test
    @DisplayName("并发添加到上限时不会超过单件上限")
    void concurrentAddNeverExceedsCap() throws Exception {
        TokenPair admin = adminLogin();
        CartUser user = registerCartUser();
        ProductFixture product = createPublishedProduct(admin.accessToken(), "10.00");
        cartService.addItem(user.userId(), UUID.randomUUID().toString(),
                new AddCartItemRequest(product.skuId(), 90));

        List<Throwable> failures = new ArrayList<>();
        runConcurrentlyCollecting(20, index -> cartService.addItem(
                user.userId(),
                UUID.randomUUID().toString(),
                new AddCartItemRequest(product.skuId(), 1)), failures);

        assertThat(cartItemMapper.selectQuantity(user.userId(), product.skuId())).isEqualTo(99);
        assertThat(failures).isNotEmpty();
        assertThat(failures).allMatch(failure ->
                failure instanceof BusinessException business && business.getCode() == 13004);
    }

    @Test
    @DisplayName("并发添加不同 SKU 各自累加")
    void concurrentAddDifferentSkus() throws Exception {
        TokenPair admin = adminLogin();
        CartUser user = registerCartUser();
        ProductFixture first = createPublishedProduct(admin.accessToken(), "10.00");
        ProductFixture second = createPublishedProduct(admin.accessToken(), "20.00");

        runConcurrently(20, index -> cartService.addItem(
                user.userId(),
                UUID.randomUUID().toString(),
                new AddCartItemRequest(index % 2 == 0 ? first.skuId() : second.skuId(), 1)));

        assertThat(cartItemMapper.selectQuantity(user.userId(), first.skuId())).isEqualTo(10);
        assertThat(cartItemMapper.selectQuantity(user.userId(), second.skuId())).isEqualTo(10);
        assertThat(cartItemMapper.selectByUser(user.userId())).hasSize(2);
    }

    private void runConcurrently(int threads, ThrowingIntConsumer action) throws Exception {
        List<Throwable> failures = new ArrayList<>();
        runConcurrentlyCollecting(threads, action, failures);
        assertThat(failures).isEmpty();
    }

    private void runConcurrentlyCollecting(int threads,
                                           ThrowingIntConsumer action,
                                           List<Throwable> failures) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < threads; i++) {
                int index = i;
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    start.await(10, TimeUnit.SECONDS);
                    action.accept(index);
                    return null;
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            for (Future<?> future : futures) {
                try {
                    future.get(30, TimeUnit.SECONDS);
                } catch (ExecutionException ex) {
                    failures.add(ex.getCause());
                }
            }
        } finally {
            pool.shutdownNow();
        }
    }

    @FunctionalInterface
    private interface ThrowingIntConsumer {
        void accept(int value) throws Exception;
    }
}
