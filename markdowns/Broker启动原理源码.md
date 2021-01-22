## Broker启动流程

### 前言

今天我们讲Broker的启动流程，讲完这篇以后，大家对Broker启动做的事情有一个清晰的认识，这个源码是我自己一点一点啃得，有点硬，后面再整理代码注释的时候，参考了一些

博客，我尽可能的说的清楚一些，我表达和总结能力比较弱，有些地方说的可能不清楚，大家可以给我留言，我看到了都会回复的。

## 源码Idea启动Broker

找到Broker的启动类，BrokerStartup，然后编辑main方法，修改启动参数，如下图所示

<img src="http://java-imgs.oss-cn-hongkong.aliyuncs.com/2021/1/12/20210112111300.png" style="zoom:50%;float:left" />

环境目录会在下图所示的包中

<img src="http://java-imgs.oss-cn-hongkong.aliyuncs.com/2021/1/12/%E5%BE%AE%E4%BF%A1%E6%88%AA%E5%9B%BE_20210112111637.png" style="zoom:50%;float:left;" />

具体配置：

`ROCKETMQ_HOME=ROCKETMQ_HOME=E:\self\rocketmq\distribution`

`-C E:\self\rocketmq\distribution/conf/broker.conf`，

启动之前先修改broker.conf中nameserver的地址，idea跑nameserver方法比跑Broker还要简单，只需要添加RocketMQ的环境地址。

然后启动main方法，如果打印下面这行日志，说明启动成功了

```java
Connected to the target VM, address: '127.0.0.1:53208', transport: 'socket'
```

## 源码讲解

Broker的启动类BrokerStartup的main方法就是源码的入口，看起来非常简单，第一步先创建BrokerContoller，第二步启动这个BrokerContoller，所以我们一步一步来讲。

```java
public static void main(String[] args) {
    start(createBrokerController(args));
}
```

### 创建BrokerController

BrokerController创建过程主要分为了解析命令行参数、初始化Broker配置、初始化BrokerController，注册JVM退出的钩子，我们一个个来看

**1.解析启动命令行填充参数**

```java
Options options = ServerUtil.buildCommandlineOptions(new Options());
//解析命令行参数 -n localhost:9876  -c  -p   -h  -m 
commandLine = ServerUtil.parseCmdLine("mqbroker", args, buildCommandlineOptions(options),
                                      new PosixParser());
```

> -n 指定nameserver地址，集群以;分割  -c 指定broker的配置文件   -p 打印出所有的配置项  -h 打印出帮助命令   -m  打印出重要的配置项  其实在broker.conf中指定了nameserver的地址 就不需要使用-n命令来指定

这句代码会把我们上面指定的broker.conf地址给解析出来，然后去加载这个文件，解析就是为了下面这句代码做准备

```java
//实例化broker配置
final BrokerConfig brokerConfig = new BrokerConfig();
//实例化消息持久化配置
final MessageStoreConfig messageStoreConfig = new MessageStoreConfig();
//实例化服务端配置
final NettyServerConfig nettyServerConfig = new NettyServerConfig();
//实例化客户端配置
final NettyClientConfig nettyClientConfig = new NettyClientConfig();
//..... 中间省略了很多系解析配置的代码
MixAll.properties2Object(properties, brokerConfig);
MixAll.properties2Object(properties, nettyServerConfig);
MixAll.properties2Object(properties, nettyClientConfig);
MixAll.properties2Object(properties, messageStoreConfig);
MixAll.properties2Object(ServerUtil.commandLine2Properties(commandLine), brokerConfig);
```

BrokerConfig一些重要的配置信息如下：

namesrvAddr、brokerIP1、brokerName、brokerClusterName、brokerId、transactionCheckMax（事务消息最大回查次数）、transactionCheckInterval（事务消息多久没收到确认开始回调）

nettyServerConfig和nettyClientConfig配置信息：

<img src="http://java-imgs.oss-cn-hongkong.aliyuncs.com/2021/1/12/20210112135302.png" style="zoom:80%;float:left;" />

messageStoreConfig一些重要的配置信息：

