package com.yuyanghui.qualityradar.risk;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class RiskScoringServiceTest {
    private final RiskScoringService service = new RiskScoringService();

    @Test void paymentChangeGetsAnExplainableScoreAndARegressionRecommendation() {
        String diff = "diff --git a/src/payment/PaymentService.java b/src/payment/PaymentService.java\n--- a/src/payment/PaymentService.java\n+++ b/src/payment/PaymentService.java\n@@\n+public void capture() {}\n";
        var result = service.assess(diff);
        assertEquals("LOW", result.level());
        assertTrue(result.affectedComponents().contains("payment"));
        assertEquals("payment-contract", result.recommendations().getFirst().testKey());
    }

    @Test void sameDiffAlwaysProducesSameAssessment() {
        String diff = "+++ b/src/catalog/ProductService.java\n+return product;\n";
        assertEquals(service.assess(diff), service.assess(diff));
    }
}
