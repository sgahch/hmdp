package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.hmdp.entity.Shop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.*;

/**
 * redis工具
 *
 * @author CHEN
 * @date 2022/10/08
 */
@Slf4j
@Component
public class CacheClient {
    private final StringRedisTemplate stringRedisTemplate;

    @Autowired
    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 将任意对象序列化成json存入redis
     *
     * @param key   关键
     * @param value 价值
     * @param time  时间
     * @param unit  单位
     */
    public void set(String key, Object value, Long time, TimeUnit unit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, unit);
    }

    /**
     * 将任意对象序列化成json存入redis 并且携带逻辑过期时间
     *
     * @param key   关键
     * @param value 价值
     * @param time  时间
     * @param unit  单位
     */
    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit) {
        //封装逻辑过期时间
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        //存入redis
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    /**
     * 设置空值解决缓存穿透
     *
     * @param keyPrefix  关键前缀
     * @param id         id
     * @param type       类型
     * @param dbFallback db回退
     * @param time       时间
     * @param unit       单位
     * @return {@link R}
     */
    public <R, ID> R queryWithPassThrough(
            String keyPrefix
            , ID id
            , Class<R> type
            , Function<ID, R> dbFallback
            , Long time
            , TimeUnit unit) {
        String key = keyPrefix + id;
        //从redis中查询
        String json = stringRedisTemplate.opsForValue().get(key);
        //判断是否存在
        if (StringUtils.isNotEmpty(json)) {
            //存在直接返回
            return JSONUtil.toBean(json, type);
        }
        //判断空值
        if ("".equals(json)) {
            return null;
        }
        //不存在 查询数据库
        R r = dbFallback.apply(id);
        if (r == null) {
            //redis写入空值
            this.set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            //数据库不存在 返回错误
            return null;
        }
        //数据库存在 写入redis
        this.set(key, r, time, unit);
        //返回
        return r;
    }

    /**
     * 逻辑过期解决缓存击穿
     *
     * @param id id
     * @return {@link Shop}
     */
    public <R, ID> R queryWithLogicalExpire(String keyPrefix
            , ID id
            , Class<R> type
            , Function<ID, R> dbFallback
            , Long time
            , TimeUnit unit) {
        String key = keyPrefix + id;
        //从redis中查询
        String json = stringRedisTemplate.opsForValue().get(key);
        // 判断是否存在
        if (StringUtils.isEmpty(json)) { // <--- 缓存未命中时进入此分支
            // 不存在，尝试从数据库查询
            R r = dbFallback.apply(id); // 调用Function从数据库查询数据

            // 如果数据库中也不存在，缓存空值以防止缓存穿透
            if (r == null) {
                // 缓存空对象，设置一个短的过期时间，防止频繁穿透
                // 这里使用 set 方法，而不是 setWithLogicalExpire，因为是真实的不存在
                stringRedisTemplate.opsForValue().set(key, "null", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null; // 数据库和缓存都不存在，返回空
            }

            // 数据库存在数据，将其写入Redis（首次写入，设置逻辑过期时间）
            // 这里需要调用 setWithLogicalExpire，因为它会把数据和逻辑过期时间一起存
            this.setWithLogicalExpire(key, r, time, unit);
            return r; // 返回从数据库查到的数据
        }
        //命中 反序列化
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        JSONObject jsonObject = (JSONObject) redisData.getData();
        R r = BeanUtil.toBean(jsonObject, type);
        LocalDateTime expireTime = redisData.getExpireTime();
        //判断是否过期
        if (expireTime.isAfter(LocalDateTime.now())) {
            //未过期 直接返回
            return r;
        }
        //已过期
        //获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        boolean flag = tryLock(lockKey);
        //是否获取锁成功
        if (flag) {
            //成功 异步重建
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    //查询数据库
                    R newR = dbFallback.apply(id);
                    //写入redis
                    this.setWithLogicalExpire(key,newR,time,unit);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    //释放锁
                    unLock(lockKey);
                }
            });
        }
        //返回过期商铺信息
        return r;
    }

    /**
     * 互斥锁解决缓存击穿
     *
     * @param keyPrefix    缓存key前缀
     * @param id           查询id
     * @param type         返回类型
     * @param dbFallback   数据库查询函数
     * @param time         缓存过期时间
     * @param unit         时间单位
     * @param lockKeyPrefix 锁key前缀
     * @return 查询结果
     */
    public <R, ID> R queryWithMutex(
            String keyPrefix,
            ID id,
            Class<R> type,
            Function<ID, R> dbFallback,
            Long time,
            TimeUnit unit,
            String lockKeyPrefix) {

        String key = keyPrefix + id;
        String lockKey = lockKeyPrefix + id;
        String threadName = Thread.currentThread().getName();

        //log.info("🔍 [互斥锁缓存] 开始查询 - Key: {}, Thread: {}", key, threadName);

        // 从redis中查询
        String json = stringRedisTemplate.opsForValue().get(key);

        // 判断是否存在
        if (StringUtils.isNotEmpty(json)) {
            //log.info("✅ [缓存命中] Key: {}, Thread: {}", key, threadName);
            return JSONUtil.toBean(json, type);
        }

        // 判断空值（缓存穿透保护）
        if ("".equals(json)) {
            //log.info("🚫 [空值缓存命中] Key: {}, Thread: {}", key, threadName);
            return null;
        }

        //log.info("❌ [缓存未命中] 准备获取互斥锁 - Key: {}, LockKey: {}, Thread: {}", key, lockKey, threadName);

        // 实现缓存重建 - 获取互斥锁
        R result = null;
        boolean lockAcquired = false;

        try {
            boolean isLock = tryLock(lockKey);
            lockAcquired = isLock;

            // 判断是否获取锁成功
            if (!isLock) {
                //log.info("🔒 [获取锁失败] 等待重试 - LockKey: {}, Thread: {}", lockKey, threadName);
                // 获取失败，休眠并重试
                Thread.sleep(50);
                return queryWithMutex(keyPrefix, id, type, dbFallback, time, unit, lockKeyPrefix);
            }

            //log.info("🔓 [获取锁成功] 开始缓存重建 - LockKey: {}, Thread: {}", lockKey, threadName);

            // 获取锁成功，再次检查缓存（双重检查）
            json = stringRedisTemplate.opsForValue().get(key);
            if (StringUtils.isNotEmpty(json)) {
                //log.info("✅ [双重检查缓存命中] Key: {}, Thread: {}", key, threadName);
                return JSONUtil.toBean(json, type);
            }

            //log.info("🗄️ [查询数据库] Key: {}, Thread: {}", key, threadName);
            // 查询数据库
            long dbStartTime = System.currentTimeMillis();
            result = dbFallback.apply(id);
            long dbEndTime = System.currentTimeMillis();

            // 模拟重建延时（可选，生产环境可删除）
            Thread.sleep(200);

            if (result == null) {
                //log.info("🚫 [数据库无数据] 缓存空值 - Key: {}, Thread: {}, 查询耗时: {}ms",
                        //key, threadName, (dbEndTime - dbStartTime));
                // 数据库不存在，缓存空值防止缓存穿透
                stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }

            // 数据库存在，写入redis
            // 添加随机时间防止缓存雪崩（在原时间基础上增加0-50%的随机时间）
            long randomTime = time + (long) (Math.random() * time * 0.5);
            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(result), randomTime, unit);

            //log.info("💾 [缓存重建完成] Key: {}, Thread: {}, 查询耗时: {}ms, 缓存TTL: {}{}",
                    //key, threadName, (dbEndTime - dbStartTime), randomTime, unit.toString().toLowerCase());

        } catch (InterruptedException e) {
            //log.error("❌ [缓存重建中断] Key: {}, Thread: {}, Error: {}", key, threadName, e.getMessage());
            throw new RuntimeException("缓存重建被中断", e);
        } finally {
            // 释放互斥锁
            if (lockAcquired) {
                unLock(lockKey);
                //log.info("🔓 [释放锁成功] LockKey: {}, Thread: {}", lockKey, threadName);
            }
        }

        return result;
    }

    /**
     * 简易线程池
     */
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    /**
     * 获取锁
     *
     * @param key 关键
     * @return boolean
     */
    private boolean tryLock(String key) {
        String threadName = Thread.currentThread().getName();
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", LOCK_SHOP_TTL, TimeUnit.SECONDS);
        boolean result = BooleanUtil.isTrue(flag);
        //log.debug("🔐 [尝试获取锁] LockKey: {}, Thread: {}, Result: {}", key, threadName, result ? "成功" : "失败");
        return result;
    }

    /**
     * 释放锁
     *
     * @param key 关键
     */
    private void unLock(String key) {
        String threadName = Thread.currentThread().getName();
        stringRedisTemplate.delete(key);
        //log.debug("🔓 [释放锁] LockKey: {}, Thread: {}", key, threadName);
    }


}