storePathCommitLog（commitLogd地址）、mappedFileSizeCommitLog（mapperFile大小）、deleteWhen（过期消息删除时间 默认凌晨四点）、fileReservedTime（消息保存时间默认48h）、brokerRole、flushDiskType

、messageDelayLevel（延迟消息延迟等级对应时间）、syncFlushTimeout（同步刷盘超时）

**2.实例化BrokerController**

- 设置BrokerController的nettyServer 
- 设置BrokerController的nettyClient 
- 设置BrokerController的messageStroreConfig
- 设置consumer的offset管理器
- 设置topic配置管理器TopicConfigManager（会自动创建系统的那些topic）
- 设置消息拉取请求的处理器PullMessageProcessor
- 设置consumer管理器consumerManager
- 设置producer管理器ProducerManager
- 设置请求的BrokerOuterAPI对外请求的api接口
- 创建一系列的队列、为了创建线程池准备的 eg：心跳、客户端管理、消息拉取等



**3.BrokerController初始化**

- 加载持久化配置文件
  - 通过加载store/config/topic.json文件加载当前broker所有的topic（系统和用户创建），然后包装成了`TopicConfigSerializeWrapper`，最后存到`TopicConfigManager的topicConfigTable`中

- 加载store/config/consumerOffset.json文件加载所有consumer的offset，最终解析成了`ConsumerOffsetManager`

- 加载store/config/subscriptionGroup.json，加载当前broker所有的订阅者,最终解析成了`SubscriptionGroupManager`

- 加载store/config/consumerFilter.json，加载consumer的过滤条件，最终解析成了`ConsumerFilterManager`

- 实例化MessageStore
  - 创建消息到达监听器 --> `NotifyMessageArrivingListener`
  - 创建Commitlog（Commitlog/DLedgerCommitLog）
    - 系统Commitlog如果是同步：创建`GroupCommitService`   如果异步：创建`FlushRealTimeService`
    - 设置消息追加的回调  --> `DefaultAppendMessageCallback`
    - 选择添加消息时锁的方式：4.0之前默认使用`ReentrantLock`，现在默认都是使用自旋锁
  - 创建刷consumerqueue服务（1s一次）
  - 创建定时删除过期的mappedFile服务（默认凌晨4点删除72小时未修改过的文件）
  - 创建删除consumerQueue和index的mappedFile （小于commitlog最小的offset的文件）
  - 创建存储层内部统计服务
  - 创建commitlog主从同步的服务
  - 创建延迟消息监控类，到期自动执行
- 从MessageStore中获取昨日和今日消息拉去的数量和发送数量
- 加载commitlog
  - 延迟消息加载延迟消息的offset以及延迟等级对应的延迟时间到内存  
  - commitlog的mappedFile文件到内存中 --> MappedFileQueue.mappedFiles<CopyOnWriteArrayList<MappedFile>>
  - 加载consumerQueue并放到内存中  --> DefaultMessageStore.consumeQueueTable<ConcurrentMap<String/* topic */, ConcurrentMap<Integer/* queueId */, ConsumeQueue>>>
  - 加载index文件到内存
- 根据brokerConfig配置实例化一些线程池
- 创建一些定时器
  - 一天执行一次记录broker一天的消息拉取量
  - 5s执行一次更新consumer的offset值
  - 1s打印一些关于Queue Size的日志  包含 Pull、 Queue、 Transaction
- nameServerAddress不为空修改内存中的nameServerAddress，如果为空，2分钟向服务器拉取nameServerAddress
- 如果没开Dleger（主从自动切换功能） 如果Broker是master打印出master和slave之间的offset差
- 初始化事务消息的处理Service 和消息状态回查的Service
- 初始化访问控制列表（acl权限控制）
- 初始化RpcHooks

