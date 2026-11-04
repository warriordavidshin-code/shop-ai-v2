package com.petitcamel.shop.reco.controller;

import com.petitcamel.shop.common.service.AuditLogService;
import com.petitcamel.shop.reco.service.CatalogSnapshotService;
import com.petitcamel.shop.reco.service.PopularityService;
import com.petitcamel.shop.reco.service.ProductFeatureService;
import com.petitcamel.shop.reco.service.RecoAdminService;
import com.petitcamel.shop.reco.service.RecoSettingsService;
import com.petitcamel.shop.reco.service.RecoStatsService;
import com.petitcamel.shop.reco.service.RecommendationService;
import com.petitcamel.shop.security.MemberPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** 관리자 AI 추천관리. 계산은 모두 규칙 기반이며, 발주·가격 변경은 하지 않고 결정만 기록합니다. */
@RestController
@RequestMapping("/api/admin/recommendations")
public class AdminRecommendationController {

    private final RecoSettingsService settingsService;
    private final RecoAdminService adminService;
    private final RecoStatsService statsService;
    private final PopularityService popularityService;
    private final ProductFeatureService featureService;
    private final RecommendationService recommendationService;
    private final CatalogSnapshotService catalog;
    private final AuditLogService auditLogService;

    public AdminRecommendationController(RecoSettingsService settingsService, RecoAdminService adminService,
                                         RecoStatsService statsService, PopularityService popularityService,
                                         ProductFeatureService featureService,
                                         RecommendationService recommendationService, CatalogSnapshotService catalog,
                                         AuditLogService auditLogService) {
        this.settingsService = settingsService;
        this.adminService = adminService;
        this.statsService = statsService;
        this.popularityService = popularityService;
        this.featureService = featureService;
        this.recommendationService = recommendationService;
        this.catalog = catalog;
        this.auditLogService = auditLogService;
    }

    public record AnalyzeRequest(Boolean forceRefresh, Boolean allowPaid) {
    }

    public record FromAnalysisRequest(Long analysisId) {
    }

    @GetMapping("/settings")
    public RecoSettingsService.Settings settings() {
        return settingsService.settings();
    }

    @PutMapping("/settings")
    public RecoSettingsService.Settings updateSettings(@AuthenticationPrincipal MemberPrincipal principal,
                                                       @RequestBody RecoSettingsService.Settings body) {
        RecoSettingsService.Settings saved = settingsService.update(body, principal.getMemberId());
        recommendationService.clearCache();
        auditLogService.record(principal.getMemberId(), "RECO_SETTINGS_UPDATE", "RECO_SETTINGS", "1",
                "enabled=" + saved.enabled() + ", weights=" + saved.weights());
        return saved;
    }

    @GetMapping("/rules")
    public List<RecoSettingsService.Rule> rules() {
        return settingsService.rules();
    }

    @PostMapping("/rules")
    public RecoSettingsService.Rule createRule(@AuthenticationPrincipal MemberPrincipal principal,
                                               @RequestBody RecoSettingsService.RuleRequest body) {
        RecoSettingsService.Rule rule = settingsService.createRule(body, principal.getMemberId());
        afterRuleChange(principal, "RECO_RULE_CREATE", rule);
        return rule;
    }

    @PutMapping("/rules/{id}")
    public RecoSettingsService.Rule updateRule(@AuthenticationPrincipal MemberPrincipal principal,
                                               @PathVariable Long id,
                                               @RequestBody RecoSettingsService.RuleRequest body) {
        RecoSettingsService.Rule rule = settingsService.updateRule(id, body);
        afterRuleChange(principal, "RECO_RULE_UPDATE", rule);
        return rule;
    }

    @DeleteMapping("/rules/{id}")
    public Map<String, Boolean> deactivateRule(@AuthenticationPrincipal MemberPrincipal principal,
                                               @PathVariable Long id) {
        settingsService.deactivateRule(id);
        recommendationService.clearCache();
        auditLogService.record(principal.getMemberId(), "RECO_RULE_DEACTIVATE", "RECO_RULE", String.valueOf(id), null);
        return Map.of("deactivated", true);
    }

    private void afterRuleChange(MemberPrincipal principal, String action, RecoSettingsService.Rule rule) {
        recommendationService.clearCache();
        auditLogService.record(principal.getMemberId(), action, "RECO_RULE", String.valueOf(rule.id()),
                rule.ruleType() + " " + rule.sourceValue() + "→" + rule.targetValue() + " product=" + rule.productId());
    }

    @GetMapping("/preview/personal")
    public RecoAdminService.Preview previewPersonal(@RequestParam Long memberId,
                                                    @RequestParam(defaultValue = "12") int limit) {
        return adminService.previewPersonal(memberId, limit);
    }

