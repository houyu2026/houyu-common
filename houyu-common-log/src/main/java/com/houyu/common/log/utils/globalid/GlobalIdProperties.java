package com.houyu.common.log.utils.globalid;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 全局ID生成器配置属性类
 * 用于配置机器码注册和ID生成相关参数
 */
@Data
@ConfigurationProperties(prefix = "houyu.global-id")
public class GlobalIdProperties {

    /**
     * 服务名称，用于生成Redis键前缀
     * 默认从spring.application.name获取
     */
    private String serviceName;

    /**
     * 服务端口号，用于计算机器码初始范围
     * 默认从server.port获取，未配置则使用8080
     */
    private Integer serverPort = 8080;

    /**
     * 分布式锁初始加锁时间（秒）
     * 默认30秒
     */
    private int lockLeaseTime = 30;

    /**
     * 分布式锁续期时间（秒）
     * 默认60秒
     */
    private int lockRenewalTime = 60;

    /**
     * 每段机器码数量
     * 默认50，即服务端口号 % 9000 * 10 开始的50个机器码
     */
    private int machineIdSegmentSize = 50;

    /**
     * 备用机器码起始值
     * 默认9050，当主段耗尽时使用
     */
    private int backupMachineIdStart = 9050;

    /**
     * 备用机器码结束值
     * 默认9999
     */
    private int backupMachineIdEnd = 9999;

    /**
     * 获取机器码Redis键前缀
     *
     * @param machineId 机器码
     * @param isBackup  是否为备用段
     * @return Redis键
     */
    public String getMachineIdKey(String serviceName, int machineId, boolean isBackup) {
        if (isBackup) {
            return String.format("machine_id_bak_%s_%04d", serviceName, machineId);
        }
        return String.format("machine_id_%s_%04d", serviceName, machineId);
    }

    /**
     * 获取分布式锁键
     *
     * @param serviceName 服务名称
     * @return 分布式锁键
     */
    public String getLockKey(String serviceName) {
        return String.format("machine_id_%s_lock", serviceName);
    }
}
