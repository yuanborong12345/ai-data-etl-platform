package com.yuan.serviceclient.feign;

import com.yuan.common.BaseResponse;
import com.yuan.model.dto.storage.PresignedDownloadResult;
import com.yuan.model.vo.FileInfoVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * ai-data-user 文件服务 Feign 客户端。
 */
@FeignClient(name = "ai-data-user", contextId = "userFileFeign", path = "/user/file")
public interface FileFeignClient {

    /**
     * 获取文件元数据。
     */
    @GetMapping("/{id}")
    BaseResponse<FileInfoVO> getFileInfo(@PathVariable("id") Long id);

    /**
     * 获取文件预签名下载 URL（10 分钟有效）。
     */
    @GetMapping("/{id}/presigned/download")
    BaseResponse<PresignedDownloadResult> createDownloadPresigned(
            @PathVariable("id") Long id,
            @RequestParam("expires") Long expiresSeconds);
}
