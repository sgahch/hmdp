package com.hmdp.service.impl;

import com.hmdp.config.RabbitMQConfig;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.service.MessageProducerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/**
 * 消息生产者服务实现
 */
@Slf4j
@Service
public class MessageProducerServiceImpl implements MessageProducerService {

    @Resource
    private RabbitTemplate rabbitTemplate;

    @Override
    public void sendSeckillOrderMessage(VoucherOrder voucherOrder) {
        try {
            log.info("📤 [RabbitMQ] 发送秒杀订单消息: orderId={}, userId={}, voucherId={}",
                    voucherOrder.getId(), voucherOrder.getUserId(), voucherOrder.getVoucherId());
            /**
             * 这行代码的核心好处是它极大简化了向 RabbitMQ 发送消息的过程，
             * 实现了消息对象的 自动化序列化、底层的连接与通道管理以及与 Spring 生态的无缝集成。
             */
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.SECKILL_ORDER_EXCHANGE,
                    RabbitMQConfig.SECKILL_ORDER_ROUTING_KEY,
                    voucherOrder
            );

            log.info("✅ [RabbitMQ] 秒杀订单消息发送成功: orderId={}", voucherOrder.getId());
        } catch (Exception e) {
            log.error("❌ [RabbitMQ] 秒杀订单消息发送失败: orderId={}, error={}",
                    voucherOrder.getId(), e.getMessage(), e);
            throw new RuntimeException("消息发送失败", e);
        }
    }
}
