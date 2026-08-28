/* Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ */
package cn.zhuatech.psi.domain;
import org.springframework.stereotype.Component;
import java.util.*;
@Component
public class DomainCatalog {
    private final Map<String, WorkflowAction> actions = new LinkedHashMap<>();
    public DomainCatalog() {
        actions.put("SUBMIT", new WorkflowAction("SUBMIT", "提交单据审核", List.of("草稿"), "待审核", "OPERATOR"));
        actions.put("APPROVE", new WorkflowAction("APPROVE", "批准库存过账", List.of("待审核"), "待过账", "ADMIN"));
        actions.put("POST", new WorkflowAction("POST", "确认业务过账", List.of("待过账"), "已完成", "ADMIN"));
    }
    public String systemName() { return "知华科技企业进销存管理系统"; }
    public String scene() { return "商品、供应商、客户、采购、销售、入出库、调拨、盘点、批次、应收应付与经营分析"; }
    public String initialStatus() { return "草稿"; }
    public String partyLabel() { return "客户/供应商/仓库"; }
    public String amountLabel() { return "进销存金额"; }
    public String quantityLabel() { return "商品数量"; }
    public String dueLabel() { return "交付或结算日期"; }
    public List<ModuleDefinition> modules() { return List.of(
            new ModuleDefinition("PRODUCT", "商品资料", "维护SKU、条码、规格、单位、税率、批次和保质期"),
            new ModuleDefinition("PURCHASE", "采购管理", "处理采购申请、订单、到货、退货和供应商交期"),
            new ModuleDefinition("SALES", "销售管理", "管理报价、订单、发货、退货、价格和客户信用"),
            new ModuleDefinition("INVENTORY", "库存台账", "按组织、仓库、库位、批次维护可用和锁定库存"),
            new ModuleDefinition("TRANSFER", "调拨与盘点", "完成调拨、盘点、损溢审批和账实差异闭环"),
            new ModuleDefinition("BATCH", "批次效期", "执行先进先出、保质期预警和批次追溯"),
            new ModuleDefinition("AR_AP", "往来结算", "生成应收应付、收付款、核销和客户供应商对账"),
            new ModuleDefinition("COST", "成本核算", "支持移动平均成本、毛利和库存金额分析"),
            new ModuleDefinition("ANALYTICS", "经营分析", "分析采购、销售、周转、滞销、缺货和利润")
        ); }
    public Map<String, WorkflowAction> actions() { return Collections.unmodifiableMap(actions); }
    public record ModuleDefinition(String code,String name,String description) {}
    public record WorkflowAction(String code,String label,List<String> from,String to,String requiredRole) {}
}
