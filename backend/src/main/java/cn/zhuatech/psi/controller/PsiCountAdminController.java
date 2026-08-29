/* Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ */
package cn.zhuatech.psi.controller;

import cn.zhuatech.psi.common.ApiResponse;
import cn.zhuatech.psi.service.PsiCoreService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/core/psi/counts")
public class PsiCountAdminController {
    private final PsiCoreService service;
    public PsiCountAdminController(PsiCoreService service){this.service=service;}
    @PostMapping("/{id}/review")
    ApiResponse<PsiCoreService.InventoryCount> review(@PathVariable Long id,
        @Valid @RequestBody PsiCoreService.CountReviewRequest request){return ApiResponse.ok(service.reviewCount(id,request));}
}