```java
public boolean initialize() throws CloneNotSupportedException {
    //加载topic   topic.json
    boolean result = this.topicConfigManager.load();
    //加载consumer的offset
    result = result && this.consumerOffsetManager.load();
    //加载当前broker所有的订阅者
    result = result && this.subscriptionGroupManager.load();
    //加载consumer的过滤
    result = result && this.consumerFilterManager.load();

    if (result) {
        try {
            this.messageStore =
                new DefaultMessageStore(this.messageStoreConfig, this.brokerStatsManager, this.messageArrivingListener,
                                        this.brokerConfig);
            //是否打开了主从自动切换（delger）
            if (messageStoreConfig.isEnableDLegerCommitLog()) {
                DLedgerRoleChangeHandler roleChangeHandler = new DLedgerRoleChangeHandler(this, (DefaultMessageStore) messageStore);
                ((DLedgerCommitLog)((DefaultMessageStore) messageStore).getCommitLog()).getdLedgerServer().getdLedgerLeaderElector().addRoleChangeHandler(roleChangeHandler);
            }
            //加载昨天和今天消息获取和发送的总数
            this.brokerStats = new BrokerStats((DefaultMessageStore) this.messageStore);
            //load plugin 加载MessageStore上下文
            MessageStorePluginContext context = new MessageStorePluginContext(messageStoreConfig, brokerStatsManager, messageArrivingListener, brokerConfig);
            this.messageStore = MessageStoreFactory.build(context, this.messageStore);
            this.messageStore.getDispatcherList().addFirst(new CommitLogDispatcherCalcBitMap(this.brokerConfig, this.consumerFilterManager));
        } catch (IOException e) {
            result = false;
            log.error("Failed to initialize", e);
        }
    }
    //加载commitlog内容  ConsumeQueue 刷盘时间点
    /**
     * lod commitlog  --> MappedFileQueue CopyOnWriteArrayList<MappedFile> mappedFiles;
     *                                          topic                  queueId
     * load consumer queue  -->   ConcurrentMap<String, ConcurrentMap<Integer, ConsumeQueue>> consumeQueueTable;
     * storeCheckpoint  记录commitlog  consumer queue  index 最近的输盘时间点，其中只用该文件的前 24个字节，结构图：	https://segmentfault.com/img/bVbpQoU?w=486&h=55
     * load index --> IndexService.ArrayList<IndexFile> indexFileList
     */
    result = result && this.messageStore.load();

    /**
     * 实例化一系列线程池
     */
    if (result) {
        this.remotingServer = new NettyRemotingServer(this.nettyServerConfig, this.clientHousekeepingService);
        NettyServerConfig fastConfig = (NettyServerConfig) this.nettyServerConfig.clone();
        fastConfig.setListenPort(nettyServerConfig.getListenPort() - 2);
        this.fastRemotingServer = new NettyRemotingServer(fastConfig, this.clientHousekeepingService);
        this.sendMessageExecutor = new BrokerFixedThreadPoolExecutor(
            this.brokerConfig.getSendMessageThreadPoolNums(),
            this.brokerConfig.getSendMessageThreadPoolNums(),
            1000 * 60,
            TimeUnit.MILLISECONDS,
            this.sendThreadPoolQueue,
            new ThreadFactoryImpl("SendMessageThread_"));

        this.pullMessageExecutor = new BrokerFixedThreadPoolExecutor(
            this.brokerConfig.getPullMessageThreadPoolNums(),
            this.brokerConfig.getPullMessageThreadPoolNums(),
            1000 * 60,
            TimeUnit.MILLISECONDS,
            this.pullThreadPoolQueue,
            new ThreadFactoryImpl("PullMessageThread_"));

        this.replyMessageExecutor = new BrokerFixedThreadPoolExecutor(
            this.brokerConfig.getProcessReplyMessageThreadPoolNums(),
            this.brokerConfig.getProcessReplyMessageThreadPoolNums(),
            1000 * 60,
            TimeUnit.MILLISECONDS,
            this.replyThreadPoolQueue,
            new ThreadFactoryImpl("ProcessReplyMessageThread_"));

        this.queryMessageExecutor = new BrokerFixedThreadPoolExecutor(
            this.brokerConfig.getQueryMessageThreadPoolNums(),
            this.brokerConfig.getQueryMessageThreadPoolNums(),
            1000 * 60,
            TimeUnit.MILLISECONDS,
            this.queryThreadPoolQueue,
            new ThreadFactoryImpl("QueryMessageThread_"));

        this.adminBrokerExecutor =
            Executors.newFixedThreadPool(this.brokerConfig.getAdminBrokerThreadPoolNums(), new ThreadFactoryImpl(
                "AdminBrokerThread_"));

        this.clientManageExecutor = new ThreadPoolExecutor(
            this.brokerConfig.getClientManageThreadPoolNums(),
            this.brokerConfig.getClientManageThreadPoolNums(),
            1000 * 60,
            TimeUnit.MILLISECONDS,
            this.clientManagerThreadPoolQueue,
            new ThreadFactoryImpl("ClientManageThread_"));

        this.heartbeatExecutor = new BrokerFixedThreadPoolExecutor(
            this.brokerConfig.getHeartbeatThreadPoolNums(),
            this.brokerConfig.getHeartbeatThreadPoolNums(),
            1000 * 60,
            TimeUnit.MILLISECONDS,
            this.heartbeatThreadPoolQueue,
            new ThreadFactoryImpl("HeartbeatThread_", true));

        this.endTransactionExecutor = new BrokerFixedThreadPoolExecutor(
            this.brokerConfig.getEndTransactionThreadPoolNums(),
            this.brokerConfig.getEndTransactionThreadPoolNums(),
            1000 * 60,
            TimeUnit.MILLISECONDS,
            this.endTransactionThreadPoolQueue,
            new ThreadFactoryImpl("EndTransactionThread_"));

        this.consumerManageExecutor =
            Executors.newFixedThreadPool(this.brokerConfig.getConsumerManageThreadPoolNums(), new ThreadFactoryImpl(
                "ConsumerManageThread_"));

        this.registerProcessor();

        /**
         * 记录broker一天拉取的消息,一天执行一次
         */
        final long initialDelay = UtilAll.computeNextMorningTimeMillis() - System.currentTimeMillis();
        final long period = 1000 * 60 * 60 * 24;
        this.scheduledExecutorService.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                try {
                    BrokerController.this.getBrokerStats().record();
                } catch (Throwable e) {
                    log.error("schedule record error.", e);
                }
            }
        }, initialDelay, period, TimeUnit.MILLISECONDS);

        /**
         * 每5s更新consumer offset的值
         */
        this.scheduledExecutorService.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                try {
                    BrokerController.this.consumerOffsetManager.persist();
                } catch (Throwable e) {
                    log.error("schedule persist consumerOffset error.", e);
                }
            }
        }, 1000 * 10, this.brokerConfig.getFlushConsumerOffsetInterval(), TimeUnit.MILLISECONDS);


        this.scheduledExecutorService.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                try {
                    BrokerController.this.consumerFilterManager.persist();
                } catch (Throwable e) {
                    log.error("schedule persist consumer filter error.", e);
                }
            }
        }, 1000 * 10, 1000 * 10, TimeUnit.MILLISECONDS);

        /**
         * 3分钟检查一次，消费者落后一定数量后 消费组暂停消费
         */
        this.scheduledExecutorService.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                try {
                    BrokerController.this.protectBroker();
                } catch (Throwable e) {
                    log.error("protectBroker error.", e);
                }
            }
        }, 3, 3, TimeUnit.MINUTES);

        /**
         * 每分钟打印一些日志 内容包括 拉取的 Send Queue Size
         * Pull Queue Size Query Queue Size   Transaction Queue Size
         */
        this.scheduledExecutorService.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                try {
                    BrokerController.this.printWaterMark();
                } catch (Throwable e) {
                    log.error("printWaterMark error.", e);
                }
            }
        }, 10, 1, TimeUnit.SECONDS);

        /**
         * 每分钟打印一次落后的字节
         */
        this.scheduledExecutorService.scheduleAtFixedRate(new Runnable() {

            @Override
            public void run() {
                try {
                    log.info("dispatch behind commit log {} bytes", BrokerController.this.getMessageStore().dispatchBehindBytes());
                } catch (Throwable e) {
                    log.error("schedule dispatchBehindBytes error.", e);
                }
            }
        }, 1000 * 10, 1000 * 60, TimeUnit.MILLISECONDS);

        /**
         * 修改nameServerAddress 如果为空 ，通过httpClient定时去拉取
         * 这个拉取地址和consumer没设置nameServAddr拉取的地址一样
         */
        if (this.brokerConfig.getNamesrvAddr() != null) {
            this.brokerOuterAPI.updateNameServerAddressList(this.brokerConfig.getNamesrvAddr());
            log.info("Set user specified name server address: {}", this.brokerConfig.getNamesrvAddr());
        } else if (this.brokerConfig.isFetchNamesrvAddrByAddressServer()) {
            this.scheduledExecutorService.scheduleAtFixedRate(new Runnable() {

                @Override
                public void run() {
                    try {
                        BrokerController.this.brokerOuterAPI.fetchNameServerAddr();
                    } catch (Throwable e) {
                        log.error("ScheduledTask fetchNameServerAddr exception", e);
                    }
                }
            }, 1000 * 10, 1000 * 60 * 2, TimeUnit.MILLISECONDS);
        }
        /**
         * 如果没有打开主从自动切换功能，
         * 如果Broker是SLAVE，高可用集群不为空，修改master地址，并将定期修改地址关闭
         * 如果是master,打印出master和slaver之间的offset差
         */
        if (!messageStoreConfig.isEnableDLegerCommitLog()) {
            if (BrokerRole.SLAVE == this.messageStoreConfig.getBrokerRole()) {
                if (this.messageStoreConfig.getHaMasterAddress() != null && this.messageStoreConfig.getHaMasterAddress().length() >= 6) {
                    this.messageStore.updateHaMasterAddress(this.messageStoreConfig.getHaMasterAddress());
                    this.updateMasterHAServerAddrPeriodically = false;
                } else {
                    this.updateMasterHAServerAddrPeriodically = true;
                }
            } else {
                this.scheduledExecutorService.scheduleAtFixedRate(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            BrokerController.this.printMasterAndSlaveDiff();
                        } catch (Throwable e) {
                            log.error("schedule printMasterAndSlaveDiff error.", e);
                        }
                    }
                }, 1000 * 10, 1000 * 60, TimeUnit.MILLISECONDS);
            }
        }

        if (TlsSystemConfig.tlsMode != TlsMode.DISABLED) {
            // Register a listener to reload SslContext
            try {
                fileWatchService = new FileWatchService(
                    new String[] {
                        TlsSystemConfig.tlsServerCertPath,
                        TlsSystemConfig.tlsServerKeyPath,
                        TlsSystemConfig.tlsServerTrustCertPath
                    },
                    new FileWatchService.Listener() {
                        boolean certChanged, keyChanged = false;

                        @Override
                        public void onChanged(String path) {
                            if (path.equals(TlsSystemConfig.tlsServerTrustCertPath)) {
                                log.info("The trust certificate changed, reload the ssl context");
                                reloadServerSslContext();
                            }
                            if (path.equals(TlsSystemConfig.tlsServerCertPath)) {
                                certChanged = true;
                            }
                            if (path.equals(TlsSystemConfig.tlsServerKeyPath)) {
                                keyChanged = true;
                            }
                            if (certChanged && keyChanged) {
                                log.info("The certificate and private key changed, reload the ssl context");
                                certChanged = keyChanged = false;
                                reloadServerSslContext();
                            }
                        }

                        private void reloadServerSslContext() {
                            ((NettyRemotingServer) remotingServer).loadSslContext();
                            ((NettyRemotingServer) fastRemotingServer).loadSslContext();
                        }
                    });
            } catch (Exception e) {
                log.warn("FileWatchService created error, can't load the certificate dynamically");
            }
        }
        //初始化事务消息的处理Service 和消息状态回查的Service
        initialTransaction();
        //初始化访问控制列表
        initialAcl();
        //初始化钩子
        initialRpcHooks();
    }
    return result;
}
```

