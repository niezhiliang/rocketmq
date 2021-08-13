package com.isuyu.debug;

import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.MessageQueueSelector;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageQueue;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @author : niezl
 * @date : 2020/12/28
 */
public class ProducerMain {

    private static final String NAMESRV_ADDR = "localhost:9876";

    private static final String PRODUCER_GROUP = "test-group";

    private static final String TOPIC = "testMsg";

    public static void main(String[] args) throws Exception {
        //初始化发送消息的线程池
        DefaultMQProducer producer = new DefaultMQProducer("test-group");
        producer.setNamesrvAddr("localhost:9876");
        //这一步还没拿到broker地址
        producer.start();
        Message message = new Message("testMsg", "hello broker! I am producer".getBytes());
        message.setDelayTimeLevel(3);
        //消息发送
        producer.send(message);
//        producer.send(new Message("testMsg", "hello broker! I am producer".getBytes()), new SendCallback() {
//            @Override
//            public void onSuccess(SendResult sendResult) {
//                //
//                System.out.println(sendResult);
//            }
//
//            @Override
//            public void onException(Throwable e) {
//
//            }
//        });
//        producer.sendOneway(new Message("testMsg","hello broker! I am producer".getBytes()));
        TimeUnit.SECONDS.sleep(3);
        producer.shutdown();

    }
}
