package com.isuyu.debug;

import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.*;
import org.apache.rocketmq.client.consumer.rebalance.AllocateMachineRoomNearby;
import org.apache.rocketmq.client.consumer.rebalance.AllocateMessageQueueAveragely;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.common.protocol.heartbeat.MessageModel;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @author : niezl
 * @date : 2021/1/5
 */
public class OrderConsumer4 {
    private static final String NAMESRV_ADDR = "127.0.0.1:9876";

    private static final String PRODUCER_GROUP = "testOrder";

    private static final String TOPIC = "testMsg";

    public static void main(String[] args) throws Exception {
        DefaultMQPushConsumer consumer = new DefaultMQPushConsumer(PRODUCER_GROUP);
        consumer.setNamesrvAddr(NAMESRV_ADDR);
        //第一个参数表示：订阅的topic   第二个参数表示消息过滤器：* 表示接收所有信息 一个消费者订阅一个topic
        consumer.subscribe(TOPIC,"*");
        consumer.registerMessageListener(new MessageListenerConcurrently() {
            @Override
            public ConsumeConcurrentlyStatus consumeMessage(List<MessageExt> msgs, ConsumeConcurrentlyContext context) {
                // do job
                return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
            }
        });
        consumer.setAllocateMessageQueueStrategy(new AllocateMachineRoomNearby(new AllocateMessageQueueAveragely()
                ,new MyMachineResolver()));
        consumer.start();
        System.out.println("simpleConsumer start....");
        TimeUnit.SECONDS.sleep(10);

    }
}
