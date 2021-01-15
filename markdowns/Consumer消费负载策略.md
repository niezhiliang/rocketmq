##  RocketMQ消费负载策略

Consumer在拉取消息之前需要对TopicMessage进行负载操作，负载操作由一个定时器来完成单位，`定时间隔默认20s`

简单来说就是将Topic下的MessageQueue分配给这些Consumer，至于怎么分，就是通过这些负载策略定义的算法规则来划分。

### AllocateMessageQueueAveragely

平均负载策略，RocketMQ默认使用的就是这种方式，如果某个Consumer集群，订阅了某个Topic，Topic下面的这些MessageQueue会被平均分配给集群中的Consumer，为了帮助大家理解，我画了个图

<img src="http://java-imgs.oss-cn-hongkong.aliyuncs.com/2021/1/7/20210107154034.jpg" style="zoom:50%;float:left;" />

我来讲下这个图代表的意思，假设topic为 testMsg，该testMsg下面有4个MessageQueue，然后这些Consumer组成了一个集群（都监听了该Topic并且消费groupId是一样的），首先会给Consumer和MessageQueue进行排序，谁是老大，谁先拿MessageQueue， 平均分配分为两种情况，

**MessageQueue数量大于Consumer数量**

如果队列数量不是消费者数量的整数倍，跟上图中`2个Consumer`和`4个Consumer`的情况一样，先分每个Consumer应得的数量，拿2个Consumer来举个例子，C1 和C2 各自分到了2个MessageQueue，C1排序时在C2前面，所以C1先把 Q0 和Q1拿走，C2再拿2个，也就是Q2和Q3。

如果队列数量不是消费者的整数倍，跟上图`3个Consumer`和`5个Consumer`的情况一样，5个Comsumer的比较特殊，我们过会再讲，我们拿3个Consumer的情况来举例，4个消息队列，每个Consumer能分到1个，还剩下1个，弱肉强食嘛，剩下的当然给排在前面的大哥C1啦，最后分下来，C1分到2个队列，C2和C3只分到一个，分完数量以后，也是按照顺序来拿，C1拿到了Q0和Q1，然后C2就只能从Q2开始拿，C3只能拿剩下的Q3啦

**MessageQueue数量小于Consumer数量**

这种情况平均下来，每个人1个Consumer都分不到一个，也就是我们上图中的`5个Comsumer`的情况老规矩，按照排序顺序，每人先拿1个队列，由于C5排在最后面，队列全被别人拿走了，C5就一直分不到消息队列，除非前面的某个Consumer挂了，20s之后，在队列重新负载的时候就能拿到MessageQueue。

**具体算法：**

```java
//consumer的排序后的
int index = cidAll.indexOf(currentCID);
//取模
int mod = mqAll.size() % cidAll.size();
//如果队列数小于消费者数量，则将分到队列数设置为1，如果余数大于当前消费者的index,则
//能分到的队列数+1，否则就是平均值
int averageSize =
  mqAll.size() <= cidAll.size() ? 1 : (mod > 0 && index < mod ? mqAll.size() / cidAll.size()
                                       + 1 : mqAll.size() / cidAll.size());
//consumer获取第一个MessageQueue的索引
int startIndex = (mod > 0 && index < mod) ? index * averageSize : index * averageSize + mod;
// 如果消费者大于队列数，rang会是负数，循环也就不会执行 
int range = Math.min(averageSize, mqAll.size() - startIndex);
for (int i = 0; i < range; i++) {
  result.add(mqAll.get((startIndex + i) % mqAll.size()));
}
```



### AllocateMessageQueueAveragelyByCircle

环形平均分配，这个和平均分配唯一的区别就是，再分队列的时候，平均队列是将属于自己的MessageQueue全部拿走，而环形平均则是，一人拿一个，拿到的Queue不是连续的。我也画了张图来帮助大家理解

<img src="http://java-imgs.oss-cn-hongkong.aliyuncs.com/2021/1/7/20210107162520.jpg" style="zoom:50%;float:left;" />

这种环形平均分配和平均分配，每个Consumer拿到的MessageQueue数量是不变的，我们就拿3个Consumer的情况举个例子，

也是对Consumer和MessageQueue排序，先确定每个Consumer能拿到的MessageQueue数量，C1能分到2个，C2和C3只能分到1个

