package com.isuyu.debug;

import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.MessageQueueSelector;
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
        DefaultMQProducer producer = new DefaultMQProducer(PRODUCER_GROUP);
        producer.setNamesrvAddr(NAMESRV_ADDR);
        producer.start();

        for (int i = 10; i < 12 ; i++) {
            Message message = new Message(TOPIC, ("hello producer  " + i).getBytes(StandardCharsets.UTF_8));
            message.setDelayTimeLevel(2);
            producer.send(message, new MessageQueueSelector() {
                @Override
                public MessageQueue select(List<MessageQueue> mqs, Message msg, Object arg) {
                    int num = (int) arg;
                    return mqs.get(mqs.size()-1);
                }
            },0);
        }


        TimeUnit.SECONDS.sleep(10);

        producer.shutdown();

    }
}
