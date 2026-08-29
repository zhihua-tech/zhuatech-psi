/* Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ */
package cn.zhuatech.psi.controller;

import cn.zhuatech.psi.common.ApiResponse;
import cn.zhuatech.psi.service.PsiCoreService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController @RequestMapping("/api/core/psi")
public class PsiCoreController {
    private final PsiCoreService service;
    public PsiCoreController(PsiCoreService service){this.service=service;}
    @PostMapping("/balances") ApiResponse<PsiCoreService.InventoryBalance> create(@Valid @RequestBody PsiCoreService.CreateBalanceRequest request){return ApiResponse.ok(service.createBalance(request));}
    @GetMapping("/balances") ApiResponse<List<PsiCoreService.InventoryBalance>> balances(@RequestParam(required=false) String warehouse){return ApiResponse.ok(service.balances(warehouse));}
    @PostMapping("/movements") ApiResponse<PsiCoreService.InventoryMovement> post(@Valid @RequestBody PsiCoreService.MovementRequest request){return ApiResponse.ok(service.post(request));}
    @GetMapping("/movements") ApiResponse<List<PsiCoreService.InventoryMovement>> movements(@RequestParam(required=false) String sku){return ApiResponse.ok(service.movements(sku));}
    @PostMapping("/transfers") ApiResponse<PsiCoreService.TransferResult> transfer(@Valid @RequestBody PsiCoreService.TransferRequest request){return ApiResponse.ok(service.transfer(request));}
    @PostMapping("/counts") ApiResponse<PsiCoreService.InventoryCount> createCount(@Valid @RequestBody PsiCoreService.CreateCountRequest request){return ApiResponse.ok(service.createCount(request));}
    @GetMapping("/counts") ApiResponse<List<PsiCoreService.InventoryCount>> counts(@RequestParam(required=false) String status){return ApiResponse.ok(service.counts(status));}
    @PostMapping("/counts/{id}/submit") ApiResponse<PsiCoreService.InventoryCount> submitCount(@PathVariable Long id){return ApiResponse.ok(service.submitCount(id));}
}
