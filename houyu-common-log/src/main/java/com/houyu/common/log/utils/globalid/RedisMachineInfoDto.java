package com.houyu.common.log.utils.globalid;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;


/**
 * 机器信息相关DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RedisMachineInfoDto {

    private String redisKey;

    private MachineInfoDto redisValue;


    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    static class MachineInfoDto {
        /**
         * 机器id
         */
        private String machineId;

        /**
         * 机器名称
         */
        private String machineName;

        /**
         * 机器IP
         */
        private String machineIp;

        /**
         * 注册时间，格式：yyyy-MM-dd hh:mm:ss SSS
         */
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss SSS")
        private LocalDateTime registerTime;

        /**
         * 服务销毁时间，格式：yyyy-MM-dd hh:mm:ss SSS
         */
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss SSS")
        private LocalDateTime destroyTime;
    }
}


