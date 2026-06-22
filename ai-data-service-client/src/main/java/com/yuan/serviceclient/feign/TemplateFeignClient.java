package com.yuan.serviceclient.feign;

import com.yuan.common.BaseResponse;
import com.yuan.model.vo.TemplateInfoVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * ai-data-user 模板服务 Feign 客户端。
 */
@FeignClient(name = "ai-data-user", contextId = "userTemplateFeign",path = "/user/template")
public interface TemplateFeignClient {

    /**
     * 根据模板 ID 获取模板信息（含列定义 schema）。
     */
    @GetMapping("/{id}")
    BaseResponse<TemplateInfoVO> getTemplateById(@PathVariable("id") Long id);
}
