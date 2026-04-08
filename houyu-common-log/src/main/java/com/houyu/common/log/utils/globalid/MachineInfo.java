package com.houyu.common.log.utils.globalid;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 机器信息相关DTO
 */
public class MachineInfoDto {

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
    @JsonSerialize(using = LocalDateTimeSerializer.class)
    private LocalDateTime registerTime;

    /**
     * 服务销毁时间，格式：yyyy-MM-dd hh:mm:ss SSS
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss SSS")
    @JsonSerialize(using = LocalDateTimeSerializer.class)
    private LocalDateTime destroyTime;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RedisMachineInfoDto {
        /**
         * Redis key
         */
        private String redisKey;

        /**
         * Redis value，机器信息DTO
         */
        private MachineInfoDto redisValue;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class MachineInfoDtoBuilder {
        private String machineName;
        private String machineIp;
        private LocalDateTime registerTime;
        private LocalDateTime destroyTime;

        public MachineInfoDto build() {
            MachineInfoDto dto = new MachineInfoDto();
            dto.machineName = this.machineName;
            dto.machineIp = this.machineIp;
            dto.registerTime = this.registerTime;
            dto.destroyTime = this.destroyTime;
            return dto;
        }
    }

    public static MachineInfoDtoBuilder builder() {
        return new MachineInfoDtoBuilder();
    }
}
