/* Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ */
package cn.zhuatech.psi.controller;

import cn.zhuatech.psi.common.ApiResponse;
import cn.zhuatech.psi.service.ReplenishmentGovernanceService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/enterprise/psi")
public class ReplenishmentGovernanceController {
    private final ReplenishmentGovernanceService service;

    public ReplenishmentGovernanceController(ReplenishmentGovernanceService service) {
        this.service = service;
    }

    @PostMapping("/replenishment-governance")
    public ApiResponse<ReplenishmentGovernanceService.Assessment> assess(
            @Valid @RequestBody ReplenishmentGovernanceService.Request request) {
        return ApiResponse.ok(service.assess(request));
    }
}
