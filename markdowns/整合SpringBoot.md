### 前言

上一篇我们讲了消息的发送和消息消费的一些知识，本篇我们开始讲RocketMQ整合SpringBoot。

### RocketMQ整合SpringBoot

我会讲两种整合SpringBoot的方式，一种使用SpringBoot的starter来整合，另外一种我们自己整合，相对来说，Starter整合方式更加单，不过技多不压身嘛，两种都学习一下。

#### Starter方式整合

**pom.xml文件**

```xml
<!-- web -->
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-web</artifactId>
</dependency>
<!-- rocket starter-->
<dependency>
  <groupId>org.apache.rocketmq</groupId>
  <artifactId>rocketmq-spring-boot-starter</artifactId>
  <version>2.1.0</version>
</dependency>
<!-- lombok -->
<dependency>
  <groupId>org.projectlombok</groupId>
  <artifactId>lombok</artifactId>
</dependency>	
```

**yaml文件**

```yaml
rocketmq:
  #nameserver地址
  name-server: 127.0.0.1:9876
  producer:
    #生产者组
    group: boot-group
    #消息发送超时
    send-message-timeout: 300000
    compress-message-body-threshold: 4096
    #消息最大大小  默认最大4M
    max-message-size: 4194304
    #失败重投间隔时间
    retry-times-when-send-async-failed: 0
    #重试重投次数
    retry-times-when-send-failed: 2
  #消息发送topic  
  topic: testMsg
```

**启动类**

为了简化工程的类，我把消息发送和程序启动类放到了一块，启动以后，通过浏览器访问send方法，将消息发送出去，代码逻辑很简单，就是每次访问send方法，Producer就会broker发送5条消息，为了区别消息内容，这里使用了个计数器一直累加。这里需要提一嘴，

由于Starter是SpringBoot封装的，这个Message是Spring自己封装的一个类，并不是我们RocketMQ的那个Message，具体发送内容通过`MessageBuilder.withPayload("xxxx").build()`来构建一条消息，我们还可以通过`setHeader()`来设置一些自定义的属性，这个Hearder实际上设置的是`Message`的Properties属性。

```java
@SpringBootApplication
@RestController
public class BootApplication {
    @Autowired
    private RocketMQTemplate rocketMQTemplate;
		//累加消息条数
    private static AtomicInteger num = new AtomicInteger(0);

    public static void main(String[] args) {
        SpringApplication.run(BootApplication.class);
    }

    /**
     * 测试发送
     * @return
     */
    @GetMapping(value = "send")
    public String send() {
        for (int i = 0; i < 5; i++) {
            Message<String> message = MessageBuilder.withPayload("hello boot :" + num.addAndGet(1))
                    //添加消息头信息（支持自定义属性）
                    .setHeader("TAGS","*")
                    .setHeader("num",i)
                    .build();
            rocketMQTemplate.send("testMsg",message);
        }
        return "ok";
    }
}
```

**消费者监听**

消费就比较简单了，毕竟是别人封装了一层，我们只需要通过`RocketMQMessageListener`注解来今天消息，我们设置注解的属性值，就能监听对应的topic和对消息进行过滤，我们可以使用泛型来对消息内容进行接收，我们拿到消息体后不用我们再转换成我们目标的类型，如果想要接收消息发送的元数据，我们可以使用`MessageExt`来接收，我们就能拿到消息的头信息和消息体，有些特殊的场景我们都会用到。

```java
@Slf4j
@Component
@RocketMQMessageListener(topic = "${rocketmq.topic}",consumerGroup = "defaultGroup",selectorExpression = "*")
public class BootMessageListener implements RocketMQListener<String> {
    @Override
    public void onMessage(String s) {
        log.info("boot reveiver: {}",s);
    }
}
```

> 接收消息原生内容

**测试**

浏览器访问该链接，producer会向broker发送五条信息，然后会被我们使用`@RocketMQMessageListener`类给消费掉

http://127.0.0.1:8080/send

- 消息体

