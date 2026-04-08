package com.houyu.common.log.utils.globalid;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class GlobalIdUtil {

    @Autowired
    private GeneratorMachineId generatorMachineId;

    @Autowired
    private GeneratorSequence generatorSequence;

    @Autowired
    private GeneratorTimestamp generatorTimestamp;

    /**
     * 生成全局唯一ID
     * @return 19位全局唯一ID
     */
    public String nextId() {
        return nextIdWithFlag(0);
    }

    /**
     * 生成带标志位的全局唯一ID
     * @param flag 标志位，取值范围1~9，非法值默认为0
     * @return 19位全局唯一ID
     */
    public String nextIdWithFlag(int flag) {
        // 确保标志位在有效范围内
        int validFlag = flag >= 1 && flag <= 9 ? flag : 0;

        String timestamp = generatorTimestamp.generateTimestamp();
        String machineCode = generatorMachineId.getMachineId();
        String sequence = generateSequence(timestamp);

        return timestamp + machineCode + sequence + validFlag;
    }

    /**
     * 生成多个全局唯一ID
     * @param count 生成数量
     * @return ID列表
     */
    public List<String> nextIdList(int count) {
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            ids.add(nextId());
        }
        return ids;
    }

    /**
     * 生成多个带标志位的全局唯一ID
     * @param count 生成数量
     * @param flag 标志位，取值范围1~9，非法值默认为0
     * @return ID列表
     */
    public List<String> nextIdListWithFlag(int count, int flag) {
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            ids.add(nextIdWithFlag(flag));
        }
        return ids;
    }

    private String generateSequence(String timestamp) {
        // 尝试生成序列
        Integer sequence = generatorSequence.nextSequence(timestamp);
        int adjustmentCount = 0;
        
        // 序列耗尽，调整时间戳
        while (sequence == null) {
            timestamp = generatorTimestamp.adjustTimestamp(timestamp, adjustmentCount++);
            sequence = generatorSequence.nextSequence(timestamp);
        }
        
        return String.format("%04d", sequence);
    }
}