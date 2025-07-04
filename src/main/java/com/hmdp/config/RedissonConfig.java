//package com.hmdp.config;
//
//import org.redisson.Redisson;
//import org.redisson.api.RedissonClient;
//import org.redisson.config.Config;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//
///**
// * redisson配置
// *
// * @author CHEN
// * @date 2022/10/10
// */
//@Configuration
//public class RedissonConfig {//
//    @Value("${spring.redis.host}")
//    private String host;
//    @Value("${spring.redis.port}")
//    private String port;
//    @Value("${spring.redis.password}")
//    private String password;
//    @Bean
//    public RedissonClient redissonClient(){
//        //配置
//        Config config=new Config();
//        config.useSingleServer().setAddress("redis://"+host+":"+port).setPassword(password);
//        //创建对并且返回
//        return Redisson.create(config);
//    }
//
//}
package com.hmdp.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory; // 导入 LoggerFactory

/**
 * redisson配置
 *
 * @author CHEN
 * @date 2022/10/10
 */
@Configuration
public class RedissonConfig {

    // 获取 Logger 实例
    private static final Logger log = LoggerFactory.getLogger(RedissonConfig.class);

    @Value("${spring.redis.host}")
    private String host;
    @Value("${spring.redis.port}")
    private String port;
    @Value("${spring.redis.password}")
    private String password;

    @Bean
    public RedissonClient redissonClient(){
        // 使用参数化日志，避免字符串拼接的性能开销，且在DEBUG级别下才真正拼接
        log.debug("Redisson 连接到 Redis，主机: {}, 端口: {}, 密码: [{}]", host, port, password); // 使用 {} 作为占位符

        // 配置
        Config config = new Config();
        config.useSingleServer().setAddress("redis://" + host + ":" + port).setPassword(password);

        // 创建并返回
        return Redisson.create(config);
    }
}