package com.jumper.common.cache.guava;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
@ConditionalOnProperty(prefix = "jumper.cache", value = "memoryType", havingValue = "GUAVA", matchIfMissing = true)
public class GuavaConfig {

    @Bean
    @Qualifier(value = "guavaCache")
    public Cache getGuavaCache(){
        Cache<Object, Object> build = CacheBuilder.newBuilder()
//                .concurrencyLevel(10)//并发级别，也就是可以同时操作的线程数
                .initialCapacity(10)//初始化容量
                .maximumSize(100000)//最大容量
                .softValues()//设置value为软应用，空间不足时缓存数据可以被回收
                .expireAfterWrite(1, TimeUnit.DAYS)//过期时间，写入数据后1天过期
                .build();
        return build;
    }
}
