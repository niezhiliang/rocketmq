package com.isuyu.debug;

import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.producer.LocalTransactionState;
import org.apache.rocketmq.client.producer.TransactionListener;
import org.apache.rocketmq.client.producer.TransactionMQProducer;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageExt;

import java.util.concurrent.TimeUnit;

/**
 * @author niezhiliang
 * @date 2021/4/22 14:38
 */
public class TransactionMessageSend {

    public static void main(String[] args) throws MQClientException, InterruptedException {
        TransactionMQProducer producer = new TransactionMQProducer();
        producer.setProducerGroup("Testttt");
        producer.setNamesrvAddr("localhost:9876");
        producer.setTransactionListener(new TransactionListener() {
            @Override
            public LocalTransactionState executeLocalTransaction(Message msg, Object arg) {
                System.out.println(new String(msg.getBody()));
                return LocalTransactionState.COMMIT_MESSAGE;
            }

            @Override
            public LocalTransactionState checkLocalTransaction(MessageExt msg) {
                System.out.println("chk---" + new String(msg.getBody()));
                return LocalTransactionState.COMMIT_MESSAGE;
            }
        });
        producer.start();
        Message message = new Message("testMsg","这是事务消息".getBytes());
        producer.sendMessageInTransaction(message,null);
        TimeUnit.SECONDS.sleep(120);
    }
}
