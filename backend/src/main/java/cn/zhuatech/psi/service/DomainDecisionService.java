/* Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ */
package cn.zhuatech.psi.service;
import jakarta.validation.constraints.*;
import org.springframework.stereotype.Service;
import java.util.*;
@Service public class DomainDecisionService {
 public DecisionResult assess(DecisionRequest request) { double amount=request.orderedQuantity()*request.unitPrice();double margin=amount-request.orderedQuantity()*request.unitCost();int score=100;List<String> actions=new ArrayList<>();if(request.availableQuantity()<request.orderedQuantity()){score-=45;actions.add("补货或拆分交付后再过账");}if(request.customerCreditAvailable()<amount){score-=45;actions.add("申请信用额度或完成预收款");}if(request.unitPrice()<request.unitCost()){score-=30;actions.add("复核负毛利订单");}if(request.shelfLifeDaysRemaining()<30){score-=25;actions.add("执行临期商品审批");}if(!request.priceApproved()){score-=35;actions.add("完成销售价格审批");}if(!request.batchTraceable()){score-=40;actions.add("补齐批次追溯信息");}return result(score,actions,"READY_TO_POST","MANUAL_REVIEW","BLOCKED",Map.of("orderAmount",amount,"grossMargin",margin,"stockAfterPosting",request.availableQuantity()-request.orderedQuantity(),"creditAfterPosting",request.customerCreditAvailable()-amount)); }
 private DecisionResult result(int raw,List<String> actions,String good,String warn,String bad,Map<String,Object> metrics) { int score=Math.max(0,Math.min(100,raw));String decision=score>=80?good:score>=50?warn:bad;return new DecisionResult(decision,score,metrics,List.copyOf(actions)); }
 private DecisionResult riskResult(int raw,List<String> actions,String good,String warn,String bad,Map<String,Object> metrics) { int score=Math.max(0,Math.min(100,raw));String decision=score>=70?bad:score>=40?warn:good;return new DecisionResult(decision,score,metrics,List.copyOf(actions)); }
 public record DecisionRequest(
        @NotBlank String documentNo,
        @Positive int orderedQuantity,
        @PositiveOrZero int availableQuantity,
        @PositiveOrZero double unitPrice,
        @PositiveOrZero double unitCost,
        @PositiveOrZero double customerCreditAvailable,
        @PositiveOrZero int shelfLifeDaysRemaining,
        boolean priceApproved,
        boolean batchTraceable) {}
 public record DecisionResult(String decision,int score,Map<String,Object> metrics,List<String> actions) {}
}
