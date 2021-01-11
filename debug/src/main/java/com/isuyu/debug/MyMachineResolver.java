package com.isuyu.debug;

import org.apache.rocketmq.client.consumer.rebalance.AllocateMachineRoomNearby;
import org.apache.rocketmq.common.message.MessageQueue;

/**
 * @author : niezl
 * @date : 2021/1/7
 */
public class MyMachineResolver implements AllocateMachineRoomNearby.MachineRoomResolver {

    /**
     * 判断当前broker处于哪个机房
     * @param messageQueue
     * @return
     */
    @Override
    public String brokerDeployIn(MessageQueue messageQueue) {
        return messageQueue.getBrokerName().split("-")[0];
    }

    /**
     * 判断consumer处于哪个机房
     * @param clientID
     * @return
     */
    @Override
    public String consumerDeployIn(String clientID) {
        return clientID.split("-")[0];
    }
}
