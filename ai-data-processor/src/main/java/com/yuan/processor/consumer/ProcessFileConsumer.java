package com.yuan.processor.consumer;

import com.yuan.constant.MqConstant;
import com.yuan.model.dto.processor.FileProcessMessage;
import com.yuan.processor.service.FileDownloadService;
import com.yuan.processor.service.ParseResultService;
import com.yuan.processor.service.TemplateDataParser;
import com.yuan.processor.service.TemplateDataParser.ParseResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.InputStream;

@Component
@Slf4j
@RequiredArgsConstructor
public class ProcessFileConsumer {

    private final FileDownloadService fileDownloadService;
    private final TemplateDataParser templateDataParser;
    private final ParseResultService parseResultService;

    @RabbitListener(queues = MqConstant.QUEUE_FILE_PROCESS)
    public void processFileMessage(FileProcessMessage message) {
        log.info("【消费者】收到文件处理任务: taskId={}, fileId={}, fileName={}",
                message.getTaskId(), message.getFileId(), message.getFileName());
        try {
            // 1. 远程下载文件
            InputStream fileStream = fileDownloadService.download(message.getFileId());

            // 2. 按模板解析文件
            ParseResult parseResult = templateDataParser.parse(fileStream, message.getTemplateId(), message.getFileName());
            log.info("【消费者】解析完成: taskId={}, totalRows={}, validRows={}, errorRows={}",
                    message.getTaskId(), parseResult.rows().size(), parseResult.validRows(), parseResult.errorRows());

            // 3. 存储解析结果
            parseResultService.save(message.getTaskId(), parseResult);

            // TODO 4. 发送 DataAnalysisMessage 到下一队列（后续步骤）

            log.info("【消费者】文件处理完成: taskId={}", message.getTaskId());
        } catch (Exception e) {
            log.error("【消费者】处理消息异常: taskId={}", message.getTaskId(), e);
        }
    }
}
