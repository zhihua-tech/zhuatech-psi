/* Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ */
package cn.zhuatech.psi.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReplenishmentGovernanceServiceTest {
    private final ReplenishmentGovernanceService service = new ReplenishmentGovernanceService();

    @Test
    void recommendsOrderWhenProjectedStockFallsBelowSafetyStock() {
        var result = service.assess(new ReplenishmentGovernanceService.Request(
                "SKU-001", 120, 80, 20, 30, 150, 5, 14, true));

        assertThat(result.projectedStock()).isEqualTo(-20);
        assertThat(result.recommendedOrder()).isEqualTo(50);
        assertThat(result.decision()).isEqualTo(ReplenishmentGovernanceService.Decision.ORDER);
        assertThat(result.blockers()).isEmpty();
        assertThat(result.actions()).hasSize(1);
    }

    @Test
    void holdsOrderWhenPolicyAndInventoryControlsFail() {
        var result = service.assess(new ReplenishmentGovernanceService.Request(
                "SKU-002", 10, 30, 0, 20, 80, 25, 0, false));

        assertThat(result.decision()).isEqualTo(ReplenishmentGovernanceService.Decision.HOLD);
        assertThat(result.blockers()).hasSize(3);
        assertThat(result.recommendedOrder()).isZero();
    }
}
