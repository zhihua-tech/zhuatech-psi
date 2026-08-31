/* Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ */
package cn.zhuatech.psi.service;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ReplenishmentGovernanceService {

    public Assessment assess(Request request) {
        int projectedStock = request.onHand() + request.openPurchase() - request.forecastDemand();
        int recommendedOrder = Math.max(0, request.safetyStock() - projectedStock);
        int stockAfterOrder = projectedStock + recommendedOrder;
        List<String> blockers = new ArrayList<>();
        List<String> actions = new ArrayList<>();

        if (!request.policyApproved()) {
            blockers.add("补货策略尚未审批");
        }
        if (request.supplierLeadTimeDays() <= 0) {
            blockers.add("供应商交期未维护");
        }
        if (request.agedInventory() > request.safetyStock()) {
            blockers.add("呆滞库存高于安全库存，需先完成处置评审");
        }
        if (projectedStock < request.safetyStock()) {
            actions.add("预计库存低于安全库存，建议补货 " + recommendedOrder + " 件");
        }
        if (stockAfterOrder > request.maxStock()) {
            blockers.add("建议补货后库存将超过最高库存");
        }
        if (request.forecastDemand() > 0 && request.openPurchase() == 0 && projectedStock < 0) {
            actions.add("预计出现缺货，需加急采购或调整承诺交期");
        }

        Decision decision = !blockers.isEmpty()
                ? Decision.HOLD
                : recommendedOrder > 0 ? Decision.ORDER : Decision.NO_ACTION;
        return new Assessment(request.sku(), projectedStock, recommendedOrder, stockAfterOrder,
                decision, List.copyOf(blockers), List.copyOf(actions));
    }

    public record Request(
            @NotBlank String sku,
            @Min(0) int forecastDemand,
            @Min(0) int onHand,
            @Min(0) int openPurchase,
            @Min(0) int safetyStock,
            @Min(0) int maxStock,
            @Min(0) int agedInventory,
            @Min(0) int supplierLeadTimeDays,
            boolean policyApproved) {
        @AssertTrue(message = "最高库存必须大于或等于安全库存")
        public boolean isStockPolicyValid() {
            return maxStock >= safetyStock;
        }
    }

    public record Assessment(
            String sku,
            int projectedStock,
            int recommendedOrder,
            int stockAfterOrder,
            Decision decision,
            List<String> blockers,
            List<String> actions) {
    }

    public enum Decision { ORDER, HOLD, NO_ACTION }
}
