## 	Consumer启动流程

### 前言

上一篇讲了RocketMQ在消息发送的流程，这一篇开始讲Consumer的启动流程，看下Consumer在启动的过程中做了哪些事情。

### Consumer端消费代码

下面是consumer并发消费时的代码，主要分为了Consumer的初始化，以及注册消息并发消费的监听器，

最后就是启动consumer

```java
DefaultMQPushConsumer  consumer = new DefaultMQPushConsumer(CONSUMER_GROUP);
consumer.setNamesrvAddr("127.0.0.1:9876");
//第一个参数表示：订阅的topic   第二个参数表示消息过滤器：* 表示接收所有信息 一个消费者订阅一个topic
consumer.subscribe("testMsg","*");
//并发消费
consumer.registerMessageListener(new MessageListenerConcurrently() {
  @Override
  public ConsumeConcurrentlyStatus consumeMessage(List<MessageExt> list, ConsumeConcurrentlyContext consumeConcurrentlyContext) {
    for (MessageExt messageExt : list) {
      System.out.println("收到的message:"+new String(messageExt.getBody()));
    }
    return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
  }
});
consumer.start();
```

### Consumer初始化

```java
public DefaultMQPushConsumer(final String namespace, final String consumerGroup, RPCHook rpcHook,
        AllocateMessageQueueStrategy allocateMessageQueueStrategy) 
```

- namespace：producer的命名空间
- consumerGroup：当前consumer的消费者组
- rpcHook：执行所有远程请求的钩子函数，办好了请求前和请求后两个方法。
- allocateMessageQueueStrategy：消费的负载策略，默认为 **AllocateMessageQueueAveragely** (平均消费)，具体的消费策略详解请看这篇https://blog.csdn.net/qq_38082304/article/details/112378245

我们这里只传了一个消费者组进去，默认namespace和rpcHook都是null，这里实例化了`DefaultMQPushConsumer`后将它传给了

**DefaultMQPushConsumerImpl**这个类（很重要）。

```java
public DefaultMQPushConsumerImpl(DefaultMQPushConsumer defaultMQPushConsumer, RPCHook rpcHook) {
  this.defaultMQPushConsumer = defaultMQPushConsumer;
  this.rpcHook = rpcHook;
  //默认1s
  this.pullTimeDelayMillsWhenException = defaultMQPushConsumer.getPullTimeDelayMillsWhenException();
}
```

**DefaultMQPushConsumer**初始化主要做了下面这些事情

1. 设置Consumer的group
2. 设置Consumer的负载策略
3. **设置属性defaultMQPushConsumerImpl**（大部分功能都委托给该类去实现）

### Consumer订阅Topic

这里将主要就是给consumer队列负载服务设置了订阅信息并放到内存中 --> RebalanceImpl#subscriptionInner

```java
protected final ConcurrentMap<String /* topic */, SubscriptionData> subscriptionInner =
        new ConcurrentHashMap<String, SubscriptionData>();
```

### Consumer注册消息监听器

实际上是给**DefaultMQPushConsumerImpl**注册了消息到达的监听和消费，上面也已经说过，Consumer很多的功能都委托了该类去实现。我们这里注册的是并发消费的监听器，另一种是顺序消费的监听，后续我们会讲到顺序消费和并发消费的区别。

### Consumer启动 

1. 检查必要的参数consumerGroup、消费模式、consumerFromWhere、负载策略等配置
2. 拷贝订阅信息，监听重投队列%RETRY%TOPIC
3. 获取MQ实例，先从缓存中取，没有则创建
4. 设置重负载的消费组、消费模式、负载策略等
5. 实例化消息拉取的包装类并注册消息过滤的钩子
6. 选择对应的OffsetStore实现类，并加载offset。广播消息从本地获取offset （Consumer本地维护），集群消息从远程获取 （broker端维护） 
7.  启动消息消费服务
   1. 并发消费：15分钟执行一次，过期消息（15分钟还未被消费的消息）一次最多清理16条过期消息，将过期消息发回给broker
   2. 顺序消费：20s定时锁定当前实例消费的所有队列，上锁成功将ProcessQueue的lock属性设置为true
8. 启动MQClientInstance
   1. 启动请求和响应的通道
   2. 开启定时任务
      1. 定时30s拉取最新的broker和topic的路由信息
      2. 定时30s向broker发送心跳包
      3. 定时5s持久化consumer的offset
      4. 定时1分钟，动态调整线程池线程数量
   3. 启动消息拉取服务
   4. 每20s重新负载一次
