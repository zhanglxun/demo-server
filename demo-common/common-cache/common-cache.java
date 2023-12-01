package com.jumper.common.cache.annotation;

import java.lang.annotation.*;

/**
 * 删除缓存
 */
@Documented
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface CacheDelete {

    /**
     * 需要删除的key
     *
     * @return
     */
    String[] key() default "";

    /**
     * 是否删除所有缓存信息，key参数会失效
     *
     * @return
     */
    boolean isDeleteAll() default false;

    /**
     * 删除缓存的包含条件（符合该条件的数据会从缓存中删除）
     *
     * @return
     */
    String condition() default "";

    /**
     * 删除缓存的排除条件（符合该条件的数据不会从缓存中删除，优先级高于condition）
     *
     * @return
     */
    String unless() default "";

    /**
     * 是否为前置删除（在执行业务之前删除缓存，默认为后置删除）
     *
     * @return
     */
    boolean beforeInvocation() default false;

    /**
     * 是否通过正则表达式删除key
     *
     * @return
     */
    boolean deletePattern() default false;
}

package com.jumper.common.cache.annotation;

import java.lang.annotation.*;
import java.util.concurrent.TimeUnit;

/**
 * 获取缓存数据
 */
@Documented
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface CacheGet {

    /**
     * 缓存的key
     * @return
     */
    String key();

    /**
     * 缓存的包含条件（符合该条件的数据会从缓存中查询）
     * @return
     */
    String condition() default "";

    /**
     * 缓存的排除条件（符合该条件的数据不会从缓存中查询，优先级高于condition）
     * @return
     */
    String unless() default "";

    /**
     * 缓存的过期时间，默认300秒
     * @return
     */
    long timeout() default 300;

    /**
     * 是否随机缓存过期时间，默认不随机，一旦指定该属性，则timeout会失效
     * @return
     */
    boolean isRandomTimeout() default false;

    /**
     * 最大的随机时间1天，isRandomTimeout为true时生效
     * @return
     */
    long maxTimeout() default 60*60*24;

    /**
     * 最小的随机时间100秒，isRandomTimeout为true时生效
     * @return
     */
    long minTimeout() default 100;

    /**
     * 缓存相关时间单位，默认为秒
     * @return
     */
    TimeUnit unit() default TimeUnit.SECONDS;
}

package com.jumper.common.cache.aop;

import com.jumper.common.cache.annotation.CacheDelete;
import com.jumper.common.cache.annotation.CacheGet;
import com.jumper.common.cache.handler.ClusterCacheHandler;
import com.jumper.common.cache.handler.MemoryCacheHandler;
import com.jumper.core.utiles.SpelExpressionUtils;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

@Aspect
@Slf4j
public class CacheAop {

    /**
     * 内存缓存的实现类
     */
    @Autowired
    private MemoryCacheHandler memoryCacheHandler;

    /**
     * 二级缓存的实现类
     */
    @Autowired
    private ClusterCacheHandler clusterCacheHandler;


