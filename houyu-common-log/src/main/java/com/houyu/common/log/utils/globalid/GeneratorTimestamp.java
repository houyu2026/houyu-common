package com.houyu.common.log.utils.globalid;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Component
public class GeneratorTimestamp {
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyMMddHHmm");
    private static final int MAX_ADJUSTMENTCOUNT = 39;

    /**
     * 生成10位时间戳，格式：yyMMddHHmm
     */
    public String generateTimestamp() {
        LocalDateTime now = LocalDateTime.now();
        return now.format(TIME_FORMATTER);
    }

    /**
     * 序列耗尽时调整时间戳
     * @param originalTimestamp 原始时间戳
     * @param adjustmentCount 调整次数，从0开始
     * @return 调整后的时间戳
     */
    public String adjustTimestamp(String originalTimestamp, int adjustmentCount) {
        if (adjustmentCount > MAX_ADJUSTMENTCOUNT) {
            return null;
        }

        // 基础时间戳
        String baseTimestamp = originalTimestamp.substring(0, 8);
        
        // 计算调整后的分钟数
        int adjustedMinute = 60 + adjustmentCount;
        
        // 生成新的时间戳
        return baseTimestamp + String.format("%02d", adjustedMinute);
    }
}