9. 从nameserver拉取topic的订阅信息
10. 向broker校验客户端
11. 给所有的broker的master发送心跳包 、上传filterClass的源文件给FilterServer
12. 立即负载队列

```java
public synchronized void start() throws MQClientException {
        switch (this.serviceState) {
            //启动时默认状态
            case CREATE_JUST:
                log.info("the consumer [{}] start beginning. messageModel={}, isUnitMode={}", this.defaultMQPushConsumer.getConsumerGroup(),
                    this.defaultMQPushConsumer.getMessageModel(), this.defaultMQPushConsumer.isUnitMode());
                //预先设置为启动失败
                this.serviceState = ServiceState.START_FAILED;
                //检查必要配置  consumerGroup、消费模式、consumerFromWhere(默认CONSUME_FROM_LAST_OFFSET) 负载策略、subscription、是否顺序消费、消费最大最小线程数不能小于1 大于1000 等等参数
                this.checkConfig();
                //拷贝订阅信息，添加监听topic的重试topic %RETRY%TOPIC
                this.copySubscription();
                //如果是集群消费，将实例名称改为进程id
                if (this.defaultMQPushConsumer.getMessageModel() == MessageModel.CLUSTERING) {
                    this.defaultMQPushConsumer.changeInstanceNameToPID();
                }
                //获取MQ实例，先从缓存中取，没有创建后放入缓存
                this.mQClientFactory = MQClientManager.getInstance().getOrCreateMQClientInstance(this.defaultMQPushConsumer, this.rpcHook);
                //设置重新负载的消费组 消费模式 负载策略 MQ实例
                this.rebalanceImpl.setConsumerGroup(this.defaultMQPushConsumer.getConsumerGroup());
                this.rebalanceImpl.setMessageModel(this.defaultMQPushConsumer.getMessageModel());
                this.rebalanceImpl.setAllocateMessageQueueStrategy(this.defaultMQPushConsumer.getAllocateMessageQueueStrategy());
                this.rebalanceImpl.setmQClientFactory(this.mQClientFactory);
                //实例化消息拉取的包装类
                this.pullAPIWrapper = new PullAPIWrapper(
                    mQClientFactory,
                    this.defaultMQPushConsumer.getConsumerGroup(), isUnitMode());
                //注册消息过滤钩子
                this.pullAPIWrapper.registerFilterMessageHook(filterMessageHookList);
                //选择对应的OffsetStore实现类，广播消息从本地过去（offset Consumer本地维护），集群消息从远程获取 （broker端维护） 设置消费者的offsetStore
                if (this.defaultMQPushConsumer.getOffsetStore() != null) {
                    this.offsetStore = this.defaultMQPushConsumer.getOffsetStore();
                } else {
                    switch (this.defaultMQPushConsumer.getMessageModel()) {
                        //广播消费
                        case BROADCASTING:
                            this.offsetStore = new LocalFileOffsetStore(this.mQClientFactory, this.defaultMQPushConsumer.getConsumerGroup());
                            break;
                        //集群消费
                        case CLUSTERING:
                            this.offsetStore = new RemoteBrokerOffsetStore(this.mQClientFactory, this.defaultMQPushConsumer.getConsumerGroup());
                            break;
                        default:
                            break;
                    }
                    this.defaultMQPushConsumer.setOffsetStore(this.offsetStore);
                }
                //加载offset RemoteBrokerOffsetStore该方法为空实现 LocalFileOffsetStore读取路径为：/Users/niezhiliang/.rocketmq_offsets/192.168.3.6@DEFAULT/test-group/offsets.json
                this.offsetStore.load();
                //设置消息监听服务
                if (this.getMessageListenerInner() instanceof MessageListenerOrderly) {
                    this.consumeOrderly = true;
                    this.consumeMessageService =
                        new ConsumeMessageOrderlyService(this, (MessageListenerOrderly) this.getMessageListenerInner());
                } else if (this.getMessageListenerInner() instanceof MessageListenerConcurrently) {
                    this.consumeOrderly = false;
                    this.consumeMessageService =
                        new ConsumeMessageConcurrentlyService(this, (MessageListenerConcurrently) this.getMessageListenerInner());
                }
                //启动消息消费服务
                //并发消费：15分钟执行一次，过期消息（15分钟还未被消费的消息）一次最多清理16条过期消息，将过期消息发回给broker
                //顺序消费：20s定时锁定当前实例消费的所有队列，上锁成功将ProcessQueue的lock属性设置为true
                this.consumeMessageService.start();
                //注册消费者到 MQClientInstance.consumerTable key=groupid value = MQConsumerInner
                boolean registerOK = mQClientFactory.registerConsumer(this.defaultMQPushConsumer.getConsumerGroup(), this);
                //注册失败 关闭消息消费服务
                if (!registerOK) {
                    this.serviceState = ServiceState.CREATE_JUST;                   this.consumeMessageService.shutdown(defaultMQPushConsumer.getAwaitTerminationMillisWhenShutdown());
                    throw new MQClientException("The consumer group[" + this.defaultMQPushConsumer.getConsumerGroup()
                        + "] has been created before, specify another name please." + FAQUrl.suggestTodo(FAQUrl.GROUP_NAME_DUPLICATE_URL),
                        null);
                }
                //MQClientInstance 启动 这里面做了很多事情 启动拉取服务、定时心跳
                //定时更新offset 定时重新负载
                mQClientFactory.start();
                log.info("the consumer [{}] start OK.", this.defaultMQPushConsumer.getConsumerGroup());
                this.serviceState = ServiceState.RUNNING;
                break;
            case RUNNING:
            case START_FAILED:
            case SHUTDOWN_ALREADY:
                throw new MQClientException("The PushConsumer service state not OK, maybe started once, "
                    + this.serviceState
                    + FAQUrl.suggestTodo(FAQUrl.CLIENT_SERVICE_NOT_OK),
                    null);
            default:
                break;
        }
        //修改topic的订阅信息 当订阅信息改变时
        this.updateTopicSubscribeInfoWhenSubscriptionChanged();
        //向broker校验客户端
        this.mQClientFactory.checkClientInBroker();
        //1.给所有的broker master发送心跳包 2.上传filterClass的源文件给FilterServer
        this.mQClientFactory.sendHeartbeatToAllBrokerWithLock();
        //立即消费负载,将当前consumer负载得到的MessageQueue全部添加到PullMessageService.pullRequestQueue(阻塞队列) 然后
        //PullMessageService服务会开始拉取消息
        this.mQClientFactory.rebalanceImmediately();
    }
```

