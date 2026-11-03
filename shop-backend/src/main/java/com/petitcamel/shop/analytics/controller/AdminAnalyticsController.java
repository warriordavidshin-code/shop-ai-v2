package com.petitcamel.shop.analytics.controller;

import com.petitcamel.shop.analytics.dto.AnalyticsDtos;
import com.petitcamel.shop.analytics.service.ProfitAnalyticsService;
import com.petitcamel.shop.dashboard.OperationsDashboardService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@RestController
@RequestMapping("/api/admin")
public class AdminAnalyticsController {

    private final ProfitAnalyticsService profitService;
    private final OperationsDashboardService dashboardService;

    public AdminAnalyticsController(ProfitAnalyticsService profitService, OperationsDashboardService dashboardService) {
        this.profitService = profitService;
        this.dashboardService = dashboardService;
    }

    @GetMapping("/analytics/product-profit")
    public AnalyticsDtos.ProductProfitResponse productProfit(
            @RequestParam(required = false) String period,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return profitService.productProfit(profitService.resolvePeriod(period, from, to));
    }

    @GetMapping("/analytics/monthly-profit")
    public AnalyticsDtos.MonthlyProfitResponse monthlyProfit(@RequestParam(required = false) Integer year) {
        return profitService.monthlyProfit(year == null ? LocalDate.now(ZoneId.of("Asia/Seoul")).getYear() : year);
    }

    @GetMapping("/analytics/category-sales")
    public List<AnalyticsDtos.CategorySales> categorySales(
            @RequestParam(required = false) String period,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return profitService.categorySales(profitService.resolvePeriod(period, from, to));
    }

    @GetMapping("/dashboard/operations")
    public OperationsDashboardService.DashboardResponse operations() {
        return dashboardService.operations();
    }
}
