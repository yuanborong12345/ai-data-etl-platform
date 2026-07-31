package com.yuan.processor.consumer;

import com.yuan.constant.MqConstant;
import com.yuan.model.dto.processor.DataAnalysisMessage;
import com.yuan.model.dto.processor.FileProcessMessage;
import com.yuan.processor.service.FileDownloadService;
import com.yuan.processor.service.ParseResultService;
import com.yuan.processor.service.TemplateDataParser;
import com.yuan.processor.service.TemplateDataParser.ParseResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.io.InputStream;

@Component
@Slf4j
@RequiredArgsConstructor
public class ProcessFileConsumer {

    private final FileDownloadService fileDownloadService;
    private final TemplateDataParser templateDataParser;
    private final ParseResultService parseResultService;
    private final RabbitTemplate rabbitTemplate;

    @RabbitListener(queues = MqConstant.QUEUE_FILE_PROCESS)
    public void processFileMessage(FileProcessMessage message) {
        log.info("【消费者】收到文件处理任务: taskId={}, fileId={}, fileName={}",
                message.getTaskId(), message.getFileId(), message.getFileName());
        try {
            // 1. 远程下载文件
            InputStream fileStream = fileDownloadService.download(message.getFileId());

            // 2. 流式解析 + 分批异步落库
            ParseResult parseResult = templateDataParser.parse(
                    fileStream, message.getTemplateId(), message.getFileName(), message.getTaskId());
            log.info("【消费者】解析完成: taskId={}, totalRows={}, validRows={}, errorRows={}",
                    message.getTaskId(), parseResult.totalRows(), parseResult.validRows(), parseResult.errorRows());

            // 3. 更新任务状态
            parseResultService.save(message.getTaskId(), parseResult);

            // 4. 发送数据分析消息到 Intelligence 模块
            DataAnalysisMessage analysisMsg = new DataAnalysisMessage();
            analysisMsg.setTaskId(message.getTaskId());
            analysisMsg.setFileId(message.getFileId());
            analysisMsg.setTemplateId(message.getTemplateId());
            analysisMsg.setTemplateSchema(parseResult.schema());
            analysisMsg.setUserId(message.getUserId());
            analysisMsg.setPromptContent(message.getPromptContent());
            analysisMsg.setTotalRows(parseResult.totalRows());
            analysisMsg.setValidRows(parseResult.validRows());
            analysisMsg.setErrorRows(parseResult.errorRows());
            rabbitTemplate.convertAndSend(MqConstant.EXCHANGE_DATA,
                    MqConstant.RK_DATA_ANALYSIS, analysisMsg);
            log.info("【消费者】已发送数据分析消息: taskId={}", message.getTaskId());

            log.info("【消费者】文件处理完成: taskId={}", message.getTaskId());
        } catch (Exception e) {
            log.error("【消费者】处理消息异常: taskId={}", message.getTaskId(), e);
            try {
                parseResultService.markFailed(message.getTaskId(), e.getMessage());
            } catch (Exception dbEx) {
                log.error("【消费者】更新任务失败状态异常: taskId={}", message.getTaskId(), dbEx);
            }
            throw new AmqpRejectAndDontRequeueException(e.getMessage(), e);
        }
    }
}