**4.向JVM注册关闭钩子**

当jvm关闭的时候，会执行这个钩子函数，关闭定时器，向所有namerServer注销注册信息

```java
 /**
  * 向系统注册一个关闭前执行的钩子
  * 当jvm关闭的时候，会执行所有添加的addShutdownHook钩子
  */
Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
    private volatile boolean hasShutdown = false;
    private AtomicInteger shutdownTimes = new AtomicInteger(0);

    @Override
    public void run() {
        synchronized (this) {
            log.info("Shutdown hook was invoked, {}", this.shutdownTimes.incrementAndGet());
            if (!this.hasShutdown) {
                this.hasShutdown = true;
                long beginTime = System.currentTimeMillis();
                /**
                 * 关闭定时器，先namerServer注销注册信息
                 */
                controller.shutdown();
                long consumingTimeTotal = System.currentTimeMillis() - beginTime;
                log.info("Shutdown hook over, consuming total time(ms): {}", consumingTimeTotal);
            }
        }
    }
}, "ShutdownHook"));
```

**5.BrokerController启动**

- 启动messageStore线程
  - 启动刷盘线程
  - 创建store文件夹
  - 创建一下定时器
    - 定时起立commitlog中72小时还未消费的消息
    - 检查commitlog是否满了，默认大小1G
