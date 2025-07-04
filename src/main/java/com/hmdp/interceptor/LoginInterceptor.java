package com.hmdp.interceptor;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 登录拦截器
 *
 * @author CHEN
 * @date 2022/10/07
 */
public class LoginInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String requestURI = request.getRequestURI();
        System.out.println("🔐 [LoginInterceptor] 检查登录状态: " + requestURI);

        //获取用户
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            //不存在用户 拦截
            System.out.println("❌ [LoginInterceptor] 用户未登录，拦截请求: " + requestURI);
            response.setStatus(401);
            return false;
        }

        //存在用户放行
        System.out.println("✅ [LoginInterceptor] 用户已登录，放行请求: " + requestURI + ", 用户ID: " + user.getId());
        return true;
    }


}