C1先拿1个，然后C2拿一个，C3拿一个,C1再拿一个。也就是图上3个Consumer画的这个情况。

另外，如果Consumer的数量大于消息队列的数量，处理方式和平均分配时一样的。

```java
//当前consumer排序后的索引 
int index = cidAll.indexOf(currentCID);  
//index会是consumer第一个拿到的消息队列索引
for (int i = index; i < mqAll.size(); i++) {
  //这里采用了取模的方式
  if (i % cidAll.size() == index) { 
    result.add(mqAll.get(i));  
  }
}
```

### AllocateMessageQueueByConfig

 用户自定义配置，用户在创建Consumer的时候，可以设置要使用的负载策略，如果我们设置为`AllocateMessageQueueByConfig`方式时，我们可以自己指定需要监听的MessageQueues，它维护了一个List<MessageQueue> messageQueueList，我们可以往这里面塞目标的MessageQueues，这个策略了解一下就行，用的不多，具体设置代码如下：

```java
DefaultMQPushConsumer consumer = new DefaultMQPushConsumer("testGroup");
consumer.setNamesrvAddr("127.0.0.1:9876");
//订阅topic
consumer.subscribe("testMsg","*");
//注册消息监听
consumer.registerMessageListener(new MessageListenerConcurrently() {
  @Override
  public ConsumeConcurrentlyStatus consumeMessage(List<MessageExt> msgs, ConsumeConcurrentlyContext context) {
    // do job
    return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
  }
});
//用户自定义queue策略
AllocateMessageQueueByConfig allocateMessageQueueByConfig = new AllocateMessageQueueByConfig();
//指定MessageQueue
allocateMessageQueueByConfig.setMessageQueueList(Arrays.asList(new MessageQueue("testMsg","broker-a",0)));
//设置consumer的负载策略
consumer.setAllocateMessageQueueStrategy(allocateMessageQueueByConfig);
//启动consumer
consumer.start();
```

### AllocateMessageQueueByMachineRoom

​    机房负载策略，其实这个策略就是当前Consumer只负载处在指定的机房内的MessageQueue，还有brokerName的命名必须要按要求的格式来设置： `机房名@brokerName`

我们先看下具体的使用

```java
DefaultMQPushConsumer consumer = new DefaultMQPushConsumer("testGroup");
consumer.setNamesrvAddr("127.0.0.1:9876");
//订阅topic
consumer.subscribe("testMsg","*");
//注册消息监听
consumer.registerMessageListener(new MessageListenerConcurrently() {
  @Override
  public ConsumeConcurrentlyStatus consumeMessage(List<MessageExt> msgs, ConsumeConcurrentlyContext context) {
    // do job
    return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
  }
});
AllocateMessageQueueByMachineRoom allocateMachineRoom = new AllocateMessageQueueByMachineRoom();
//指定机房名称  machine_room1、machine_room2
allocateMachineRoom.setConsumeridcs(new HashSet<>(Arrays.asList("machine_room1","machine_room2")));
//设置consumer的负载策略
consumer.setAllocateMessageQueueStrategy(allocateMachineRoom);
//启动consumer
consumer.start();
```

我们再看下源码

```java
 //当前consumer的下标
int currentIndex = cidAll.indexOf(currentCID);
if (currentIndex < 0) {
  return result;
}
//符合机房条件的队列
List<MessageQueue> premqAll = new ArrayList<MessageQueue>();
for (MessageQueue mq : mqAll) {
  //brokerName命名规则   machine_room1@broker-a
  String[] temp = mq.getBrokerName().split("@");
  //判断是否符合指定的机房条件
  if (temp.length == 2 && consumeridcs.contains(temp[0])) {
    premqAll.add(mq);
  }
}
//分配到的队列数
int mod = premqAll.size() / cidAll.size();
//取模
int rem = premqAll.size() % cidAll.size();
//当前分配到的第一个队列索引
int startIndex = mod * currentIndex;
//分配到的最后一个队列索引
int endIndex = startIndex + mod;
//取startIndex到endIndex的队列
for (int i = startIndex; i < endIndex; i++) {
  result.add(mqAll.get(i));
}
//MessageQueue数量和Consumer不是整数倍时  有点像平均分配因为队列下标取到的也是连续的
if (rem > currentIndex) {
  result.add(premqAll.get(currentIndex + mod * cidAll.size()));
}
```