#### mQClientFactory启动

```java
public void start() throws MQClientException {

  synchronized (this) {
    switch (this.serviceState) {
        //默认状态
      case CREATE_JUST:
        this.serviceState = ServiceState.START_FAILED;
        // 如果nameserver地址为空，会去`http:// + WS_DOMAIN_NAME + ":8080/rocketmq/" + WS_DOMAIN_SUBGROUP`获取，
        // WS_DOMAIN_NAME由配置参数rocketmq.namesrv.domain设置，WS_DOMAIN_SUBG由配置参数rocketmq.namesrv.domain.subgroup设置
        if (null == this.clientConfig.getNamesrvAddr()) {
          this.mQClientAPIImpl.fetchNameServerAddr();
        }
        // 开启请求和响应通道
        this.mQClientAPIImpl.start();
        // Start various schedule tasks
        /**
          * 1.定时30s拉取最新的broker和topic的路由信息
          * 2.定时30s向broker发送心跳包
          * 3.定时5s持久化consumer的offset
          * 4.定时1分钟，动态调整线程池线程数量
          */
        this.startScheduledTask();
        // 启动消息拉去服务 如果有是producer 该服务没有consumer
        this.pullMessageService.start();
        /**
          * 消息队列重新负载，默认为平均负载
          * 20s重新更新一次consumer的消费队列
          */
        this.rebalanceService.start();
        // 启动producer消息推送服务
        this.defaultMQProducer.getDefaultMQProducerImpl().start(false);
        log.info("the client factory [{}] start OK", this.clientId);
        this.serviceState = ServiceState.RUNNING;
        break;
      case START_FAILED:
        throw new MQClientException("The Factory object[" + this.getClientId() + "] has been created before, and failed.", null);
      default:
        break;
    }
  }
}
```

### 总结

总的来说，Consumer的启动过程比较简单，代码也不是很难懂。主要就是检查配置参数、获取MQ实例、给重新负载服务设置消费组和负载策略等属性、根据消费模式选择对应的消费服务加载offset并启动、启动MQClientInstance、给broker心跳、立即给consumer队列负载。