- 启动netty路由服务线程
- 启动快速路由服务
- 启动SSL证书文件监听线程
- 启动broker对外的api
- 启动消息拉取辅助服务，当消息到达，通知PullMessageProcessor处理
- 启动客户端心跳服务，每10s清理有问题的netty通道
- broker主从数据同步
- 向nameserver注册broker
- 启动broker向nameserver心跳的定时器，默认30s一次，心跳间隔值只能设置在10s - 60s之间

```java
public void start() throws Exception {
    /**
     * 1.启动刷盘线程 如果是同步刷盘 启动GroupCommitService
     * 异步启动FlushRealTimeService
     * 2.服务高可用，进行commitlog数据的主从同步
     * 3.创建store文件夹
     * 4.创建一些定时器
     *  1.定时清理commitlog中72小时还未消费的消息
     *  2.检查commitlog是否满了 默认大小1G
     */
    if (this.messageStore != null) {
        this.messageStore.start();
    }

    /**
     * 启动netty路由服务,路由consumer和producer的请求
     */
    if (this.remotingServer != null) {
        this.remotingServer.start();
    }

    /**
     * 快速路由服务
     * 主要用于扫描生产者和消费者是否还存活
     */
    if (this.fastRemotingServer != null) {
        this.fastRemotingServer.start();
    }

    /**
     * 关注文件变更的服务，及时加载最新的ssl证书
     * 通过对文件进行hash 判断新的hash和当前hash是否一致  不一致
     * 表示文件变更了
    */
    if (this.fileWatchService != null) {
        this.fileWatchService.start();
    }

    //启动broker对外的api
    if (this.brokerOuterAPI != null) {
        this.brokerOuterAPI.start();
    }

    /**
     * 消息拉取辅助服务，主要看消息是否到达， 处理客户端的拉去请求
     * 当消息到达，通知PullMessageProcessor处理
     */
    if (this.pullRequestHoldService != null) {
        this.pullRequestHoldService.start();
    }

    /**
     * 客户端心跳服务
     * 启动定时器 每10s清理没用的链接
     */
    if (this.clientHousekeepingService != null) {
        this.clientHousekeepingService.start();
    }

    /**
     * 过滤消息服务
     * 启动定时器 每30s 通过shell脚本启动startfsrv.sh
     * 自定义消息过滤服务，如果用系统的tag或者是sql 不需要开启该服务
     */
    if (this.filterServerManager != null) {
        this.filterServerManager.start();
    }

    /**
     * 主从数据同步
     * 没开启Dleger 则使用的是系统默认的CommitLog
     *
     */
    if (!messageStoreConfig.isEnableDLegerCommitLog()) {
        //如果是master 事务消息回调启动 默认6s一次 最多15次
        startProcessorByHa(messageStoreConfig.getBrokerRole());
        //如果是slave 10s同步一次master信息
        handleSlaveSynchronize(messageStoreConfig.getBrokerRole());
        /**
         * 向nameservaddr注册broker
         */
        this.registerBrokerAll(true, false, true);
    }

    /**
     * 定时向nameser注册   心跳
     * 心跳间隔只能是10s - 60s  默认30s
     */
    this.scheduledExecutorService.scheduleAtFixedRate(new Runnable() {

        @Override
        public void run() {
            try {
                BrokerController.this.registerBrokerAll(true, false, brokerConfig.isForceRegister());
            } catch (Throwable e) {
                log.error("registerBrokerAll Exception", e);
            }
        }
    }, 1000 * 10, Math.max(10000, Math.min(brokerConfig.getRegisterNameServerPeriod(), 60000)), TimeUnit.MILLISECONDS);

    //broker状态管理
    if (this.brokerStatsManager != null) {
        this.brokerStatsManager.start();
    }

    /**
     * 定期清理Queue中过期的请求
     */
    if (this.brokerFastFailure != null) {
        this.brokerFastFailure.start();
    }
}
```

