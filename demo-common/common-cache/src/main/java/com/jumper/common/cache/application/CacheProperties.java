package com.jumper.common.cache.application;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "jumper.cache")
@Data
public class CacheProperties{

    /**
     * 是否开启缓存
     */
    boolean enable;

    /**
     * 一级缓存类型枚举
     */
    CacheConstact.Memory memoryType;

    /**
     * 二级缓存类型
     */
    CacheConstact.Cluster clusterType;
}
