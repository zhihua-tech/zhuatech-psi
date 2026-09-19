/* Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ */
package cn.zhuatech.psi.controller;

import cn.zhuatech.psi.common.ApiResponse;
import cn.zhuatech.psi.service.PsiCoreService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/**
 * 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。
 */
@RestController
@RequestMapping("/api/admin/core/psi/counts")
public class PsiCountAdminController {
    private final PsiCoreService service;
    /**
     * 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。
     */
    public PsiCountAdminController(PsiCoreService service){this.service=service;}
    /**
     * 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。
     */
    @PostMapping("/{id}/review")
    ApiResponse<PsiCoreService.InventoryCount> review(@PathVariable Long id,
        @Valid @RequestBody PsiCoreService.CountReviewRequest request){return ApiResponse.ok(service.reviewCount(id,request));}
}
