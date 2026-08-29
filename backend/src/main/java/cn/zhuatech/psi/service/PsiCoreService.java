/* Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ */
package cn.zhuatech.psi.service;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class PsiCoreService {
    private final EntityManager em;

    public PsiCoreService(EntityManager em) { this.em = em; }

    @Transactional
    public InventoryBalance createBalance(CreateBalanceRequest request) {
        if (!findBalance(request.sku(), request.warehouse()).isEmpty()) throw conflict("该仓库SKU库存台账已存在");
        InventoryBalance balance = new InventoryBalance(request.sku(), request.warehouse(), request.openingQuantity());
        em.persist(balance);
        return balance;
    }

    public List<InventoryBalance> balances(String warehouse) {
        if (warehouse == null || warehouse.isBlank()) {
            return em.createQuery("select b from PsiInventoryBalance b order by b.warehouse,b.sku", InventoryBalance.class).getResultList();
        }
        return em.createQuery("select b from PsiInventoryBalance b where b.warehouse=:warehouse order by b.sku", InventoryBalance.class)
            .setParameter("warehouse", warehouse).getResultList();
    }

    public List<InventoryMovement> movements(String sku) {
        return em.createQuery("select m from PsiInventoryMovement m where (:sku is null or m.sku=:sku) order by m.createdAt desc", InventoryMovement.class)
            .setParameter("sku", sku == null || sku.isBlank() ? null : sku).setMaxResults(200).getResultList();
    }

    @Transactional
    public InventoryMovement post(MovementRequest request) {
        List<InventoryMovement> existing = em.createQuery("select m from PsiInventoryMovement m where m.idempotencyKey=:key", InventoryMovement.class)
            .setParameter("key", request.idempotencyKey()).getResultList();
        if (!existing.isEmpty()) return existing.getFirst();
        InventoryBalance balance = balanceForUpdate(request.sku(), request.warehouse());
        int beforeOnHand = balance.onHand;
        int beforeReserved = balance.reserved;
        switch (request.type()) {
            case "PURCHASE_RECEIPT", "SALES_RETURN", "ADJUSTMENT_IN", "TRANSFER_IN" -> balance.onHand += request.quantity();
            case "SALES_ISSUE", "PURCHASE_RETURN", "ADJUSTMENT_OUT", "TRANSFER_OUT" -> {
                if (balance.available() < request.quantity()) throw conflict("可用库存不足，禁止负库存过账");
                balance.onHand -= request.quantity();
            }
            case "RESERVE" -> {
                if (balance.available() < request.quantity()) throw conflict("可用库存不足，无法完成预留");
                balance.reserved += request.quantity();
            }
            case "RELEASE" -> {
                if (balance.reserved < request.quantity()) throw conflict("释放数量超过已预留库存");
                balance.reserved -= request.quantity();
            }
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不支持的库存动作");
        }
        balance.updatedAt = LocalDateTime.now();
        InventoryMovement movement = new InventoryMovement(request.idempotencyKey(), request.referenceNo(), request.type(),
            balance.sku, balance.warehouse, request.quantity(), beforeOnHand, balance.onHand, beforeReserved, balance.reserved, operator());
        em.persist(movement);
        return movement;
    }

    @Transactional
    public TransferResult transfer(TransferRequest request) {
        if (request.fromWarehouse().equals(request.toWarehouse())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "调出和调入仓库不能相同");
        InventoryMovement out = post(new MovementRequest(request.idempotencyKey()+"-OUT", request.referenceNo(), "TRANSFER_OUT", request.sku(), request.fromWarehouse(), request.quantity()));
        InventoryMovement in = post(new MovementRequest(request.idempotencyKey()+"-IN", request.referenceNo(), "TRANSFER_IN", request.sku(), request.toWarehouse(), request.quantity()));
        return new TransferResult(out, in);
    }

    @Transactional
    public InventoryCount createCount(CreateCountRequest request) {
        if (!em.createQuery("select c from PsiInventoryCount c where c.countNo=:no", InventoryCount.class)
            .setParameter("no", request.countNo()).getResultList().isEmpty()) throw conflict("盘点单号已存在");
        InventoryBalance balance = balanceForUpdate(request.sku(), request.warehouse());
        InventoryCount count = new InventoryCount(request.countNo(), balance.sku, balance.warehouse,
            balance.onHand, request.countedQuantity(), operator());
        em.persist(count);
        return count;
    }

    public List<InventoryCount> counts(String status) {
        return em.createQuery("select c from PsiInventoryCount c where (:status is null or c.status=:status) order by c.createdAt desc", InventoryCount.class)
            .setParameter("status", status == null || status.isBlank() ? null : status).getResultList();
    }

    @Transactional
    public InventoryCount submitCount(Long id) {
        InventoryCount count = countForUpdate(id);
        if (!"DRAFT".equals(count.status)) throw conflict("仅草稿盘点单可以提交");
        count.status = "PENDING_REVIEW";
        count.submittedBy = operator();
        count.submittedAt = LocalDateTime.now();
        return count;
    }

    @Transactional
    public InventoryCount reviewCount(Long id, CountReviewRequest request) {
        InventoryCount count = countForUpdate(id);
        if (!"PENDING_REVIEW".equals(count.status)) throw conflict("仅待复核盘点单可以审批");
        if (operator().equals(count.submittedBy)) throw conflict("盘点提交人与复核人必须职责分离");
        count.reviewRemark = request.remark();
        count.reviewedBy = operator();
        count.reviewedAt = LocalDateTime.now();
        if ("REJECT".equals(request.decision())) {
            count.status = "REJECTED";
            return count;
        }
        InventoryBalance balance = balanceForUpdate(count.sku, count.warehouse);
        if (balance.onHand != count.bookQuantity) throw conflict("盘点期间库存已变化，请重新盘点后提交");
        int variance = count.variance();
        if (variance != 0) {
            InventoryMovement movement = post(new MovementRequest("COUNT-" + count.id, count.countNo,
                variance > 0 ? "ADJUSTMENT_IN" : "ADJUSTMENT_OUT", count.sku, count.warehouse, Math.abs(variance)));
            count.movementId = movement.id;
        }
        count.status = "APPROVED";
        return count;
    }

    private List<InventoryBalance> findBalance(String sku, String warehouse) {
        return em.createQuery("select b from PsiInventoryBalance b where b.sku=:sku and b.warehouse=:warehouse", InventoryBalance.class)
            .setParameter("sku", sku).setParameter("warehouse", warehouse).getResultList();
    }

    private InventoryBalance balanceForUpdate(String sku, String warehouse) {
        List<InventoryBalance> result = em.createQuery("select b from PsiInventoryBalance b where b.sku=:sku and b.warehouse=:warehouse", InventoryBalance.class)
            .setParameter("sku", sku).setParameter("warehouse", warehouse).setLockMode(LockModeType.PESSIMISTIC_WRITE).getResultList();
        if (result.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "库存台账不存在");
        return result.getFirst();
    }

    private InventoryCount countForUpdate(Long id) {
        InventoryCount count = em.find(InventoryCount.class, id, LockModeType.PESSIMISTIC_WRITE);
        if (count == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "盘点单不存在");
        return count;
    }

    private String operator() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth == null ? "system" : auth.getName();
    }

    private ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT, message); }

    public record CreateBalanceRequest(@NotBlank @Size(max=50) String sku, @NotBlank @Size(max=50) String warehouse,
                                       @PositiveOrZero int openingQuantity) {}
    public record MovementRequest(@NotBlank @Size(max=80) String idempotencyKey, @NotBlank @Size(max=50) String referenceNo,
                                  @NotBlank String type, @NotBlank @Size(max=50) String sku,
                                  @NotBlank @Size(max=50) String warehouse, @Positive int quantity) {}
    public record TransferRequest(@NotBlank @Size(max=80) String idempotencyKey, @NotBlank @Size(max=50) String referenceNo,
                                  @NotBlank @Size(max=50) String sku, @NotBlank @Size(max=50) String fromWarehouse,
                                  @NotBlank @Size(max=50) String toWarehouse, @Positive int quantity) {}
    public record TransferResult(InventoryMovement outbound, InventoryMovement inbound) {}
    public record CreateCountRequest(@NotBlank @Size(max=50) String countNo, @NotBlank @Size(max=50) String sku,
                                     @NotBlank @Size(max=50) String warehouse, @PositiveOrZero int countedQuantity) {}
    public record CountReviewRequest(@NotNull @Pattern(regexp="APPROVE|REJECT") String decision,
                                     @NotBlank @Size(max=500) String remark) {}

    @Entity(name="PsiInventoryBalance")
    @Table(name="psi_inventory_balances", uniqueConstraints=@UniqueConstraint(columnNames={"sku","warehouse"}))
    public static class InventoryBalance {
        @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
        @Column(nullable=false,length=50) public String sku;
        @Column(nullable=false,length=50) public String warehouse;
        @Column(nullable=false) public int onHand;
        @Column(nullable=false) public int reserved;
        @Version public long version;
        @Column(nullable=false) public LocalDateTime updatedAt;
        protected InventoryBalance() {}
        InventoryBalance(String sku,String warehouse,int openingQuantity){this.sku=sku;this.warehouse=warehouse;this.onHand=openingQuantity;this.updatedAt=LocalDateTime.now();}
        public int getAvailable(){return available();}
        int available(){return onHand-reserved;}
    }

    @Entity(name="PsiInventoryMovement")
    @Table(name="psi_inventory_movements", uniqueConstraints=@UniqueConstraint(columnNames="idempotencyKey"))
    public static class InventoryMovement {
        @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
        @Column(nullable=false,length=80) public String idempotencyKey;
        @Column(nullable=false,length=50) public String referenceNo;
        @Column(nullable=false,length=30) public String type;
        @Column(nullable=false,length=50) public String sku;
        @Column(nullable=false,length=50) public String warehouse;
        public int quantity; public int beforeOnHand; public int afterOnHand; public int beforeReserved; public int afterReserved;
        @Column(nullable=false,length=50) public String operatorName;
        @Column(nullable=false) public LocalDateTime createdAt;
        protected InventoryMovement() {}
        InventoryMovement(String key,String referenceNo,String type,String sku,String warehouse,int quantity,int beforeOnHand,int afterOnHand,int beforeReserved,int afterReserved,String operator){this.idempotencyKey=key;this.referenceNo=referenceNo;this.type=type;this.sku=sku;this.warehouse=warehouse;this.quantity=quantity;this.beforeOnHand=beforeOnHand;this.afterOnHand=afterOnHand;this.beforeReserved=beforeReserved;this.afterReserved=afterReserved;this.operatorName=operator;this.createdAt=LocalDateTime.now();}
    }

    @Entity(name="PsiInventoryCount")
    @Table(name="psi_inventory_counts", uniqueConstraints=@UniqueConstraint(columnNames="countNo"))
    public static class InventoryCount {
        @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
        @Column(nullable=false,length=50) public String countNo;
        @Column(nullable=false,length=50) public String sku;
        @Column(nullable=false,length=50) public String warehouse;
        public int bookQuantity;
        public int countedQuantity;
        @Column(nullable=false,length=30) public String status;
        @Column(nullable=false,length=80) public String createdBy;
        @Column(length=80) public String submittedBy;
        @Column(length=80) public String reviewedBy;
        @Column(length=500) public String reviewRemark;
        public Long movementId;
        @Column(nullable=false) public LocalDateTime createdAt;
        public LocalDateTime submittedAt;
        public LocalDateTime reviewedAt;
        @Version public long version;
        protected InventoryCount() {}
        InventoryCount(String no,String sku,String warehouse,int book,int counted,String by){this.countNo=no;this.sku=sku;this.warehouse=warehouse;this.bookQuantity=book;this.countedQuantity=counted;this.createdBy=by;this.status="DRAFT";this.createdAt=LocalDateTime.now();}
        public int getVariance(){return variance();}
        int variance(){return countedQuantity-bookQuantity;}
    }
}
