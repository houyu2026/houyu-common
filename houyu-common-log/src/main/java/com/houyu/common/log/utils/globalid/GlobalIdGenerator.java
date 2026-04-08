package com.houyu.common.log.utils.globalid;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

@Component
public class GlobalIdGenerator {
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyMMddHHmm");
    private static final int MAX_MINUTE = 59;

    @Autowired
    private MachineIdManager machineIdManager;

    private final SequenceGenerator sequenceGenerator = new SequenceGenerator();

    public String generateId() {
        return generateId(0);
    }

    public String generateId(int flag) {
        // 确保标志位在有效范围内
        int validFlag = flag >= 1 && flag <= 9 ? flag : 0;

        String timestamp = generateTimestamp();
        String machineCode = machineIdManager.getMachineId();
        String sequence = generateSequence(timestamp);

        return timestamp + machineCode + sequence + validFlag;
    }

    private String generateTimestamp() {
        LocalDateTime now = LocalDateTime.now();
        return now.format(TIME_FORMATTER);
    }

    private String generateSequence(String timestamp) {
        // 检查序列是否耗尽
        if (sequenceGenerator.isSequenceExhausted(timestamp)) {
            // 序列耗尽，修正时间戳
            timestamp = adjustTimestamp(timestamp);
        }

        int sequence = sequenceGenerator.nextSequence(timestamp);
        return String.format("%04d", sequence);
    }

    private String adjustTimestamp(String originalTimestamp) {
        // 解析时间戳
        int year = Integer.parseInt(originalTimestamp.substring(0, 2)) + 2000;
        int month = Integer.parseInt(originalTimestamp.substring(2, 4));
        int day = Integer.parseInt(originalTimestamp.substring(4, 6));
        int hour = Integer.parseInt(originalTimestamp.substring(6, 8));
        int minute = Integer.parseInt(originalTimestamp.substring(8, 10));

        LocalDateTime dateTime = LocalDateTime.of(year, month, day, hour, minute);

        // 增加到下一分钟
        dateTime = dateTime.plus(1, ChronoUnit.MINUTES);

        // 生成新的时间戳
        String newTimestamp = dateTime.format(TIME_FORMATTER);
        
        // 检查序列是否仍然耗尽
        if (sequenceGenerator.isSequenceExhausted(newTimestamp)) {
            // 如果仍然耗尽，使用特殊标记
            minute = Integer.parseInt(newTimestamp.substring(8, 10));
            if (minute <= MAX_MINUTE) {
                // 修正为60以上
                newTimestamp = newTimestamp.substring(0, 8) + String.format("%02d", minute + 1);
            } else {
                // 继续递增
                newTimestamp = newTimestamp.substring(0, 8) + String.format("%02d", minute + 1);
            }
        }

        return newTimestamp;
    }
}
