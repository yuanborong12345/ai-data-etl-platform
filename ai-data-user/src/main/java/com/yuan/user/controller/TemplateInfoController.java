package com.yuan.user.controller;

import com.yuan.annotation.AuthCheck;
import com.yuan.common.BaseResponse;
import com.yuan.common.ResultUtils;
import com.yuan.constant.UserConstant;
import com.yuan.model.dto.template.TemplateAuditRequest;
import com.yuan.model.dto.template.TemplateCreateRequest;
import com.yuan.model.dto.template.TemplateQueryRequest;
import com.yuan.model.dto.template.TemplateUpdateRequest;
import com.yuan.model.vo.TemplateInfoVO;
import com.yuan.user.service.TemplateInfoService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/")
public class TemplateInfoController {

    @Resource
    private TemplateInfoService templateInfoService;

    @PostMapping("/template/create")
    public BaseResponse<Long> createTemplate(@Valid @RequestBody TemplateCreateRequest templateCreateRequest) {
        Long templateId = templateInfoService.createTemplate(templateCreateRequest);
        return ResultUtils.success(templateId);
    }

    @GetMapping("/template/{id}")
    public BaseResponse<TemplateInfoVO> getTemplateById(@PathVariable(value = "id") Long id) {
        return ResultUtils.success(templateInfoService.getTemplateById(id));
    }

    @PostMapping("/template/list")
    public BaseResponse<List<TemplateInfoVO>> listTemplateByPage(@RequestBody TemplateQueryRequest request) {
        return ResultUtils.success(templateInfoService.listTemplateByPage(request));
    }

    @PostMapping("/template/audit")
    @AuthCheck(mustRole = UserConstant.ROLE_ADMIN)
    public BaseResponse<String> auditTemplate(@Valid @RequestBody TemplateAuditRequest request) {
        templateInfoService.auditTemplate(request);
        return ResultUtils.success("ok");
    }

    @PostMapping("/template/update")
    public BaseResponse<String> updateTemplate(@Valid @RequestBody TemplateUpdateRequest request) {
        templateInfoService.updateTemplate(request);
        return ResultUtils.success("ok");
    }

    @DeleteMapping("/template/{id}")
    public BaseResponse<String> deleteTemplate(@PathVariable(value = "id") Long id) {
        templateInfoService.deleteTemplate(id);
        return ResultUtils.success("ok");
    }
}
