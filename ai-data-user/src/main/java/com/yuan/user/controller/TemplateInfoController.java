package com.yuan.user.controller;

import cn.hutool.json.JSONUtil;
import com.alibaba.excel.EasyExcel;
import com.yuan.annotation.AuthCheck;
import com.yuan.common.BaseResponse;
import com.yuan.common.ErrorCode;
import com.yuan.common.ResultUtils;
import com.yuan.constant.ApproveConstant;
import com.yuan.constant.UserConstant;
import com.yuan.exception.BusinessException;
import com.yuan.model.dto.template.*;
import com.yuan.model.entity.TemplateInfo;
import com.yuan.model.vo.TemplateInfoVO;
import com.yuan.user.service.TemplateInfoService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/template")
@Slf4j
public class TemplateInfoController {

    @Resource
    private TemplateInfoService templateInfoService;

    @PostMapping("/create")
    public BaseResponse<Long> createTemplate(@Valid @RequestBody TemplateCreateRequest templateCreateRequest) {
        Long templateId = templateInfoService.createTemplate(templateCreateRequest);
        return ResultUtils.success(templateId);
    }

    @GetMapping("/{id}")
    public BaseResponse<TemplateInfoVO> getTemplateById(@PathVariable(value = "id") Long id) {
        return ResultUtils.success(templateInfoService.getTemplateById(id));
    }

    @PostMapping("/list")
    public BaseResponse<List<TemplateInfoVO>> listTemplateByPage(@RequestBody TemplateQueryRequest request) {
        return ResultUtils.success(templateInfoService.listTemplateByPage(request));
    }

    @PostMapping("/audit")
    @AuthCheck(mustRole = UserConstant.ROLE_ADMIN)
    public BaseResponse<String> auditTemplate(@Valid @RequestBody TemplateAuditRequest request) {
        templateInfoService.auditTemplate(request);
        return ResultUtils.success("ok");
    }

    @PostMapping("/update")
    public BaseResponse<String> updateTemplate(@Valid @RequestBody TemplateUpdateRequest request) {
        templateInfoService.updateTemplate(request);
        return ResultUtils.success("ok");
    }

    @DeleteMapping("/{id}")
    public BaseResponse<String> deleteTemplate(@PathVariable(value = "id") Long id) {
        templateInfoService.deleteTemplate(id);
        return ResultUtils.success("ok");
    }

    @GetMapping("/download/{id}")
    public void downloadTemplate(@PathVariable("id") Long id, HttpServletResponse response) {
        try{
            if(id == null || id <= 0){
                throw new BusinessException(ErrorCode.PARAMS_ERROR);
            }
            //1. 根据id查询模板
            TemplateInfo templateInfo = templateInfoService.getById(id);
            if (templateInfo == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND_ERROR,"模板不存在");
            }
            if (templateInfo.getAuditStatus() != ApproveConstant.APPROVE) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR,"该模板尚未审核通过，无法下载");
            }
            //2. 设置 HTTP 响应头，Excel文件下载请求
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setCharacterEncoding("utf-8");
            String encodedFileName = URLEncoder.encode(templateInfo.getTemplateName(), "UTF-8").replaceAll("\\+", "%20");
            response.setHeader("Content-Disposition", "attachment; filename*=utf-8''" + encodedFileName + ".xlsx");
            //3. 将数据库保存的JSON 转换为EasyExcel的动态表头
            String templateSchema = templateInfo.getTemplateSchema();
            String templateName = templateInfo.getTemplateName();
            List<TemplateSchemaDTO> list = JSONUtil.parseArray(templateSchema).toList(TemplateSchemaDTO.class);
            List<List<String>> headData = buildDynamicHead(list);
            EasyExcel.write(response.getOutputStream())
                    .head(headData)
                    .autoCloseStream(Boolean.FALSE)
                    .sheet(templateName)
                    .doWrite(new ArrayList<>());
        } catch (IOException e) {
            log.error("下载模板失败，模板ID: {}", id, e);
            try {
                response.reset();
                response.setContentType("application/json");
                response.setCharacterEncoding("utf-8");
                response.getWriter().write("{\"code\":500,\"msg\":\"下载模板失败\"}");
            } catch (IOException ioException) {
                log.error("重置响应失败", ioException);
            }
        }
    }

    /**
     * 将元数据 Schema 转换为 EasyExcel 识别的动态表头结构
     */
    private List<List<String>> buildDynamicHead(List<TemplateSchemaDTO> schemaList) {
        if(schemaList == null || schemaList.isEmpty()){
            return new ArrayList<>();
        }
        List<List<String>> headList = new ArrayList<>();
        // 1. 按照 columnIndex 从小到大排序，防止前端传参无序
        List<TemplateSchemaDTO> sortedSchema = schemaList.stream()
                .sorted(Comparator.comparing(TemplateSchemaDTO::getColumnIndex))
                .collect(Collectors.toList());

        // 2. 构建 EasyExcel 的头结构
        for (TemplateSchemaDTO schema : sortedSchema) {
            List<String> head = new ArrayList<>();
            head.add(schema.getHeaderName());
            headList.add(head);
        }
        return headList;
    }
}