总结一下源码的意思：

**1.** 首先赛选出当前Topic处在指定机房的队列

**2.** 赛选出队列后，按照平均负载策略进行具体的分配（算法极其相似）

哈哈，发现总结的真简洁呀，别慌，来张图给你润润喉

<img src="http://java-imgs.oss-cn-hongkong.aliyuncs.com/2021/1/7/20210107201858.jpg" style="zoom:50%;float:left;" />

其实这个策略就是对MessageQueue进行了过滤，过滤完了以后，后续操作就按照平均负载策略来进行具体负载操作。这个算法和平均负载的算法得到的结果是一样的，我感觉这两个策略应该是两个人写的，不然不会写两套不同的算法来实现一个功能。也不知道是不是我自己太差了，理解不到大佬的思路。

### AllocateMachineRoomNearby

这个策略我个人感觉是`AllocateMessageQueueByMachineRoom`的改进版本，因为这个策略的处理方式要比`AllocateMessageQueueByMachineRoom`更加灵活，还考虑到了那些同机房只有MessageQueue却没有Consumer的情况，下面我们来具体讲这个策略。使用该策略需要自己定义一个类，来区分每个broker处于哪个机房，该策略RocketMQ有个测试单元，我稍微改造了一下，就是把这个类提出来了。

```java
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
```

我们从代码中，可以看出来需要在设置brokerName和Consumer的Id的时候需要加上机房名称，eg：hz_aliyun_room1-broker-a、

hz_aliyun_root1-Client1。我们先看一下在代码里面怎么使用这个同机房分配策略

```java
DefaultMQPushConsumer consumer = new DefaultMQPushConsumer("testGroup");
consumer.setNamesrvAddr("127.0.0.1:9876");
//订阅topic
consumer.subscribe("testMsg","*");
//注册消息监听
consumer.registerMessageListener(new MessageListenerConcurrently() {
  @Override
  public ConsumeConcurrentlyStatus consumeMessage(List<MessageExt> msgs, ConsumeConcurrentlyContext context) {
    // do job
    return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
  }
});
//用户同机房分配策略  
consumer.setAllocateMessageQueueStrategy(new AllocateMachineRoomNearby(new AllocateMessageQueueAveragely()
                ,new MyMachineResolver()));
//启动consumer
consumer.start();
```

我们可以看到，我们创建这个同机房分配策略的时候，还加了一个平均分配的策略进去，它本身就是一个策略，为啥还要传另一个策略。（`该策略只会讲MessageQueue和Consumer按机房进行分组，分组以后具体的负载，就是通过我们传的另外一个负载策略来分配的`）我们到源码里面去看，后面会解释到

```java
//消息队列按机房分组
Map<String/*machine room */, List<MessageQueue>> mr2Mq = new TreeMap<String, List<MessageQueue>>();
for (MessageQueue mq : mqAll) {
  //这里调用我们自己定义的类方法，得到broker的机房的名称
  String brokerMachineRoom = machineRoomResolver.brokerDeployIn(mq);
  //机房不为空，将broker放到分组中
  if (StringUtils.isNoneEmpty(brokerMachineRoom)) {
    if (mr2Mq.get(brokerMachineRoom) == null) {
      mr2Mq.put(brokerMachineRoom, new ArrayList<MessageQueue>());
    }
    mr2Mq.get(brokerMachineRoom).add(mq);
  } else {
    throw new IllegalArgumentException("Machine room is null for mq " + mq);
  }
}

//consumer按机房分组
Map<String/*machine room */, List<String/*clientId*/>> mr2c = new TreeMap<String, List<String>>();
for (String cid : cidAll) {
  //这里调用我们自己定义的类方法，得到broker的机房的名称
  String consumerMachineRoom = machineRoomResolver.consumerDeployIn(cid);
  if (StringUtils.isNoneEmpty(consumerMachineRoom)) {
    if (mr2c.get(consumerMachineRoom) == null) {
      mr2c.put(consumerMachineRoom, new ArrayList<String>());
    }
    mr2c.get(consumerMachineRoom).add(cid);
  } else {
    throw new IllegalArgumentException("Machine room is null for consumer id " + cid);
  }
}

//当前consumer分到的所有MessageQueue
List<MessageQueue> allocateResults = new ArrayList<MessageQueue>();

//1.给当前consumer分当前机房的那些MessageQeueue
String currentMachineRoom = machineRoomResolver.consumerDeployIn(currentCID);
//得到当前机房的MessageQueue
List<MessageQueue> mqInThisMachineRoom = mr2Mq.remove(currentMachineRoom);
//得到当前机房的Consumer
List<String> consumerInThisMachineRoom = mr2c.get(currentMachineRoom);
if (mqInThisMachineRoom != null && !mqInThisMachineRoom.isEmpty()) {
  //得到当前机房所有MessageQueue和Consumers后根据指定的策略再负载
  allocateResults.addAll(allocateMessageQueueStrategy.allocate(consumerGroup, currentCID, mqInThisMachineRoom, consumerInThisMachineRoom));
}

//2.如果该MessageQueue的机房 没有同机房的consumer,将这些MessageQueue按配置好的备用策略分配给所有的consumer
for (String machineRoom : mr2Mq.keySet()) {
  if (!mr2c.containsKey(machineRoom)) { 
    //添加分配到的游离态MessageQueue
    allocateResults.addAll(allocateMessageQueueStrategy.allocate(consumerGroup, currentCID, mr2Mq.get(machineRoom), cidAll));
  }
}
```

