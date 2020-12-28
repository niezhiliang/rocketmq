package com.isuyu.debug;

import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.common.message.Message;

import java.util.concurrent.TimeUnit;

/**
 * @author : niezl
 * @date : 2020/12/28
 */
public class ProducerMain {

    private static final String NAMESRV_ADDR = "localhost:9876";

    private static final String PRODUCER_GROUP = "test-group";

    private static final String TOPIC = "simple-topic";

    public static void main(String[] args) throws Exception {
        //初始化发送消息的线程池
        DefaultMQProducer producer = new DefaultMQProducer(PRODUCER_GROUP);
        producer.setNamesrvAddr(NAMESRV_ADDR);
        producer.start();

        producer.send(new Message(TOPIC,"hello producer".getBytes()));

        TimeUnit.SECONDS.sleep(10);

        producer.shutdown();

    }
}
