package com.yuan.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import com.yuan.common.ErrorCode;
import com.yuan.constant.UserConstant;
import com.yuan.exception.BusinessException;
import com.yuan.model.dto.template.TemplateAuditRequest;
import com.yuan.model.dto.template.TemplateCreateRequest;
import com.yuan.model.dto.template.TemplateQueryRequest;
import com.yuan.model.dto.template.TemplateUpdateRequest;
import com.yuan.model.entity.TemplateInfo;
import com.yuan.model.vo.TemplateInfoVO;
import com.yuan.user.mapper.TemplateInfoMapper;
import com.yuan.user.service.TemplateInfoService;
import cn.hutool.json.JSONUtil;
import com.yuan.utils.UserContext;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
* @author dev
* @description 针对表【template_info(业务模板表)】的数据库操作Service实现
* @createDate 2026-06-09 15:47:38
*/
@Service
public class TemplateInfoServiceImpl extends ServiceImpl<TemplateInfoMapper, TemplateInfo>
    implements TemplateInfoService{

    private final TemplateInfoMapper templateInfoMapper;

    public TemplateInfoServiceImpl(TemplateInfoMapper templateInfoMapper) {
        this.templateInfoMapper = templateInfoMapper;
    }

    @Override
    public Long createTemplate(TemplateCreateRequest templateCreateRequest) {
        TemplateInfo templateInfo = new TemplateInfo();
        BeanUtils.copyProperties(templateCreateRequest, templateInfo);
        templateInfo.setTemplateSchema(JSONUtil.toJsonStr(templateCreateRequest.getTemplateSchema()));
        templateInfo.setCreatorId(Long.valueOf(UserContext.getUserId()));
        templateInfo.setAuditStatus(isAdmin() ? 1 : 0);
        this.save(templateInfo);
        return templateInfo.getId();
    }

    @Override
    public TemplateInfoVO getTemplateById(Long id) {
        TemplateInfo template = this.getById(id);
        if (template == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "模板不存在");
        }
        return toVO(template);
    }

    @Override
    public List<TemplateInfoVO> listTemplateByPage(TemplateQueryRequest request) {
        LambdaQueryWrapper<TemplateInfo> wrapper = new LambdaQueryWrapper<>();
        if (!UserContext.getRole().equals(UserConstant.ROLE_ADMIN)) {
            wrapper.eq(TemplateInfo::getCreatorId, Long.valueOf(UserContext.getUserId()));
        }
        if (request.getTemplateName() != null) {
            wrapper.like(TemplateInfo::getTemplateName, request.getTemplateName());
        }
        if (request.getAuditStatus() != null) {
            wrapper.eq(TemplateInfo::getAuditStatus, request.getAuditStatus());
        }
        wrapper.eq(TemplateInfo::getIsDelete, 0);
        wrapper.orderByDesc(TemplateInfo::getCreateTime);

        Page<TemplateInfo> page = baseMapper.selectPage(
                new Page<>(request.getPageNum(), request.getPageSize()), wrapper);

        return page.getRecords().stream()
                .map(this::toVO)
                .collect(Collectors.toList());
    }

    @Override
    public void auditTemplate(TemplateAuditRequest request) {
        if (request.getAuditStatus() != 1 && request.getAuditStatus() != 2) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "审核状态不合法，只能为通过或驳回");
        }
        if (request.getAuditStatus() == 2 && (request.getAuditMsg() == null || request.getAuditMsg().isBlank())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "驳回时必须填写驳回原因");
        }
        TemplateInfo template = this.getById(request.getId());
        if (template == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "模板不存在");
        }
        template.setAuditStatus(request.getAuditStatus());
        template.setAuditMsg(request.getAuditMsg());
        this.updateById(template);
    }

    /**
     * 更新模板
     */
    @Override
    public void updateTemplate(TemplateUpdateRequest request) {
        TemplateInfo template = this.getById(request.getId());
        if (template == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "模板不存在");
        }
        if (!canModify(template)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "只能修改自己创建的模板");
        }
        template.setTemplateName(request.getTemplateName());
        template.setDescription(request.getDescription());
        template.setTemplateSchema(request.getTemplateSchema());
        this.updateById(template);
    }

    /**
     * 根据模板id 删除模板
     */
    @Override
    public void deleteTemplate(Long id) {
        TemplateInfo template = this.getById(id);
        if (template == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "模板不存在");
        }
        if (!canModify(template)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "只能删除自己创建的模板");
        }
        this.removeById(id);
    }

    private TemplateInfoVO toVO(TemplateInfo template) {
        TemplateInfoVO vo = new TemplateInfoVO();
        BeanUtils.copyProperties(template, vo);
        return vo;
    }

    private boolean isAdmin() {
        return UserContext.getRole().equals(UserConstant.ROLE_ADMIN);
    }

    private boolean isCreator(TemplateInfo template) {
        Long currentUserId = Long.valueOf(UserContext.getUserId());
        return currentUserId.equals(template.getCreatorId());
    }

    private boolean canModify(TemplateInfo template) {
        return isAdmin() || isCreator(template);
    }
}
