## Broker启动流程

### 前言

今天我们讲Broker的启动流程，讲完这篇以后，大家会对消息有个大致



### 源码Idea启动Broker

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
//初始化broker配置
final BrokerConfig brokerConfig = new BrokerConfig();
//初始化消息持久化配置
final MessageStoreConfig messageStoreConfig = new MessageStoreConfig();
//初始化服务端配置
final NettyServerConfig nettyServerConfig = new NettyServerConfig();
//初始化客户端配置
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

- 加载持久化文件
  - 通过加载store/config/topic.json文件加载当前broker所有的topic（系统和用户创建）
  - 加载store/config/consumerOffset.json文件加载所有consumer的offset
  - 加载store/config/subscriptionGroup.json，加载当前broker所有的订阅者
  - 加载
- 实例化MessageStore
- 从MessageStore中获取昨日和今日消息拉去的数量和发送数量
- 延迟消息加载延迟消息的offset到内存中以及延迟等级对应的延迟时间到内存
- 加载commitlog的mappedFile文件到内存中、加载consumerQueue并放到内存中、加载index文件到内存
- 根据brokerConfig配置实例化一些线程池
- 创建一些定时器
  - 一天执行一次记录broker一天的拉去量
  - 5s执行一次更新consumer的offset值
  - 1s打印一些关于Queue Size的日志  包含 Pull、 Queue、 Transaction
- nameServerAddress不为空修改内存中的nameServerAddress，如果为空，2分钟向服务器拉取nameServerAddress
- 如果没开Dleger（主从自动切换功能） 如果Broker是master打印出master和slave之间的offset差
- 初始化事务消息的处理Service 和消息状态回查的Service
- 初始化访问控制列表（acl权限控制）
- 初始化钩子？？？？

**4.向JVM注册关闭钩子**

当jvm关闭的时候，会执行这个钩子函数，关闭定时器，向所有namerServer注销注册信息

**5.BrokerController启动**

- 启动messageStore线程
  - 启动刷盘线程
  - 创建store文件夹
  - 创建一下定时器
    - 定时起立commitlog中72小时还未消费的消息
    - 检查commitlog是否满了，默认大小1G

- 启动netty相关线程
- 启动文件监听线程
- 启动NettyRemotingClient
- 启动消息到达监听，通知PullMessageProcessor处理
- 启动定时器，每10s清理有问题的netty通道
- 同步master的信息到slave
- 向nameserver注册broker
- 启动broker向nameserver心跳的定时器，默认30s一次，心跳间隔值只能设置在10s - 60s之间



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