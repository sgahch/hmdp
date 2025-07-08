package com.hmdp.service;

import com.hmdp.entity.VoucherOrder;

/**
 * 消息生产者服务接口
 */
public interface MessageProducerService {

    /**
     * 发送秒杀订单消息
     *
     * @param voucherOrder 优惠券订单
     */
    void sendSeckillOrderMessage(VoucherOrder voucherOrder);
}
