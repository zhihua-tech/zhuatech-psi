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
}
