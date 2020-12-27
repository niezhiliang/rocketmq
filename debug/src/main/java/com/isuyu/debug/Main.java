package com.isuyu.debug;

import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyContext;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.common.protocol.heartbeat.MessageModel;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @author : niezl
 * @date : 2020/12/12
 */
public class Main {
    private static final String NAMESRV_ADDR = "192.168.3.6:9876";

    private static final String PRODUCER_GROUP = "test-group";

    private static final String TOPIC = "simple-topic";

    public static void main(String[] args) throws Exception {
        DefaultMQPushConsumer  consumer = new DefaultMQPushConsumer(PRODUCER_GROUP);
        consumer.setNamesrvAddr(NAMESRV_ADDR);
        //第一个参数表示：订阅的topic   第二个参数表示消息过滤器：* 表示接收所有信息 一个消费者订阅一个topic
        consumer.subscribe(TOPIC,"*");
        //最大消费线程数
        consumer.setConsumeThreadMax(3);
        //最小消费线程数
        consumer.setConsumeThreadMin(2);
        consumer.registerMessageListener(new MessageListenerConcurrently() {
            @Override
            public ConsumeConcurrentlyStatus consumeMessage(List<MessageExt> list, ConsumeConcurrentlyContext consumeConcurrentlyContext) {
                for (MessageExt messageExt : list) {
                    System.out.println("ThreadId:"+ Thread.currentThread().getName() +"收到的message:"+new String(messageExt.getBody()));
                }
                return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
            }
        });
        consumer.setMessageModel(MessageModel.CLUSTERING);
        consumer.start();
        System.out.println("simpleConsumer start....");
        TimeUnit.SECONDS.sleep(10);

    }
}
