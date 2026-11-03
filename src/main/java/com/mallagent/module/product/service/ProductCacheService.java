package com.mallagent.module.product.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mallagent.module.product.config.ProductCacheProperties;
import com.mallagent.module.product.dto.category.CategoryResponse;
import com.mallagent.module.product.dto.product.ProductDetailResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 商品 Redis 缓存。
 *
 * Key：
 * - mallagent:product:category:tree:v1
 * - mallagent:product:spu:detail:v1:{spuId}
 *
 * 更新策略：写库成功后删除缓存；删除动作在事务提交后执行。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductCacheService {

    private static final String CATEGORY_TREE_KEY = "mallagent:product:category:tree:v1";
    private static final String SPU_DETAIL_KEY_PREFIX = "mallagent:product:spu:detail:v1:";

    private static final TypeReference<List<CategoryResponse>> CATEGORY_TREE_TYPE =
            new TypeReference<>() {
            };

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final ProductCacheProperties properties;

    public Optional<List<CategoryResponse>> getCategoryTree() {
        return read(CATEGORY_TREE_KEY, CATEGORY_TREE_TYPE);
    }

    public void putCategoryTree(List<CategoryResponse> tree) {
        write(CATEGORY_TREE_KEY, tree, properties.getCategoryTreeTtl());
    }

    public Optional<ProductDetailResponse> getSpuDetail(Long spuId) {
        return read(spuDetailKey(spuId), new TypeReference<>() {
        });
    }

    public void putSpuDetail(Long spuId, ProductDetailResponse detail) {
        write(spuDetailKey(spuId), detail, properties.getSpuDetailTtl());
    }

    public void evictCategoryTree() {
        redisTemplate.delete(CATEGORY_TREE_KEY);
    }

    public void evictSpuDetail(Long spuId) {
        if (spuId != null) {
            redisTemplate.delete(spuDetailKey(spuId));
        }
    }

    public void evictSpuDetails(Collection<Long> spuIds) {
        if (spuIds == null || spuIds.isEmpty()) {
            return;
        }
        List<String> keys = spuIds.stream()
                .filter(java.util.Objects::nonNull)
                .distinct()
                .map(this::spuDetailKey)
                .toList();
        if (!keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    public void evictCategoryTreeAfterCommit() {
        afterCommit(this::evictCategoryTree);
    }

    public void evictSpuDetailAfterCommit(Long spuId) {
        afterCommit(() -> evictSpuDetail(spuId));
    }

    public void evictSpuDetailsAfterCommit(Collection<Long> spuIds) {
        afterCommit(() -> evictSpuDetails(spuIds));
    }

    private <T> Optional<T> read(String key, TypeReference<T> type) {
        String json;
        try {
            json = redisTemplate.opsForValue().get(key);
        } catch (Exception ex) {
            log.warn("商品缓存读取失败，降级查库 key={}", key, ex);
            return Optional.empty();
        }
        if (json == null) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(objectMapper.readValue(json, type));
        } catch (JsonProcessingException ex) {
            log.warn("商品缓存反序列化失败，删除缓存 key={}", key, ex);
            try {
                redisTemplate.delete(key);
            } catch (Exception deleteEx) {
                log.warn("删除损坏缓存失败 key={}", key, deleteEx);
            }
            return Optional.empty();
        }
    }

    private void write(String key, Object value, Duration ttl) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(value), ttl);
        } catch (Exception ex) {
            log.warn("商品缓存写入失败，跳过缓存 key={}", key, ex);
        }
    }

    private void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    runSafely(action);
                }
            });
        } else {
            runSafely(action);
        }
    }

    private void runSafely(Runnable action) {
        try {
            action.run();
        } catch (Exception ex) {
            log.warn("商品缓存失效执行失败", ex);
        }
    }

    private String spuDetailKey(Long spuId) {
        return SPU_DETAIL_KEY_PREFIX + spuId;
    }
}