![](http://java-imgs.oss-cn-hongkong.aliyuncs.com/2021/1/5/20210105111732.jpg)

- 原生消息

![](http://java-imgs.oss-cn-hongkong.aliyuncs.com/2021/1/5/20210105133912.jpg)

#### 手动整合

**pom.xml文件**

```xml
<!-- web -->
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-web</artifactId>
</dependency>
<!-- rocketmq -->
<dependency>
  <groupId>org.apache.rocketmq</groupId>
  <artifactId>rocketmq-client</artifactId>
  <version>4.7.1</version>
</dependency>
<!-- lombok -->
<dependency>
  <groupId>org.projectlombok</groupId>
  <artifactId>lombok</artifactId>
</dependency>
```

**yaml文件**

```yaml
rocketmq:
  my:
    nameServer: 127.0.0.1:9876
    producer:
      group: my-group
    consumer:
      group: test-group
    topic: testMsg
```

配置文件是我自定义的，具体根据业务来，并不需要完全按照这个配置来

**配置Consumer和Producer**

配置好Producer的配置和Consumer的配置并设置一个消息的监听类，然后将它们启动起来，并注册到spring容器中，发送消息时，只需要注入这两个类就行。

```java
package cn.isuyu.my.rocketmq.config;

import cn.isuyu.my.rocketmq.listen.ConsumerListener;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.common.protocol.heartbeat.MessageModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import javax.annotation.PreDestroy;

/**
 * @author : niezl
 * @date : 2021/1/3
 */
@Configuration
@Slf4j
public class RocketConfig {

    @Value("${rocketmq.my.nameServer}")
    private String namesrvAddr;

    @Value("${rocketmq.my.producer.group}")
    private String producerGroup;

    @Value("${rocketmq.my.consumer.group}")
    private String consumerGroup;

    @Value("${rocketmq.my.topic}")
    private String topic;

    private DefaultMQPushConsumer mqPushConsumer;

    private DefaultMQProducer mqProducer;


    /**
     * 注入produer
     * @return
     * @throws MQClientException
     */
    @Bean
    public DefaultMQProducer defaultMQProducer() throws MQClientException {
        mqProducer = new DefaultMQProducer(producerGroup);
        mqProducer.setNamesrvAddr(namesrvAddr);
        //启动produer
        mqProducer.start();
        log.info("RocketMQ producer start finished....");
        return mqProducer;
    }

    /**
     * 注入consumer
     * @return
     * @throws MQClientException
     */
    @Bean
    public DefaultMQPushConsumer defaultMQPushConsumer() throws MQClientException {
        mqPushConsumer = new DefaultMQPushConsumer(consumerGroup);
        mqPushConsumer.setNamesrvAddr(namesrvAddr);
        mqPushConsumer.setMessageModel(MessageModel.CLUSTERING);
        //监听topic和消息过滤条件
        mqPushConsumer.subscribe(topic,"*");
        //注册消息监听器
        mqPushConsumer.registerMessageListener(new ConsumerListener());
        //启动consumer
        mqPushConsumer.start();
        log.info("RocketMQ consumer start finished,liseten topic:{}....",topic);
        return mqPushConsumer;
    }

    /**
     * 容器关闭时，关闭produer和consumer
     */
    @PreDestroy
    public void beanDestroy() {
        mqProducer.shutdown();
        log.info("RocketMQ producer shutdown finished...");
        mqPushConsumer.shutdown();
        log.info("RocketMQ consumer shutdown finished...");
    }

}
```

**启动类**

这里启动类和Starter整合方式的目的是一样的，都是给broker发送五条信息，不过这里的Message是RocketrMQ自己的类

```java
@SpringBootApplication
@RestController
public class Application {
    @Autowired
    private DefaultMQProducer defaultMQProducer;

    private static AtomicInteger num = new AtomicInteger(0);

    public static void main(String[] args) {
        SpringApplication.run(Application.class);
    }

     /**
     * 消息发送
     * @return
     * @throws Exception
     */
    @GetMapping(value = "send")
    public String sendMsg() throws Exception {
        for (int i = 0; i < 5; i++) {
            Message message = new Message("testMsg",("hello " + num.addAndGet(1)).getBytes());
            defaultMQProducer.send(message);
        }
        return "ok";
    }
}
```

**消息监听**

```java
@Component
@Slf4j
public class ConsumerListener implements MessageListenerConcurrently {

    @Override
    public ConsumeConcurrentlyStatus consumeMessage(List<MessageExt> msgs, ConsumeConcurrentlyContext context) {
        log.info("my receiver: {}", JSON.toJSONString(msgs));
        for (MessageExt msg : msgs) {
            log.info("message Body:{}",new String(msg.getBody()));
        }
        return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
    }
}
```

**测试**

浏览器访问该链接，producer会想broker发送五条消息，然后被我们编写的consumer的监听类给消费掉

http://127.0.0.1:8080/send

![](http://java-imgs.oss-cn-hongkong.aliyuncs.com/2021/1/5/20210104180043.jpg)

### 总结

这两种方式，平常我们使用最多的还是**Starter方式**，毕竟这种方式使用起来太简单，而且该Starter为各种消息都封装了具体的实现方式，接收消息方式也给我们封装好了，可以在发送消息的时候，直接发送对象，然后泛型接收该消息，都不用我们进行任何转换。不像第二种方式，第二种我们如果要传个对象，必须将对象转成json字符串，然后转成byte，消费的时候，先转成json字符串，再转换成对象。不过第二种方式在不是springboot的项目中，没有starter，我们还是得自己写。

[源码地址：https://github.com/niezhiliang/rocketmq-demo](https://github.com/niezhiliang/rocketmq-demo)

