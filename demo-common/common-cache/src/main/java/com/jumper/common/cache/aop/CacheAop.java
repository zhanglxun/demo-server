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
