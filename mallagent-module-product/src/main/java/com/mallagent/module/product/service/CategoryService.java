package com.mallagent.module.product.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mallagent.common.error.CommonErrorCode;
import com.mallagent.common.exception.BusinessException;
import com.mallagent.module.product.dto.category.CategoryCreateRequest;
import com.mallagent.module.product.dto.category.CategoryResponse;
import com.mallagent.module.product.dto.category.CategoryUpdateRequest;
import com.mallagent.module.product.entity.ProductCategory;
import com.mallagent.module.product.error.ProductErrorCode;
import com.mallagent.module.product.mapper.ProductCategoryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 商品分类管理。分类最多 3 级，父分类创建后不可修改。
 */
@Service
@RequiredArgsConstructor
public class CategoryService {

    private static final int MAX_LEVEL = 3;

    private final ProductCategoryMapper categoryMapper;
    private final ProductCacheService cacheService;

    public List<CategoryResponse> adminTree() {
        return buildTree(loadCategories(false));
    }

    public List<CategoryResponse> userTree() {
        Optional<List<CategoryResponse>> cached = cacheService.getCategoryTree();
        if (cached.isPresent()) {
            return cached.get();
        }
        List<CategoryResponse> tree = buildTree(loadCategories(true));
        cacheService.putCategoryTree(tree);
        return tree;
    }

    public CategoryResponse get(Long id) {
        return toResponse(requireCategory(id), List.of());
    }

    @Transactional
    public CategoryResponse create(CategoryCreateRequest request) {
        String name = request.name().trim();
        Long parentId = request.parentId() == null ? 0L : request.parentId();
        int level = 1;

        if (parentId != 0) {
            ProductCategory parent = requireCategory(parentId);
            if (parent.getLevel() == null || parent.getLevel() >= MAX_LEVEL) {
                throw new BusinessException(ProductErrorCode.CATEGORY_LEVEL_EXCEEDED);
            }
            level = parent.getLevel() + 1;
        }

        ensureNameUnique(parentId, name, null);

        ProductCategory category = new ProductCategory();
        category.setParentId(parentId);
        category.setName(name);
        category.setLevel(level);
        category.setSort(request.sort() == null ? 0 : request.sort());
        category.setStatus(request.status() == null ? 1 : request.status());
        category.setIconUrl(normalize(request.iconUrl()));

        try {
            categoryMapper.insert(category);
        } catch (DuplicateKeyException ex) {
            throw new BusinessException(ProductErrorCode.CATEGORY_NAME_EXISTS);
        }
        cacheService.evictCategoryTreeAfterCommit();
        return toResponse(category, List.of());
    }

    @Transactional
    public CategoryResponse update(Long id, CategoryUpdateRequest request) {
        ProductCategory category = requireCategory(id);
        String name = request.name().trim();
        ensureNameUnique(category.getParentId(), name, id);

        category.setName(name);
        if (request.sort() != null) {
            category.setSort(request.sort());
        }
        if (request.status() != null) {
            category.setStatus(request.status());
        }
        if (request.iconUrl() != null) {
            category.setIconUrl(normalize(request.iconUrl()));
        }

        int updated = categoryMapper.updateById(category);
        if (updated == 0) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "分类信息已被修改，请刷新后重试");
        }
        cacheService.evictCategoryTreeAfterCommit();
        cacheService.evictSpuDetailsAfterCommit(categoryMapper.selectActiveSpuIds(id));
        return toResponse(category, List.of());
    }

    @Transactional
    public void delete(Long id) {
        requireCategory(id);

        long children = categoryMapper.selectCount(new LambdaQueryWrapper<ProductCategory>()
                .eq(ProductCategory::getParentId, id));
        if (children > 0) {
            throw new BusinessException(ProductErrorCode.CATEGORY_HAS_CHILDREN);
        }

        if (categoryMapper.countActiveSpus(id) > 0) {
            throw new BusinessException(ProductErrorCode.CATEGORY_IN_USE);
        }

        categoryMapper.deleteById(id);
        cacheService.evictCategoryTreeAfterCommit();
    }

    /**
     * 返回分类自身及全部后代分类 ID，用于用户端按分类筛选商品。
     */
    public List<Long> collectSelfAndDescendantIds(Long categoryId) {
        requireCategory(categoryId);

        List<ProductCategory> all = categoryMapper.selectList(null);
        Map<Long, List<Long>> childrenByParent = all.stream()
                .collect(Collectors.groupingBy(
                        category -> category.getParentId() == null ? 0L : category.getParentId(),
                        Collectors.mapping(ProductCategory::getId, Collectors.toList())));

        Set<Long> result = new LinkedHashSet<>();
        Deque<Long> queue = new ArrayDeque<>();
        queue.add(categoryId);
        while (!queue.isEmpty()) {
            Long current = queue.poll();
            if (result.add(current)) {
                queue.addAll(childrenByParent.getOrDefault(current, List.of()));
            }
        }
        return new ArrayList<>(result);
    }

    private List<ProductCategory> loadCategories(boolean onlyEnabled) {
        LambdaQueryWrapper<ProductCategory> wrapper = new LambdaQueryWrapper<ProductCategory>()
                .orderByAsc(ProductCategory::getSort)
                .orderByAsc(ProductCategory::getId);
        if (onlyEnabled) {
            wrapper.eq(ProductCategory::getStatus, 1);
        }
        return categoryMapper.selectList(wrapper);
    }

    private List<CategoryResponse> buildTree(List<ProductCategory> categories) {
        Map<Long, List<ProductCategory>> childrenByParent = categories.stream()
                .collect(Collectors.groupingBy(
                        category -> category.getParentId() == null ? 0L : category.getParentId(),
                        LinkedHashMap::new,
                        Collectors.toList()));
        return buildChildren(0L, childrenByParent);
    }

    private List<CategoryResponse> buildChildren(Long parentId,
                                                 Map<Long, List<ProductCategory>> childrenByParent) {
        return childrenByParent.getOrDefault(parentId, List.of()).stream()
                .map(category -> toResponse(category, buildChildren(category.getId(), childrenByParent)))
                .toList();
    }

    private void ensureNameUnique(Long parentId, String name, Long excludeId) {
        LambdaQueryWrapper<ProductCategory> wrapper = new LambdaQueryWrapper<ProductCategory>()
                .eq(ProductCategory::getParentId, parentId)
                .eq(ProductCategory::getName, name);
        if (excludeId != null) {
            wrapper.ne(ProductCategory::getId, excludeId);
        }
        if (categoryMapper.selectCount(wrapper) > 0) {
            throw new BusinessException(ProductErrorCode.CATEGORY_NAME_EXISTS);
        }
    }

    private ProductCategory requireCategory(Long id) {
        ProductCategory category = categoryMapper.selectById(id);
        if (category == null) {
            throw new BusinessException(ProductErrorCode.CATEGORY_NOT_FOUND);
        }
        return category;
    }

    private CategoryResponse toResponse(ProductCategory category, List<CategoryResponse> children) {
        return new CategoryResponse(
                category.getId(),
                category.getParentId(),
                category.getName(),
                category.getLevel(),
                category.getSort(),
                category.getStatus(),
                category.getIconUrl(),
                children
        );
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
