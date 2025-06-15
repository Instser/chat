package com.instser.chat.service.serviceimpl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.instser.chat.dao.AiModelDao;
import com.instser.chat.entity.AiModel;
import com.instser.chat.service.AiModelService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * AI模型服务实现类
 *
 * @author instser
 * @since 2024-01-01
 */
@Slf4j
@Service
public class AiModelServiceImpl extends ServiceImpl<AiModelDao, AiModel> implements AiModelService {

    @Override
    public AiModel getModelByCode(String modelCode) {
        return baseMapper.selectByModelCode(modelCode);
    }

    @Override
    public List<AiModel> getAvailableModels() {
        return baseMapper.selectAvailableModels();
    }

    @Override
    public void initDefaultModels() {
        // 检查是否已有数据
        if (this.count() > 0) {
            return;
        }

        // 初始化默认模型
        AiModel gpt4 = new AiModel();
        gpt4.setModelCode("gpt-4");
        gpt4.setModelName("GPT-4");
        gpt4.setDescription("OpenAI最先进的语言模型");
        gpt4.setProvider("OpenAI");
        gpt4.setVersion("1.0");
        gpt4.setTokenLimit(8192);
        gpt4.setPricePer1kTokens(new BigDecimal("0.03"));
        gpt4.setIsAvailable(1);
        gpt4.setCreatedTime(LocalDateTime.now());
        gpt4.setUpdateTime(LocalDateTime.now());

        AiModel gpt35 = new AiModel();
        gpt35.setModelCode("gpt-3.5-turbo");
        gpt35.setModelName("GPT-3.5 Turbo");
        gpt35.setDescription("快速且经济的语言模型");
        gpt35.setProvider("OpenAI");
        gpt35.setVersion("1.0");
        gpt35.setTokenLimit(4096);
        gpt35.setPricePer1kTokens(new BigDecimal("0.002"));
        gpt35.setIsAvailable(1);
        gpt35.setCreatedTime(LocalDateTime.now());
        gpt35.setUpdateTime(LocalDateTime.now());

        AiModel qwen = new AiModel();
        qwen.setModelCode("qwen3-235b-a22b");
        qwen.setModelName("通义千问");
        qwen.setDescription("阿里云通义千问大语言模型");
        qwen.setProvider("Alibaba");
        qwen.setVersion("3.0");
        qwen.setTokenLimit(8192);
        qwen.setPricePer1kTokens(new BigDecimal("0.001"));
        qwen.setIsAvailable(1);
        qwen.setCreatedTime(LocalDateTime.now());
        qwen.setUpdateTime(LocalDateTime.now());

        this.saveBatch(List.of(gpt4, gpt35, qwen));
        log.info("默认AI模型初始化完成");
    }
}
