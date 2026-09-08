package com.petitcamel.shop.category.controller;

import com.petitcamel.shop.category.dto.CategoryTreeResponse;
import com.petitcamel.shop.category.service.CategoryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/categories")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping
    public List<CategoryTreeResponse> getCategories() {
        return categoryService.getCategoryTree();
    }
}
