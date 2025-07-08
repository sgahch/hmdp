package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.service.MessageProducerService;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author Ynchen
 * @since 2021-12-22
 */
@Service
@Slf4j
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
    @Resource
    private ISeckillVoucherService seckillVoucherService;
    @Resource
    private RedisIdWorker redisIdWorker;
    @Resource
    private RedissonClient redissonClient;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private MessageProducerService messageProducerService;
    /**
     * 自己注入自己为了获取代理对象 @Lazy 延迟注入 避免形成循环依赖
     */
    @Resource
    @Lazy
    private IVoucherOrderService voucherOrderService;
    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;

    //加载lua脚本
    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    /**
     * 秒杀优惠券(普通版)
     *
     * @param voucherId 券id
     * @return {@link Result}
     */
//    @Override
//    public Result seckillVoucher(Long voucherId) {
//        //获取用户
//        UserDTO user = UserHolder.getUser();
//        //获取订单id
//        Long orderId = redisIdWorker.nextId("order");
//        //执行lua脚本
//        Long res = stringRedisTemplate.execute(
//                SECKILL_SCRIPT
//                , Collections.emptyList()
//                , voucherId.toString()
//                , user.getId().toString()
//                , orderId.toString());
//        ///0：表示抢购成功。
//        ///1：表示库存不足。
//        ///2：表示重复下单。
//        //判断结果是否为0
//        int r = res.intValue();
//        if (r != 0) {
//            //不为0 没有购买资格
//            return Result.fail(r == 1 ? "库存不足" : "禁止重复下单");
//        }
//        return Result.ok(orderId);
//    }
    /**
     * 秒杀优惠券(异步消息队列)
     *
     * @param voucherId 券id
     * @return {@link Result}
     */

    @Override
    public Result seckillVoucher(Long voucherId) {
        try {
            //获取用户
            UserDTO user = UserHolder.getUser();
            if (user == null) {
                return Result.fail("用户未登录");
            }

            //获取订单id
            Long orderId = redisIdWorker.nextId("order");

            System.out.println("🎯 [秒杀] 开始处理秒杀请求: voucherId=" + voucherId + ", userId=" + user.getId() + ", orderId=" + orderId);

            //执行lua脚本 - 传递3个参数（voucherId, userId, orderId）
            Long res = stringRedisTemplate.execute(
                    SECKILL_SCRIPT
                    , Collections.emptyList()
                    , voucherId.toString()      // ARGV[1] - voucherId
                    , user.getId().toString()   // ARGV[2] - userId
                    , orderId.toString());      // ARGV[3] - orderId

            if (res == null) {
                System.out.println("❌ [秒杀] Lua脚本执行返回null");
                return Result.fail("系统繁忙，请稍后重试");
            }

            //判断结果是否为0
            int r = res.intValue();
            System.out.println("📊 [秒杀] Lua脚本执行结果: " + r);

            if (r != 0) {
                //不为0 没有购买资格
                String errorMsg;
                switch (r) {
                    case 1:
                        errorMsg = "库存不足";
                        break;
                    case 2:
                        errorMsg = "禁止重复下单";
                        break;
                    case 3:
                        errorMsg = "秒杀活动未开始或已结束";
                        break;
                    default:
                        errorMsg = "秒杀失败，未知错误";
                }
                System.out.println("❌ [秒杀] 秒杀失败: " + errorMsg);
                return Result.fail(errorMsg);
            }

            //为0有购买资格，创建订单并发送到RabbitMQ
            VoucherOrder voucherOrder = new VoucherOrder();
            voucherOrder.setVoucherId(voucherId);
            voucherOrder.setUserId(user.getId());
            voucherOrder.setId(orderId);

            System.out.println("✅ [秒杀] 秒杀资格验证通过，准备发送消息到RabbitMQ");

            //发送消息到RabbitMQ
            messageProducerService.sendSeckillOrderMessage(voucherOrder);

            System.out.println("🎉 [秒杀] 秒杀请求处理完成: orderId=" + orderId);
            //返回订单id
            return Result.ok(orderId);

        } catch (Exception e) {
            System.out.println("❌ [秒杀] 秒杀处理异常: " + e.getMessage());
            e.printStackTrace();
            return Result.fail("系统异常，请稍后重试");
        }
    }

    /**
     * 秒杀优惠券
     *
     * @param voucherId 券id
     * @return {@link Result}
     */
    /*@Override
    public Result seckillVoucher(Long voucherId) {
        //查询优惠券
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        //判断秒杀是否开始
        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
            //秒杀尚未开始
            return Result.fail("秒杀尚未开始");
        }
        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
            //秒杀已经结束
            return Result.fail("秒杀已经结束");
        }
        //判断库存是否充足
        if (voucher.getStock() < 1) {
            //库存不足
            return Result.fail("库存不足");
        }
        Long userId = UserHolder.getUser().getId();
        //仅限单体应用使用
//        synchronized (userId.toString().intern()) {
//            //实现获取代理对象 比较复杂 我采用了自己注入自己的方式
//            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
//            return voucherOrderService.getResult(voucherId);
//        }
        //创建锁对象
//        SimpleRedisLock simpleRedisLock = new SimpleRedisLock("order:" + userId, stringRedisTemplate);
        RLock lock = redissonClient.getLock("lock:order:" + userId);
        //获取锁
//        boolean isLock = simpleRedisLock.tryLock(1200L);
        boolean isLock = lock.tryLock();
        //判断是否获取锁成功
        if (!isLock){
            //获取失败,返回错误或者重试
            return Result.fail("一人一单哦！");
        }
        try {
            return voucherOrderService.getResult(voucherId);
        } finally {
            //释放锁
            lock.unlock();
        }
    }*/
    @Override
    @NotNull
    @Transactional(rollbackFor = Exception.class)//用于保证事务的原子性:当发生异常时回滚
    //得到抢购结果
    public Result getResult(Long voucherId) {
        //是否下单
        Long userId = UserHolder.getUser().getId();
        Long count = lambdaQuery()
                .eq(VoucherOrder::getVoucherId, voucherId)
                .eq(VoucherOrder::getUserId, userId)
                .count();

        if (count > 0) {
            return Result.fail("禁止重复购买");
        }
        //扣减库存
        boolean isSuccess = seckillVoucherService.update(
                new LambdaUpdateWrapper<SeckillVoucher>()
                        .eq(SeckillVoucher::getVoucherId, voucherId)// where voucher_id = ?
                        .gt(SeckillVoucher::getStock, 0)  // where stock > 0
                        .setSql("stock=stock-1"));// set stock=stock-1
        if (!isSuccess) {
            //库存不足
            return Result.fail("库存不足");
        }
        //创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        Long orderId = redisIdWorker.nextId("order");
        voucherOrder.setVoucherId(voucherId);
        voucherOrder.setUserId(userId);
        voucherOrder.setId(orderId);
        this.save(voucherOrder);
        //返回订单id
        return Result.ok(orderId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    //创建优惠券订单
    public void createVoucherOrder(VoucherOrder voucherOrder) {
        //扣减库存
        boolean isSuccess = seckillVoucherService.update(
                new LambdaUpdateWrapper<SeckillVoucher>()
                        .eq(SeckillVoucher::getVoucherId, voucherOrder.getVoucherId())
                        .gt(SeckillVoucher::getStock, 0)
                        .setSql("stock=stock-1"));
        //创建订单
        this.save(voucherOrder);
    }
}
