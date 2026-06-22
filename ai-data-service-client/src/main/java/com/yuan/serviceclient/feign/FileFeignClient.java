package com.yuan.serviceclient.feign;

import com.yuan.common.BaseResponse;
import com.yuan.model.vo.FileInfoVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * ai-data-user 文件服务 Feign 客户端。
 */
@FeignClient(name = "ai-data-user",contextId = "userFileFeign", path = "/user/file")
public interface FileFeignClient {

    /**
     * 获取文件元数据。
     */
    @GetMapping("/{id}")
    BaseResponse<FileInfoVO> getFileInfo(@PathVariable("id") Long id);

    /**
     * 下载文件内容（返回原始字节）。
     */
    @GetMapping("/{id}/inner/download")
    byte[] download(@PathVariable("id") Long id);
}
