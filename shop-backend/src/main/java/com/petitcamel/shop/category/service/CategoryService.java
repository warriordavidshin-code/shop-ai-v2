package com.petitcamel.shop.category.service;

import com.petitcamel.shop.category.domain.Category;
import com.petitcamel.shop.category.dto.CategoryTreeResponse;
import com.petitcamel.shop.category.repository.CategoryRepository;
import com.petitcamel.shop.common.exception.BusinessException;
import com.petitcamel.shop.common.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;

    public CategoryService(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @Transactional(readOnly = true)
    public List<CategoryTreeResponse> getCategoryTree() {
        List<Category> categories = categoryRepository.findByActiveTrueOrderBySortOrderAsc();
        Map<Long, List<Category>> childrenByParent = new HashMap<>();
        List<Category> roots = new ArrayList<>();

        for (Category category : categories) {
            if (category.getParentId() == null) {
                roots.add(category);
            } else {
                childrenByParent.computeIfAbsent(category.getParentId(), ignored -> new ArrayList<>()).add(category);
            }
        }

        roots.sort(Comparator.comparing(Category::getSortOrder).thenComparing(Category::getCategoryId));
        return roots.stream().map(root -> toTree(root, childrenByParent)).toList();
    }

    @Transactional(readOnly = true)
    public Set<Long> resolveCategoryIds(String category) {
        if (category == null || category.isBlank()) {
            return Set.of();
        }

        Category matched = resolveCategory(category.trim());
        List<Category> all = categoryRepository.findByActiveTrueOrderBySortOrderAsc();
        Map<Long, List<Long>> children = new HashMap<>();
        for (Category item : all) {
            if (item.getParentId() != null) {
                children.computeIfAbsent(item.getParentId(), ignored -> new ArrayList<>()).add(item.getCategoryId());
            }
        }

        Set<Long> ids = new HashSet<>();
        collectDescendants(matched.getCategoryId(), children, ids);
        return ids;
    }

    private Category resolveCategory(String category) {
        try {
            Long id = Long.valueOf(category);
            return categoryRepository.findById(id)
                    .filter(Category::getActive)
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "카테고리를 찾을 수 없습니다."));
        } catch (NumberFormatException ignored) {
            return categoryRepository.findBySlug(category)
                    .filter(Category::getActive)
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "카테고리를 찾을 수 없습니다."));
        }
    }

    private void collectDescendants(Long categoryId, Map<Long, List<Long>> children, Set<Long> ids) {
        ids.add(categoryId);
        for (Long childId : children.getOrDefault(categoryId, List.of())) {
            collectDescendants(childId, children, ids);
        }
    }

    private CategoryTreeResponse toTree(Category category, Map<Long, List<Category>> childrenByParent) {
        List<Category> children = new ArrayList<>(
                childrenByParent.getOrDefault(category.getCategoryId(), List.of()));
        children.sort(Comparator.comparing(Category::getSortOrder).thenComparing(Category::getCategoryId));
        return new CategoryTreeResponse(
                category.getCategoryId(),
                category.getName(),
                category.getSlug(),
                children.stream().map(child -> toTree(child, childrenByParent)).toList());
    }
}
