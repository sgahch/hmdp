package draft;

import com.rabbitmq.client.*;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

public class TimeConsumer {

    private final static String QUEUE_NAME = "time_queue";

    public static void main(String[] argv) throws IOException, TimeoutException {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("192.168.182.128"); // RabbitMQ 服务器地址
        factory.setPort(5672);             // RabbitMQ 端口
        factory.setUsername("guest");      // 用户名
        factory.setPassword("guest");      // 密码

        Channel channel = factory.newConnection().createChannel();//同时创建连接和信道
        channel.queueDeclare(QUEUE_NAME, true, false, false, null);//声明一个持久化队列
        channel.basicConsume(QUEUE_NAME, true, new DefaultConsumer(channel){
            @Override
            public void handleDelivery(String consumerTag, Envelope envelope,
                                       AMQP.BasicProperties properties, byte[] body) throws IOException {
                // 5.处理消息
                String message = new String(body);
                System.out.println("接收到消息：【" + message + "】");
            }
        });
//        try {
//            Connection connection = factory.newConnection(); // 创建连接
//            Channel channel = connection.createChannel();   // 创建信道
//
//            // 声明一个持久化队列 (与生产者保持一致)
//            // durable = true 表示队列是持久化的
//            channel.queueDeclare(QUEUE_NAME, true, false, false, null);
//            System.out.println(" [*] 时间消费者正在等待消息.");
//
//            // 设置消息预取数量，一次只从队列获取一条消息，处理完再获取下一条
//            // 这有助于公平分发消息给多个消费者，并防止消费者一次性加载过多消息导致内存问题
//            channel.basicQos(100); // 每次只分发一条消息给消费者
//
//            // 定义一个回调函数，用于处理接收到的消息
//            DeliverCallback deliverCallback = (consumerTag, delivery) -> {
//                String message = new String(delivery.getBody(), "UTF-8");
//                System.out.println(" [√] 已接收 '" + message + "'");
//                try {
//                    // 模拟消息处理的耗时操作 (可选，但有助于理解手动确认)
//                    // Thread.sleep(100);
//                } finally {
//                    // 手动确认消息，表示消息已成功处理
//                    // basicAck(deliveryTag, multiple)
//                    // deliveryTag: 消息的唯一标识
//                    // multiple: false 表示只确认当前这一条消息
//                    channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
//                }
//            };
//
//            // 开始消费消息，设置 autoAck 为 false，进行手动确认
//            // basicConsume(queue, autoAck, deliverCallback, cancelCallback)
//            // autoAck = false: 消费者必须手动确认消息，否则消息会保留在队列中
//            channel.basicConsume(QUEUE_NAME, false, deliverCallback, consumerTag -> {
//                System.out.println("消费者 " + consumerTag + " 被取消");
//            });
//
//            // 注意：这里没有关闭连接和信道，因为消费者需要持续监听消息。
//            // 当应用程序关闭时（例如通过Ctrl+C），连接会自动关闭。
//
//        } catch (IOException | TimeoutException e) {
//            System.err.println("消费者连接或操作错误: " + e.getMessage());
//        }
    }
}
