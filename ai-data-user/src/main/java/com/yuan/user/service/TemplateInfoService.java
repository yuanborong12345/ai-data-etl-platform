package com.yuan.user.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.yuan.model.dto.template.TemplateAuditRequest;
import com.yuan.model.dto.template.TemplateCreateRequest;
import com.yuan.model.dto.template.TemplateQueryRequest;
import com.yuan.model.dto.template.TemplateUpdateRequest;
import com.yuan.model.entity.TemplateInfo;
import com.yuan.model.vo.TemplateInfoVO;

import java.util.List;

/**
* @author dev
* @description 针对表【template_info(业务模板表)】的数据库操作Service
* @createDate 2026-06-09 15:47:38
*/
public interface TemplateInfoService extends IService<TemplateInfo> {
    Long createTemplate(TemplateCreateRequest templateCreateRequest);

    TemplateInfoVO getTemplateById(Long id);

    List<TemplateInfoVO> listTemplateByPage(TemplateQueryRequest request);

    void auditTemplate(TemplateAuditRequest request);

    void updateTemplate(TemplateUpdateRequest request);

    void deleteTemplate(Long id);
}
