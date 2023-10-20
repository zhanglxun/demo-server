package com.jumper.common.cache.redis;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(prefix = "jumper.cache", value = "clusterType", havingValue = "REDIS", matchIfMissing = true)
public class RedisConfig {

   /* @Bean("cacheRedisProperties")
    @ConditionalOnBean
    public RedisProperties getRedisProperties(RedisProperties redisProperties){
        RedisProperties cacheRedisProperties = new RedisProperties();
        BeanUtils.copyProperties(redisProperties, cacheRedisProperties);
        cacheRedisProperties.setDatabase(15);
        return cacheRedisProperties;
    }

    @Bean("cacheRedisConnectionFactory")
    @ConditionalOnBean
    public RedisConnectionFactory getRedisConnectionFactory(RedisConnectionFactory redisConnectionFactory){
        LettuceConnectionFactory lettuceConnectionFactory = new LettuceConnectionFactory();
    }*/

    /**
     * 配置redis的key和value的序列化方式
     *
     * @param factory
     * @return
     */
   /* @Bean(name = "cacheRedisTemplate")
    public RedisTemplate getRedisTemplate(RedisConnectionFactory factory) {
        //暂时改成注入一个新对象,在同时引入common-redis和common-cache时，不影响到common-redis的序列化
        RedisTemplate redisTemplate = new RedisTemplate();
        redisTemplate.setConnectionFactory(factory);
        redisTemplate.setKeySerializer(new JdkSerializationRedisSerializer());
        redisTemplate.setValueSerializer(new JdkSerializationRedisSerializer());
        redisTemplate.afterPropertiesSet();
        return redisTemplate;
    }*/
}
