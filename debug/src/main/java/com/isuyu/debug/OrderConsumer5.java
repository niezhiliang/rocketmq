package com.isuyu.debug;

import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeOrderlyContext;
import org.apache.rocketmq.client.consumer.listener.ConsumeOrderlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerOrderly;
import org.apache.rocketmq.client.consumer.rebalance.AllocateMessageQueueByConfig;
import org.apache.rocketmq.client.producer.MessageQueueSelector;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.common.message.MessageQueue;
import org.apache.rocketmq.common.protocol.heartbeat.MessageModel;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @author : niezl
 * @date : 2021/1/5
 */
public class OrderConsumer5 {
    private static final String NAMESRV_ADDR = "127.0.0.1:9876";

    private static final String PRODUCER_GROUP = "testOrder";

    private static final String TOPIC = "testMsg";

    public static void main(String[] args) throws Exception {
        DefaultMQPushConsumer consumer = new DefaultMQPushConsumer(PRODUCER_GROUP);
        consumer.setNamesrvAddr(NAMESRV_ADDR);
        //第一个参数表示：订阅的topic   第二个参数表示消息过滤器：* 表示接收所有信息 一个消费者订阅一个topic
        consumer.subscribe(TOPIC,"*");
        //最大消费线程数
        consumer.setConsumeThreadMax(3);
        //最小消费线程数
        consumer.setConsumeThreadMin(2);
        consumer.registerMessageListener(new MessageListenerOrderly() {
            @Override
            public ConsumeOrderlyStatus consumeMessage(List<MessageExt> msgs, ConsumeOrderlyContext context) {
                msgs.forEach( m -> {
                    System.out.println(new String(m.getBody()));
                });
                return ConsumeOrderlyStatus.SUCCESS;
            }
        });
        consumer.setMessageModel(MessageModel.CLUSTERING);
        AllocateMessageQueueByConfig allocateMessageQueueByConfig = new AllocateMessageQueueByConfig();
        allocateMessageQueueByConfig.setMessageQueueList(Arrays.asList(new MessageQueue("testMsg","broker-a",0)));
        consumer.setAllocateMessageQueueStrategy(allocateMessageQueueByConfig);
        consumer.start();
        System.out.println("simpleConsumer start....");
        TimeUnit.SECONDS.sleep(10);

    }
}