    @GetMapping("/preview/similar/{productId}")
    public RecoAdminService.Preview previewSimilar(@PathVariable Long productId,
                                                   @RequestParam(defaultValue = "12") int limit) {
        return adminService.previewSimilar(productId, limit);
    }

    @GetMapping("/preview/coordination/{productId}")
    public RecoAdminService.Preview previewCoordination(@PathVariable Long productId,
                                                        @RequestParam(defaultValue = "12") int limit) {
        return adminService.previewCoordination(productId, limit);
    }

    @GetMapping("/popular")
    public RecoAdminService.Preview popular(@RequestParam(defaultValue = "TOP10") String type,
                                            @RequestParam(defaultValue = "7") int period,
                                            @RequestParam(defaultValue = "20") int limit) {
        return adminService.previewPopular(type, period, limit);
    }

    @PostMapping("/popular/refresh")
    public Map<String, Boolean> refreshPopular(@AuthenticationPrincipal MemberPrincipal principal) {
        catalog.invalidate();
        popularityService.refreshAll();
        recommendationService.clearCache();
        auditLogService.record(principal.getMemberId(), "RECO_POPULAR_REFRESH", "RECO", "popular", null);
        return Map.of("refreshed", true);
    }

    @GetMapping("/purchase")
    public RecoAdminService.PurchaseReport purchase(@RequestParam(defaultValue = "REORDER") String filter) {
        return adminService.purchase(filter);
    }

    @GetMapping("/slow-moving")
    public RecoAdminService.SlowReport slowMoving(@RequestParam(required = false) String type) {
        return adminService.slowMoving(type);
    }

    @PostMapping("/decisions")
    public RecoAdminService.DecisionView decide(@AuthenticationPrincipal MemberPrincipal principal,
                                                @RequestBody RecoAdminService.DecisionRequest body) {
        return adminService.decide(body, principal.getMemberId());
    }

    @GetMapping("/decisions")
    public List<RecoAdminService.DecisionView> decisions(@RequestParam(defaultValue = "PURCHASE") String type,
                                                         @RequestParam(defaultValue = "50") int limit) {
        return adminService.decisions(type, limit);
    }

    @GetMapping("/analytics")
    public RecoStatsService.Analytics analytics(@RequestParam(defaultValue = "30") int days) {
        return statsService.analytics(days);
    }

    @GetMapping("/features")
    public List<ProductFeatureService.FeatureView> features(@RequestParam(required = false) String keyword,
                                                            @RequestParam(defaultValue = "100") int limit) {
        return featureService.list(keyword, limit);
    }

    @PostMapping("/features/refresh")
    public Map<String, Integer> refreshFeatures(@AuthenticationPrincipal MemberPrincipal principal) {
        int count = featureService.refreshRuleFeatures();
        recommendationService.clearCache();
        auditLogService.record(principal.getMemberId(), "RECO_FEATURE_REFRESH", "RECO", "features", "count=" + count);
        return Map.of("updated", count);
    }

    @PostMapping("/features/{productId}/analyze")
    public ProductFeatureService.FeatureView analyzeFeature(@AuthenticationPrincipal MemberPrincipal principal,
                                                            @PathVariable Long productId,
                                                            @RequestBody(required = false) AnalyzeRequest body) {
        boolean force = body != null && Boolean.TRUE.equals(body.forceRefresh());
        boolean paid = body != null && Boolean.TRUE.equals(body.allowPaid());
        ProductFeatureService.FeatureView view = featureService.analyzeWithAi(productId, force, paid,
                principal.getMemberId());
        recommendationService.clearCache();
        return view;
    }

    @PostMapping("/features/{productId}/from-analysis")
    public ProductFeatureService.FeatureView fromAnalysis(@PathVariable Long productId,
                                                          @RequestBody FromAnalysisRequest body) {
        ProductFeatureService.FeatureView view = featureService.saveFromAnalysis(productId, body.analysisId());
        recommendationService.clearCache();
        return view;
    }

    @PutMapping("/features/{productId}")
    public ProductFeatureService.FeatureView saveFeature(@AuthenticationPrincipal MemberPrincipal principal,
                                                         @PathVariable Long productId,
                                                         @RequestBody ProductFeatureService.ManualRequest body) {
        ProductFeatureService.FeatureView view = featureService.saveManual(productId, body);
        recommendationService.clearCache();
        auditLogService.record(principal.getMemberId(), "RECO_FEATURE_EDIT", "PRODUCT", String.valueOf(productId), null);
        return view;
    }
}
