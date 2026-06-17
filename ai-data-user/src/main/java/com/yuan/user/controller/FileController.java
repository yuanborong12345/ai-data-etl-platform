package com.yuan.user.controller;

import com.yuan.annotation.AuthCheck;
import com.yuan.common.BaseResponse;
import com.yuan.common.ResultUtils;
import com.yuan.constant.UserConstant;
import com.yuan.model.entity.FileInfo;
import com.yuan.model.vo.FileInfoVO;
import com.yuan.user.service.FileService;
import com.yuan.model.dto.storage.FileStorageObject;
import com.yuan.model.dto.storage.PresignedDownloadResult;
import com.yuan.model.dto.storage.PresignedUploadRequest;
import com.yuan.model.dto.storage.PresignedUploadResult;
import com.yuan.utils.UserContext;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * 文件管理控制器。
 *
 * <p>提供文件上传、下载、预签名、删除与暂存提升等 REST 接口。</p>
 */
@RestController
@RequestMapping("/file")
public class FileController {

    private final FileService fileService;

    public FileController(FileService fileService) {
        this.fileService = fileService;
    }

    /**
     * 服务端直接上传文件并提交处理任务。
     */
    @PostMapping("/upload")
    public BaseResponse<FileInfoVO> upload(@RequestParam("file") MultipartFile file,
                                           @RequestParam("templateId") Long templateId,
                                           @RequestParam(value = "promptContent", required = false) String promptContent) {
        Long userId = Long.parseLong(UserContext.getUserId());
        FileInfo fileInfo = fileService.uploadFileAndSubmitTask(file, userId, promptContent,templateId);
        return ResultUtils.success(toVO(fileInfo));
    }

    /**
     * 获取文件元数据。
     */
    @GetMapping("/{id}")
    public BaseResponse<FileInfoVO> getFileInfo(@PathVariable Long id) {
        FileInfo fileInfo = fileService.getFileInfo(id);
        return ResultUtils.success(toVO(fileInfo));
    }

    /**
     * 服务端直接下载文件（流式传输）。
     */
    @GetMapping("/{id}/download")
    public void download(@PathVariable Long id, HttpServletResponse response) throws IOException {
        FileInfo fileInfo = fileService.getFileInfo(id);
        FileStorageObject object = fileService.downloadObject(id);

        // 设置响应头
        response.setContentType(object.getContentType() != null
                ? object.getContentType() : "application/octet-stream");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment()
                        .filename(fileInfo.getFileName(), StandardCharsets.UTF_8)
                        .build()
                        .toString());
        if (object.getContentLength() != null) {
            response.setContentLengthLong(object.getContentLength());
        }

        // 流式写出文件内容
        try (OutputStream os = response.getOutputStream();
             var is = object.getInputStream()) {
            StreamUtils.copy(is, os);
        }
    }

    /**
     * 创建上传预签名地址（预注册文件元数据）。
     */
    @PostMapping("/presigned/upload")
    public BaseResponse<PresignedUploadResult> createUploadPresigned(
            @RequestParam("fileName") String fileName,
            @RequestParam(value = "contentType", required = false) String contentType) {
        Long userId = Long.parseLong(UserContext.getUserId());
        PresignedUploadRequest request = new PresignedUploadRequest();
        request.setFileName(fileName);
        request.setContentType(contentType);
        PresignedUploadResult result = fileService.createUploadPresigned(request, userId);
        return ResultUtils.success(result);
    }

    /**
     * 创建下载预签名地址。
     */
    @GetMapping("/{id}/presigned/download")
    public BaseResponse<PresignedDownloadResult> createDownloadPresigned(
            @PathVariable Long id,
            @RequestParam(value = "expires", required = false) Long expiresSeconds) {
        Duration expires = expiresSeconds != null ? Duration.ofSeconds(expiresSeconds) : null;
        PresignedDownloadResult result = fileService.createDownloadPresigned(id, expires);
        return ResultUtils.success(result);
    }

    /**
     * 确认预签名上传完成并提交处理任务。
     *
     * <p>客户端通过预签名 URL 上传完成后调用此接口，回填文件元数据并触发 ETL 流程。</p>
     */
    @PostMapping("/{id}/confirm")
    public BaseResponse<FileInfoVO> confirmUpload(
            @PathVariable Long id,
            @RequestParam("templateId") Long templateId,
            @RequestParam(value = "promptContent", required = false) String promptContent) {
        FileInfo fileInfo = fileService.confirmUploadAndSubmitTask(id, promptContent,templateId);
        return ResultUtils.success(toVO(fileInfo));
    }

    /**
     * 删除文件（逻辑删除 + 存储层删除）。
     */
    @DeleteMapping("/{id}")
    @AuthCheck(mustRole = UserConstant.ROLE_ADMIN)
    public BaseResponse<Boolean> delete(@PathVariable Long id) {
        boolean result = fileService.deleteFile(id);
        return ResultUtils.success(result);
    }

    /**
     * 将暂存文件提升为久存文件。
     */
    @PostMapping("/{id}/promote")
    @AuthCheck(mustRole = UserConstant.ROLE_ADMIN)
    public BaseResponse<FileInfoVO> promote(@PathVariable Long id) {
        FileInfo fileInfo = fileService.promoteFile(id);
        return ResultUtils.success(toVO(fileInfo));
    }

    /**
     * FileInfo Entity → FileInfoVO。
     */
    private FileInfoVO toVO(FileInfo fileInfo) {
        FileInfoVO vo = new FileInfoVO();
        vo.setId(fileInfo.getId());
        vo.setFileName(fileInfo.getFileName());
        vo.setFileSize(fileInfo.getFileSize());
        vo.setFileType(fileInfo.getFileType());
        vo.setCreateTime(fileInfo.getCreateTime());
        vo.setUpdateTime(fileInfo.getUpdateTime());
        return vo;
    }
}