### Broker注册方法

```java
public List<RegisterBrokerResult> registerBrokerAll(
    final String clusterName,
    final String brokerAddr,
    final String brokerName,
    final long brokerId,
    final String haServerAddr,
    final TopicConfigSerializeWrapper topicConfigWrapper,
    final List<String> filterServerList,
    final boolean oneway,
    final int timeoutMills,
    final boolean compressed) {

    final List<RegisterBrokerResult> registerBrokerResultList = Lists.newArrayList();
    //所有的nameserver地址
    List<String> nameServerAddressList = this.remotingClient.getNameServerAddressList();
    if (nameServerAddressList != null && nameServerAddressList.size() > 0) {
        /**
        * 构建请求头
        * 包含了broker地址 ip:port
        * broker的id 也就是角色  0 master > 0 slave
        * brokerName 
        * broker集群名称
        * 是否开启压缩
        */
        final RegisterBrokerRequestHeader requestHeader = new RegisterBrokerRequestHeader();
        requestHeader.setBrokerAddr(brokerAddr);
        requestHeader.setBrokerId(brokerId);
        requestHeader.setBrokerName(brokerName);
        requestHeader.setClusterName(clusterName);
        requestHeader.setHaServerAddr(haServerAddr);
        requestHeader.setCompressed(compressed);

        /**
        * 构建请求体
        * body  当前broker所有的topic信息，名称、读写队列数
        * 使用门闩依次向各个nameserver注册
        */
        RegisterBrokerBody requestBody = new RegisterBrokerBody();
        requestBody.setTopicConfigSerializeWrapper(topicConfigWrapper);
        requestBody.setFilterServerList(filterServerList);
        final byte[] body = requestBody.encode(compressed);
        final int bodyCrc32 = UtilAll.crc32(body);
        requestHeader.setBodyCrc32(bodyCrc32);
        final CountDownLatch countDownLatch = new CountDownLatch(nameServerAddressList.size());
        for (final String namesrvAddr : nameServerAddressList) {
            brokerOuterExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        RegisterBrokerResult result = registerBroker(namesrvAddr,oneway, timeoutMills,requestHeader,body);
                        if (result != null) {
                            registerBrokerResultList.add(result);
                        }

                        log.info("register broker[{}]to name server {} OK", brokerId, namesrvAddr);
                    } catch (Exception e) {
                        log.warn("registerBroker Exception, {}", namesrvAddr, e);
                    } finally {
                        countDownLatch.countDown();
                    }
                }
            });
        }

        try {
            countDownLatch.await(timeoutMills, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
        }
    }

    return registerBrokerResultList;
}
```

**Broker注册和心跳的信息**

<img src="http://java-imgs.oss-cn-hongkong.aliyuncs.com/2021/1/13/20210113162825.png" style="zoom:80%;float:left;" />



我对着代码我画了个简单流程图，将就看一下吧 

![](http://java-imgs.oss-cn-hongkong.aliyuncs.com/2021/1/16/%E7%B1%BB%E5%8A%A0%E8%BD%BD%E6%97%B6%E5%BA%8F%E5%9B%BE%20%281%29.png)