package com.hmdp.interceptor;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.hmdp.dto.UserDTO;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 刷新令牌拦截器
 *
 * @author CHEN
 * @date 2022/10/07
 */
public class RefreshTokenInterceptor implements HandlerInterceptor {
    private final StringRedisTemplate stringRedisTemplate;

    public RefreshTokenInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String requestURI = request.getRequestURI();
        System.out.println("🔄 [RefreshTokenInterceptor] 拦截请求: " + requestURI);

        //从请求头中获取token
        String token = request.getHeader("authorization");
        System.out.println("🔑 [RefreshTokenInterceptor] Token: " + (token != null ? "存在" : "不存在"));

        if (StringUtils.isEmpty(token)) {
            //不存在token
            System.out.println("❌ [RefreshTokenInterceptor] Token为空，跳过处理");
            return true;
        }

        //从redis中获取用户
        String redisKey = RedisConstants.LOGIN_USER_KEY + token;
        Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(redisKey);

        //用户不存在
        if (userMap.isEmpty()) {
            System.out.println("❌ [RefreshTokenInterceptor] Redis中未找到用户信息，key: " + redisKey);
            return true;
        }

        //hash转UserDTO存入ThreadLocal
        UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);
        UserHolder.saveUser(userDTO);
        System.out.println("✅ [RefreshTokenInterceptor] 用户信息已保存到ThreadLocal，用户ID: " + userDTO.getId());

        //token续命
        stringRedisTemplate.expire(redisKey, RedisConstants.LOGIN_USER_TTL, TimeUnit.MINUTES);
        System.out.println("⏰ [RefreshTokenInterceptor] Token已续期: " + RedisConstants.LOGIN_USER_TTL + "分钟");

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        System.out.println("🧹 [RefreshTokenInterceptor] 清理ThreadLocal用户信息");
        UserHolder.removeUser();
    }
}