总结一下源码的意思

**1.** 分别给MessageQueue和Consumer按机房分组

**2.** 得到当前Consumer所在机房的所有Consumer和MessageQueue

**3.** 通过`设置的负载策略`，再进行具体的负载，得到当前Consumer分到的MessageQueue

**4.**如果存在MessageQueue的某个机房中，没有和MessageQueue同机房的Consumer，将这些MessageQueue按配置的负载策略分配给集群中所有的Consumer去负载

**5.** 最终该Consumer分到的MessageQueue会包含同机房分配到的和部分游离态分配的

这里我也画个图来解释一下，

<img src="http://java-imgs.oss-cn-hongkong.aliyuncs.com/2021/1/7/20210107191911.jpg" style="zoom:50%;float:left;" />

先同机房的Consumer和MessageQueue进行负载，这里按照平均负载来分（我们创建机房就近策略使用的是平均负载），然后将游离态的通过设置的负载策略来分。

### AllocateMessageQueueConsistentHash

一致性哈希策略，这里我简单介绍一下一致性哈希，这里不好我先给个图，我们再来解释

<img src="http://java-imgs.oss-cn-hongkong.aliyuncs.com/2021/1/8/20210108182537.jpg" style="zoom:40%;float:left;" />



一致性哈希有一个哈希环的概念，哈希环由数值 `0到2^32-1` 组成，不管内容多长的字符，经过哈希计算都能得到一个等长的数字，最后都会落在哈希环上的某个点，哈希环上的点都是虚拟的，比如我们这里使用Consumer的Id来进行哈希计算，得到的这几个是物理的点，然后把得到的点存到TreeMap里面，然后将所有的MessageQueue依次进行同样的哈希计算，得到距离MessageQueue顺时针方向最近的那个Consumer点，这个就是MessageQeueu最终归属的那个Consumer。

>  具体哈希一致性算法参考这篇文章：https://segmentfault.com/a/1190000021199728?utm_source=sf-related

我们看下源码：

```java
//将所有consumer变成节点 到时候经过hash计算 分布在hash环上
Collection<ClientNode> cidNodes = new ArrayList<ClientNode>();
for (String cid : cidAll) {
  cidNodes.add(new ClientNode(cid));
}

final ConsistentHashRouter<ClientNode> router; 
//构建哈希环
if (customHashFunction != null) {
  router = new ConsistentHashRouter<ClientNode>(cidNodes, virtualNodeCnt, customHashFunction);
} else {
  //默认使用MD5进行Hash计算
  router = new ConsistentHashRouter<ClientNode>(cidNodes, virtualNodeCnt);
}

List<MessageQueue> results = new ArrayList<MessageQueue>();
for (MessageQueue mq : mqAll) {
  //对messageQueue进行hash计算，找到顺时针最近的consumer节点
  ClientNode clientNode = router.routeNode(mq.toString());
  //判断是否是当前consumer
  if (clientNode != null && currentCID.equals(clientNode.getKey())) {
    results.add(mq);
  }
}
```





