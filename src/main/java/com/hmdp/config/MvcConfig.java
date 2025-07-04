package com.hmdp.config;

import com.hmdp.interceptor.LoginInterceptor;
import com.hmdp.interceptor.RefreshTokenInterceptor;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.annotation.Resource;

/**
 * mvc配置
 *
 * @author CHEN
 * @date 2022/10/07
 */
@Configuration
public class MvcConfig implements WebMvcConfigurer {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        System.out.println("🚀 [MvcConfig] 开始注册拦截器...");

        //Token续命拦截器 - 优先级最高
        registry
                .addInterceptor(new RefreshTokenInterceptor(stringRedisTemplate))
                .addPathPatterns("/**")
                .order(0);
        System.out.println("✅ [MvcConfig] RefreshTokenInterceptor已注册 - order: 0, 拦截路径: /**");

        //登陆拦截器 - 优先级较低
        registry
                .addInterceptor(new LoginInterceptor())
                .excludePathPatterns("/user/code"
                        , "/user/login"
                        , "/blog/hot"
                        , "/shop/**"
                        , "/shop-type/**"
                        , "/upload/**"
                        , "/voucher/**"
                )
                .order(1);
        System.out.println("✅ [MvcConfig] LoginInterceptor已注册 - order: 1, 排除路径: /user/code, /user/login, /blog/hot, /shop/**, /shop-type/**, /upload/**, /voucher/**");

        System.out.println("🎯 [MvcConfig] 拦截器注册完成！执行顺序: RefreshTokenInterceptor(0) -> LoginInterceptor(1)");
    }
}
