### 前言

 最近一直在学`RocketMQ`的东西，为了加深对它的印象，决定写一个关于`RocketMQ`的主题博客，博客会涉及RocketMQ从安装、到使用、集群、以及各种原理源码。笔者水平有限，说的不对的地方，还希望各位老哥能够指出来，毕竟误人子弟不是我的初衷，我也是希望对读者们有一些帮助。

### 简介

RocketMQ是一个分布式消息中间件，最初是由阿里团队开发的，经过一系列的迭代升级，经历了淘宝双十一的大流量考验，很多小伙伴可能比较好奇市面上已经有了很多成熟的消息中间件、ActiveMQ、Kafka、RabbitMQ等。为啥阿里还要自己再开发一个RocketMQ,市面上的消息中间件有各自的优缺点，有点就不说了，我们说下它们的缺点，ActiveMQ不适合用于那种上千个队列应用的场景、比较适合那种中小型应用。Kafka会有数据丢失、消息延迟比较高、不支持事务、还强依赖Zookeeper,大数据应用场景用的比较多。RabbitMQ是Erlang编写的，入门难度较大，集群也不支持动态扩展。由于市面上是消息队中间件有各自的缺点，阿里团队就集百家之长，2012年RocketMQ就横空出世，经历了淘宝双十一超级大流量的考验，后期捐献给了Apache，成为了Apache的顶级开源项目。

### 优点

RocketMQ是由Java编写的，方便我们自己后期的扩展，具有低延迟、高吞吐量、高可用性、支持事务消息、顺序消费、延迟消息、还支持消息过滤和集群分片等

### 角色

#### NameServer

NameServer底层是Netty实现的，**提供了服务注册**、**路由管理**、**服务发现**的功能是一个无状态的节点，作为服务的发现者，集群中的各个角色都需要定时向NameServer上报自己的状态和同步数据信息，NameServer把`数据存储在内存中不会对数据进行持久化`NameServer部署多个的时候，其他角色需要分别向所有的NameServer上报自己的状态和同步数据信息，来确保高可用。NameServer之间互不通讯，所有NameServer都是平等的，没有主从的概念。

#### Broker

Broker主要负责消息的存储、投递和查询以及服务高可用保证、Broker启动时，会主动创建一些系统的Topic,然后向NameServer注册自己的信息，消息持久化采用文件的形式来实现，Broker也可以集群部署，角色分为Master和Slave，Slave只能进行消息的读操作，不能进行写操作，Master既可以读也可以写。还维持消息的消费的offset（记录消费的进度）

#### Producer

消息发布的角色，也支持集群部署，通过集群中的其中一个节点（随机选择）建立长连接，获得Topic的路由信息，包括Topic下面有哪些Queue，这些Queue分布在哪些Broker上等。向提供Topic服务的Master建立长连接，且定时向Master发送心跳

#### Consumer

消息消费的角色，通过NameServer集群获得Topic的路由信息，连接到对应的Broker上消费消息，由于Master和Slave都可以读取消息，因此Consumer会与Master和Slave都建立连接。



### 安装

系统环境：Centos 7

RocketMQ版本：4.7.1

Jdk:1.8

**1.下载源码** 

[源码地址：https://github.com/apache/rocketmq](https://github.com/apache/rocketmq)

后续源码笔者讲的是4.7.1版本，所以我们安装也选择4.7.1版本，Code按钮选择现在ZIP文件，下载完了上传到虚拟机。

![](http://java-imgs.oss-cn-hongkong.aliyuncs.com/2020/12/30/WX20201230093453.png)

**2.解压编译**

```shell
# 安装或更新unzip命令
yum install unzip -y

unzip rocketmq-rocketmq-all-4.7.1.zip

cd rocketmq-rocketmq-all-4.7.1
#这个命令执行完以后 会把rocketmq打包到distribution的target目录下
mvn -Prelease-all -DskipTests clean install -U
```

![](http://java-imgs.oss-cn-hongkong.aliyuncs.com/2020/12/30/20211230153653.jpg)

```shell
cd distribution/ 
cd target/
#把rocketmq移到opt目录下 以后我们只需要操作这个文件夹就行了
mv rocketmq-4.7.1/ /opt/

cd /opt/rocketmq-4.7.1
```

**3.启动NameServer**

默认堆大小为4g，太大了，很多机器都启动不起来，我们改成256m

```shell
cd bin/
#赋执行权限
chmod  777 *
#修改NameServer启动参数
vim  runserver.sh
```

![](http://java-imgs.oss-cn-hongkong.aliyuncs.com/2020/12/30/20211230111135.jpg)

```shell
#启动nameserver脚本
./mqnamesrv &
#后台运行
 nohup sh mqnamesrv &
```

成功标识（成功以后建议关闭后再执行一次后台运行命令）

![](http://java-imgs.oss-cn-hongkong.aliyuncs.com/2020/12/30/20211230142350.jpg)

**4.启动Broker**

```shell
#编辑broker启动参数
vim  runbroker.sh
```

![](http://java-imgs.oss-cn-hongkong.aliyuncs.com/2020/12/30/20211230142647.jpg)

修改一下broker.conf文件，把本机ip带上，我没带发现ip不是我本机的ip,导致我发送消息的时候，报连接超时异常


![](http://java-imgs.oss-cn-hongkong.aliyuncs.com/2020/12/30/20211230172320.jpg)

```shell
#启动broker 连接nameserver
./mqbroker -n localhost:9876
#后台运行
nohup sh mqbroker -n localhost:9876 -c ../conf/broker.conf &
```

成功标识

![](http://java-imgs.oss-cn-hongkong.aliyuncs.com/2020/12/30/20211230143054.jpg)

### 安装可视化界面

[源码地址：https://github.com/apache/rocketmq-externals](https://github.com/apache/rocketmq-externals)

**1.下载完以后，解压** 

![](http://java-imgs.oss-cn-hongkong.aliyuncs.com/2020/12/30/20211230143747.jpg)

**2.编译rocketmq-console**

```shell
cd rocketmq-externals/

cd rocketmq-console/

mvn clean install -DskipTests=true
```

![](http://java-imgs.oss-cn-hongkong.aliyuncs.com/2020/12/30/20211230144223.jpg)

**3.运行rocket-console**

```shell
cd target/
# 这里需要带上nameserver的地址
java -jar rocketmq-console-ng-2.0.0.jar --rocketmq.config.namesrvAddr=127.0.0.1:9876
```



可视化管理界面：http://127.0.0.1:8080/#/

![](http://java-imgs.oss-cn-hongkong.aliyuncs.com/2020/12/30/20211230145428.jpg)

### 测试

**消息发送**

```shell
#需要设置环境变量  不然读不到nameserver地址
export NAMESRV_ADDR=localhost:9876
#执行发送脚本
./tools.sh org.apache.rocketmq.example.quickstart.Producer
```

![](http://java-imgs.oss-cn-hongkong.aliyuncs.com/2020/12/30/20211230161233.jpg)



**消费测试**

```shell
#上面设置了环境变量 我们直接执行消费的脚本就行啦
./tools.sh org.apache.rocketmq.example.quickstart.Consumer
```

![](http://java-imgs.oss-cn-hongkong.aliyuncs.com/2020/12/30/20211230161257.jpg)



![](http://java-imgs.oss-cn-hongkong.aliyuncs.com/2020/12/30/20211230161330.jpg)

### 总结

这一篇简单讲了RocketMQ的角色，每个角色在RocketMQ中充当的角色，以及安装和简单的测试，下一篇会将RocketMQ的基本使用。