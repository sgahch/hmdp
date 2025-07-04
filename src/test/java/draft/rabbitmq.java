package draft;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.MessageProperties; // 导入 MessageProperties

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeoutException;

public class rabbitmq {

    private final static String QUEUE_NAME = "time_queue";

    public static void main(String[] args) {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("192.168.182.128"); // RabbitMQ 服务器地址
        factory.setPort(5672);             // RabbitMQ 端口
        factory.setUsername("guest");      // 用户名
        factory.setPassword("guest");      // 密码

        try (Connection connection = factory.newConnection(); // 创建连接
             Channel channel = connection.createChannel()) {   // 创建信道

            // 声明一个持久化队列
            // queueDeclare(queue, durable, exclusive, autoDelete, arguments)
            // durable = true 表示队列是持久化的，即使RabbitMQ重启，队列也不会丢失
            channel.queueDeclare(QUEUE_NAME, true, false, false, null);

            System.out.println(" [x] 时间生产者已启动。");

            while (true) {
                LocalDateTime now = LocalDateTime.now();
                String message = "当前时间: " + now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

                // 发布消息，并设置消息持久化
                // MessageProperties.PERSISTENT_TEXT_PLAIN 使消息持久化，确保RabbitMQ重启后消息不丢失
                channel.basicPublish("", QUEUE_NAME,
                        MessageProperties.PERSISTENT_TEXT_PLAIN, // 设置消息为持久化
                        message.getBytes(StandardCharsets.UTF_8));
                System.out.println(" [x] 已发送 '" + message + "'");

                Thread.sleep(1000);   // 每秒发送一次
            }
        } catch (IOException | TimeoutException e) {
            System.err.println("生产者连接或操作错误: " + e.getMessage());
        } catch (InterruptedException e) {
            System.err.println("生产者线程中断: " + e.getMessage());
            Thread.currentThread().interrupt(); // 重新设置中断标志
        }
    }
}
