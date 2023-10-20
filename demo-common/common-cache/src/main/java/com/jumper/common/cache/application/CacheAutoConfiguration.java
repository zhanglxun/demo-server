package com.jumper.common.cache.application;

import com.jumper.common.cache.aop.CacheAop;
import com.jumper.common.cache.guava.GuavaMemoryCacheHandler;
import com.jumper.common.cache.handler.ClusterCacheHandler;
import com.jumper.common.cache.handler.MemoryCacheHandler;
import com.jumper.common.cache.redis.RedisClusterCacheHandler;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * 相关缓存的自动配置
 */
@Configuration
@ConditionalOnProperty(prefix = "jumper.cache", value = "enable", havingValue = "true", matchIfMissing = true)
@ComponentScan("com.jumper.common.cache")
public class CacheAutoConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "jumper.cache", value = "memoryType", havingValue = "GUAVA", matchIfMissing = true)
    public MemoryCacheHandler getGuavaMemoryCacheHandler(){
        return new GuavaMemoryCacheHandler<>();
    }


    @Bean
    @ConditionalOnProperty(prefix = "jumper.cache", value = "clusterType", havingValue = "REDIS", matchIfMissing = true)
    public ClusterCacheHandler getRedisClusterCacheHandler(){
        return new RedisClusterCacheHandler<>();
    }


    /**
     * 配置aop
     * @return
     */
    @Bean
    public CacheAop getCacheAop(){
        return new CacheAop();
    }
}
