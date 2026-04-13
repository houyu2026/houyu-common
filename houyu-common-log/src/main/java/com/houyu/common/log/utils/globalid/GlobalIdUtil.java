package com.houyu.common.log.utils.globalid;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;


/**
 全局唯一id，生成算法如下
 1) 一共19位，10位时间 + 4位机器码 + 4位自增序列 + 1位标志位
 2) 10位时间格式为yyMMddHHmm，即2位年+2位月+2位日+2位时+2位分
 3) 4位机器码，取值范围为0000~9999，需要在整个集群保持唯一，在服务启动完成之后将机器码注册到redis中
    A、使用redisson客户端操作redis
    B、在注册期间，使用redisson分布式锁，分布式锁key生成规则为 machine_id_{取当前服务名称spring.application.name}_lock，机器码注册到redis成功之后再释放该锁。初始加锁时间30s，续期60s
    C、机器码生成规则，不允许重复
       a、取值范围：服务端口号 % 9000 * 10 ~ (服务端口号 % 9000 * 10 + 50)
       b、示例数据
         服务端口号 取值范围
         9000     0000 ~ 0049
         9005 	  0050 ~ 0099
       c、兜底机制：即当前服务pod数量超过50则启用备用段9050 ~ 9999
    D、机器码在redis存储
        a、key生成规则：machine_id_{取当前服务名称spring.application.name}_{机器码}，
 如果是备用段则key生成规则：machine_id_bak_{取当前服务名称spring.application.name}_{机器码}
        b、value生成规则，json文本
 {"machineId": "生成的机器码", "machineName": "取当前机器名称", "machineIp": "取当前机器ip", "registerTime": "注册时间，取当前时间精确到毫秒，格式为yyyy-MM-dd hh:mm:ss SSS", "destroyTime": "服务销毁时间"}
 4) 4位自增序列，取值范围为0000~9999，需要在整个服务进程保持唯一
    A、兜底机制，即当前时间内需要产生的id数量超过10000个，则将10位时间后2位修正为60继续，如果仍然不够则修正为61，依次类推，最大只能修正为99
 5) 1位标志位，由调用方传入，取值范围为1~9，如果没有传入或传入值非法则默认为0

 注意：为了跟“全局唯一id”配合，每个微服务的port取值要求如下
 1、取值范围9000~9900，需要剔除“9200 / 9300（ES）”、“9411（Zipkin）”、“9999（很多管理后台默认）”
 即微服务数量不能超过（9900-9000）/ 5  - 4个需要剔除端口 = 136
 2、每次递增5，这样支持每个微服务最多可以扩容到50个pod
 */
@Slf4j
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
            // 等待30秒后重试
            if (timestamp == null) {
                try {
                    Thread.sleep(30 * 1000);
                } catch (InterruptedException e) {
                    log.error("generateSequence sleep error", e);
                }
            }
            sequence = generatorSequence.nextSequence(timestamp);
        }
        
        return String.format("%04d", sequence);
    }
}