    /**
     * 缓存读取的环绕增强
     *
     * @param joinPoint
     * @return
     */
    @Around("@annotation(com.jumper.common.cache.annotation.CacheGet)")
    public Object cacheGetAop(ProceedingJoinPoint joinPoint) {
        //获取注解上的key
        Method method = getMethod(joinPoint);
        //获得方法执行参数
        Object[] params = joinPoint.getArgs();
        //读取数据前先从内存缓存中读取
        CacheGet cacheGet = method.getAnnotation(CacheGet.class);
        //获得unless
        String unless = cacheGet.unless();
        Boolean unlessFlag = SpelExpressionUtils.parserSpel(method, params, unless, Boolean.class, false);
        log.debug("[CACHE GET] - unless表达式 - {}  结果 - {}", unless, unlessFlag);
        //获得condition
        String condition = cacheGet.condition();
        Boolean conditionFlag = SpelExpressionUtils.parserSpel(method, params, condition, Boolean.class, true);
        log.debug("[CACHE GET] - condition表达式 - {}  结果 - {}", condition, conditionFlag);
        //获取缓存的过期时间
        Long timeout = getTimeout(cacheGet);
        TimeUnit unit = cacheGet.unit();
        //方法的返回值
        Object value = null;
        //如果unless为false，并且condition为true时，才会进行缓存读取，否则直接执行目标方法
        if (!unlessFlag && conditionFlag) {
            //获得key
            String key = cacheGet.key();
            //解析key中的spel表达式
            key = SpelExpressionUtils.parserSpel(method, params, key, String.class, null);
            log.debug("[CACHE GET] - 缓存的key - {}", key);
            //从缓存中获取数据
            value = getCache(key, timeout, unit);

            //从分布式缓存中获取的数据为空
            if (Objects.isNull(value)) {
                //加锁访问后端数据
                synchronized (key.intern()) {
                    //双重锁判定
                    value = getCache(key, timeout, unit);
                    if (Objects.isNull(value)) {
                        //直接调用目标方法
                        try {
                            value = joinPoint.proceed();
                            log.debug("[CACHE GET] - 执行目标方法获得数据 - {}", value);
                            if (Objects.isNull(value)) return value;
                            log.debug("[CACHE GET] - 开始进行缓存重建.....");
                            //进行缓存重建
                            if (timeout == -1) {
                                memoryCacheHandler.putCache(key, value);
                                clusterCacheHandler.putCache(key, value);
                            } else {
                                memoryCacheHandler.putCache(key, value, timeout, unit);
                                clusterCacheHandler.putCache(key, value, timeout, unit);
                            }

                        } catch (Throwable e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        } else {
            try {
                value = joinPoint.proceed();
                log.debug("[CACHE GET] - 不符合缓存条件，直接执行目标方法 - {}", value);
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }

        return value;
    }

    /**
     * 删除缓存的环绕增强
     *
     * @param joinPoint
     * @return
     */
    @Around("@annotation(com.jumper.common.cache.annotation.CacheDelete)")
    public Object cacheDelAop(ProceedingJoinPoint joinPoint) throws Throwable {
        //获取注解上的key
        Method method = getMethod(joinPoint);

        //获得方法执行参数
        Object[] params = joinPoint.getArgs();

        //获取缓存删除注解
        CacheDelete cacheDelete = method.getAnnotation(CacheDelete.class);

        //是否清空全部
        boolean deleteAll = cacheDelete.isDeleteAll();

        //是否前置删除
        boolean flag = cacheDelete.beforeInvocation();

        //获得unless
        String unless = cacheDelete.unless();
        Boolean unlessFlag = SpelExpressionUtils.parserSpel(method, params, unless, Boolean.class, false);
        log.debug("[CACHE DELETE] - unless表达式 - {}  结果 - {}", unless, unlessFlag);

        //获得condition
        String condition = cacheDelete.condition();
        Boolean conditionFlag = SpelExpressionUtils.parserSpel(method, params, condition, Boolean.class, true);
        log.debug("[CACHE DELETE] - condition表达式 - {}  结果 - {}", condition, conditionFlag);
        //是否通过正则表达式删除
        boolean deletePattern = cacheDelete.deletePattern();
        //获得key
        String[] keys = cacheDelete.key();
        //解析key中的spel表达式
        List<String> keysList = new ArrayList<>();
        for (String key : keys) {
            //解析key用List接收,用于获取的方法参数本身就是已经生成的多个key的情况
            List k = SpelExpressionUtils.parserSpel(method, params, key, List.class, null);
            log.debug("[CACHE DELETE] - 需要删除的key - {}", k);
            keysList.addAll(k);
        }

        //前置删除
        if (flag && !unlessFlag && conditionFlag) {
            log.debug("[CACHE DELETE] - 开始进行前置删除......");
            deleteCache(keysList, deletePattern);
        }

        //调用目标方法
        try {
            Object result = joinPoint.proceed();

            //后置删除
            if (!flag && !unlessFlag && conditionFlag) {
                log.debug("[CACHE DELETE] - 开始进行后置删除......");
                deleteCache(keysList, deletePattern);
            }

            return result;
        } catch (Throwable e) {
            e.printStackTrace();
            throw e;
        }
    }

    private void deleteCache(List<String> keysList, boolean deletePattern) {
        for (String key : keysList) {
            boolean result = deletePattern ? memoryCacheHandler.deleteCachePattern(key) : memoryCacheHandler.deleteCache(key);
            //调用方法删除其他节点缓存
            memoryCacheHandler.deleteClusterCache(key, deletePattern);
            result = deletePattern ? clusterCacheHandler.deleteCachePattern(key) : clusterCacheHandler.deleteCache(key);
        }
    }

    /**
     * 从缓存中获取数据
     *
     * @param key
     * @return
     */
    private Object getCache(String key, long timeout, TimeUnit unit) {
        //从内存缓存中获取数据
        Object value = memoryCacheHandler.getCache(key);
        log.debug("[CACHE GET] - 从内存缓存中获取数据 - {}", value);
        //如果内存缓存为空
        if (Objects.isNull(value)) {
            //从分布式缓存中获取数据
            value = clusterCacheHandler.getCache(key);
            log.debug("[CACHE GET] - 从分布式缓存中获取数据 - {}", value);

            if (!Objects.isNull(value)) {
                //重建内存缓存
                if (timeout == -1) {
                    memoryCacheHandler.putCache(key, value);
                } else {
                    memoryCacheHandler.putCache(key, value, timeout, unit);
                }
            }
        }
        return value;
    }

    /**
     * 获取过期时间
     *
     * @return
     */
    private Long getTimeout(CacheGet cacheGet) {
        boolean randomTimeout = cacheGet.isRandomTimeout();

        //如果设置了随机时间
        if (randomTimeout) {
            //获取最大值
            long maxTimeout = cacheGet.maxTimeout();
            //获取最小值
            long minTimeout = cacheGet.minTimeout();

            return (long) (Math.random() * (maxTimeout - minTimeout)) + minTimeout;
        } else {
            //如果没有设置随机时间
            return cacheGet.timeout();
        }
    }

    /**
     * 获得目标方法
     *
     * @return
     */
    private Method getMethod(ProceedingJoinPoint joinPoint) {

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
//        if (method.getDeclaringClass().isInterface()) {
//            try {
//                method = joinPoint
//                        .getTarget()
//                        .getClass()
//                        .getDeclaredMethod(joinPoint.getSignature().getName(),
//                                method.getParameterTypes());
//            } catch (SecurityException | NoSuchMethodException e) {
//                throw new RuntimeException(e);
//            }
//        }
        return method;
    }
}

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
package com.jumper.common.cache.application;

public class CacheConstact {

    /**
     * 内存缓存类型枚举
     */
    public enum Memory {
        GUAVA;
    }

    /**
     * 分布式缓存类型枚举
     */
    public enum Cluster {
        REDIS;
    }
}

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

package com.jumper.common.cache.guava;

import cn.hutool.core.util.ReUtil;
import com.google.common.cache.Cache;
import com.jumper.common.cache.handler.MemoryCacheHandler;
import lombok.Data;
import lombok.experimental.Accessors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/**
 * 基于JVM内存的缓存策略
 */
public class GuavaMemoryCacheHandler<K extends Serializable, V> extends MemoryCacheHandler<K, V> {

    @Autowired
    @Qualifier(value = "guavaCache")
    private Cache cache;
    @Value("${jumper.cache.maximumMemory:50}")
    private Integer maximumMemory;
    @Autowired
    private Executor executor;

    @Override
    public boolean putCache(K key, V value) {
        return this.putCache(key, value, -1L, TimeUnit.SECONDS);
    }

    @Override
    public boolean putCache(K key, V value, Long timeOut, TimeUnit unit) {
        if (!isMaximumMemory()) {
            return true;
        }
        CacheValue cacheValue = new CacheValue()
                .setValue(value)
                .setCreateTime(System.currentTimeMillis())
                .setTimeout(timeOut)
                .setUnit(unit);
        cache.put(key, cacheValue);
        return true;
    }

    @Override
    public V getCache(K key) {
        CacheValue cacheValue = (CacheValue) cache.getIfPresent(key);

        if (cacheValue != null) {
            Long timeout = cacheValue.getTimeout();
            //如果为-1表示永生
            if (timeout == -1)
                return (V) cacheValue.getValue();

            //判断当前是否超时
            if (cacheValue.getCreateTime() + cacheValue.getUnit().toMillis(timeout) < System.currentTimeMillis()) {
                //已经超时
                deleteCache(key);
            } else {
                //未超时
                return (V) cacheValue.getValue();
            }
        }
        return null;
    }

    @Override
    public boolean deleteCache(K key) {
        cache.invalidate(key);
        return true;
    }

    /**
     * 通过正则表达式删除key
     *
     * @param key
     * @return
     */
    @Override
    public boolean deleteCachePattern(K key) {
        CompletableFuture.runAsync(() -> {
            String patten = String.valueOf(key);
            List<String> keys = new ArrayList<>();
            //取出缓存中的key
            ConcurrentMap concurrentMap = cache.asMap();
            concurrentMap.keySet().forEach(cacheKey -> {
                String deleteKey = String.valueOf(cacheKey);
                //通过正则校验，缓存中的key与指定key是否匹配,匹配则放入list中
                boolean match = ReUtil.contains(patten, deleteKey);
                if (match) {
                    keys.add(deleteKey);
                }
            });
            //删除匹配到的所有key
            if (keys.size() > 0) {
                cache.invalidateAll(keys);
            }
        }, executor);
        return true;
    }

    /**
     * 缓存对象
     *
     * @param <V>
     */
    @Data
    @Accessors(chain = true)
    private static class CacheValue<V> {
        //缓存的值
        V value;
        //超时时间
        Long timeout;
        //单位
        TimeUnit unit;
        //创建时间
        Long createTime;
    }

    /**
     * 判断JVM剩余内存是否到达指定的最大内存
     *
     * @return
     */
    private boolean isMaximumMemory() {
        //JVM剩余内存
        long freeMemory = Runtime.getRuntime().freeMemory();
        return freeMemory > maximumMemory * 1024 * 1024;
    }
}
package com.jumper.common.cache.handler;

import java.io.Serializable;
import java.util.concurrent.TimeUnit;

/**
 * 基础数据缓存操作规范
 */
public interface BaseCacheHandler<K extends Serializable, V> {

    /**
     * 添加缓存
     *
     * @param key
     * @param value
     * @return
     */
    boolean putCache(K key, V value);

    /**
     * 添加缓存并且携带超时时间
     *
     * @param key
     * @param value
     * @param timeOut
     * @param unit
     * @return
     */
    boolean putCache(K key, V value, Long timeOut, TimeUnit unit);

    /**
     * 获取缓存
     *
     * @param key
     * @return
     */
    V getCache(K key);

    /**
     * 删除缓存
     *
     * @param key
     * @return
     */
    boolean deleteCache(K key);

    /**
     * 通过正则表达式删除key
     *
     * @param key
     * @return
     */
    boolean deleteCachePattern(K key);
}
package com.jumper.common.cache.handler;

import java.io.Serializable;

/**
 * 分布式缓存接口 - 二级缓存
 */
public abstract class ClusterCacheHandler<K extends Serializable, V>
        implements BaseCacheHandler<K, V> {
}
package com.jumper.common.cache.handler;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 描述：
 *
 * @author huangbin
 * @version 1.0
 * @date 2022/4/24 10:09
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeleteCacheMessage implements Serializable {
    private static final long serialVersionUID = -3444217464590115260L;
    private String key;
    private boolean deletePattern;
}
package com.jumper.common.cache.handler;

import com.jumper.common.event.apply.handle.EventHandler;
import com.jumper.common.event.apply.handle.annotation.EventType;
import com.jumper.common.event.framework.message.EventMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
@Slf4j
@EventType("cache-delete")
public class MemoryCacheEventHandler implements EventHandler<DeleteCacheMessage> {

    @Autowired
    private MemoryCacheHandler memoryCacheHandler;

    @Override
    public void eventHandler(DeleteCacheMessage message, EventMessage eventMessage) {
        log.debug("接收到删除key：{}",message.getKey());
        boolean result = message.isDeletePattern() ? memoryCacheHandler.deleteCachePattern(message.getKey()) : memoryCacheHandler.deleteCache(message.getKey());

    }
}
package com.jumper.common.cache.handler;

import com.jumper.common.event.apply.utils.EventUtils;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.Serializable;

/**
 * 内存缓存规范 - 一级缓存
 *
 * @param <K>
 * @param <V>
 */
public abstract class MemoryCacheHandler<K extends Serializable, V> implements BaseCacheHandler<K, V> {

    @Autowired(required = false)
    private EventUtils eventUtil;

    /**
     * 移除集群的内存缓存
     *
     * @param key
     * @return
     */
    public boolean deleteClusterCache(K key, boolean deletePattern) {
        if (eventUtil != null) {
            DeleteCacheMessage deleteCacheMessage = DeleteCacheMessage.builder().key(String.valueOf(key)).deletePattern(deletePattern).build();
            eventUtil.sendMsg("cache-delete", deleteCacheMessage, false);
            return true;
        }
        return false;
    }
}
package com.jumper.common.cache.redis;

import com.jumper.common.cache.handler.ClusterCacheHandler;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;

import java.io.Serializable;
import java.util.concurrent.TimeUnit;

/**
 * 基于Redis实现的分布式缓存（二级缓存）
 *
 * @param <K>
 * @param <V>
 */
public class RedisClusterCacheHandler<K extends Serializable, V> extends ClusterCacheHandler<K, V> {

    /**
     * Redis模板
     */
    @Autowired
    private RedisTemplate redisTemplate;
    @Autowired
    private RedissonClient redissonClient;

    @Override
    public boolean putCache(K key, V value) {
        //设置缓存不设置过期时间
        redisTemplate.opsForValue().set(key, value);
        return true;
    }

    @Override
    public boolean putCache(K key, V value, Long timeOut, TimeUnit unit) {
        //设置缓存并且设置过期时间
        redisTemplate.opsForValue().set(key, value, timeOut, unit);
        return true;
    }

    @Override
    public V getCache(K key) {
        return (V) redisTemplate.opsForValue().get(key);
    }

    @Override
    public boolean deleteCache(K key) {
        return redisTemplate.delete(key);
    }

    /**
     * 通过正则表达式删除key
     *
     * @param key
     * @return
     */
    @Override
    public boolean deleteCachePattern(K key) {
        redissonClient.getKeys().deleteByPatternAsync(String.valueOf(key));
        return true;
    }
}
package com.jumper.common.cache.redis;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(prefix = "jumper.cache", value = "clusterType", havingValue = "REDIS", matchIfMissing = true)
public class RedisConfig {

  
}



