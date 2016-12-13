package org.opennms.core.ipc.sink.kafka;

import java.util.Properties;

import org.opennms.core.camel.JmsQueueNameFactory;
import org.opennms.core.ipc.sink.api.Message;
import org.opennms.core.ipc.sink.api.MessageProducer;
import org.opennms.core.ipc.sink.api.MessageProducerFactory;
import org.opennms.core.ipc.sink.api.SinkModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;

import joptsimple.internal.Objects;
import kafka.javaapi.producer.Producer;
import kafka.producer.KeyedMessage;
import kafka.producer.ProducerConfig;

public class KafkaRemoteMessageProducerFactory implements MessageProducerFactory, InitializingBean {
    private static final Logger LOG = LoggerFactory.getLogger(KafkaRemoteMessageProducerFactory.class);

    private String m_kafkaAddress = "127.0.0.1:9092";
    private String m_zookeeperHost = "127.0.0.1";
    private Integer m_zookeeperPort = 2181;
    private String m_groupId = "kafkaMessageConsumer";

    @Override
    public <T extends Message> MessageProducer<T> getProducer(final SinkModule<T> module) {
        final String topic = new JmsQueueNameFactory(KafkaSinkConstants.KAFKA_TOPIC_PREFIX, module.getId()).getName();
        LOG.debug("getProducer({}): creating MessageProducer.", topic);

        final Properties props = new Properties();
        props.put("metadata.broker.list", m_kafkaAddress);
        props.put("request.required.acks", "1");
        props.put("serializer.class", "kafka.serializer.StringEncoder");
        /*
        props.put("retries", 0);
        props.put("batch.size", 16384);
        props.put("linger.ms", 1);
        props.put("buffer.memory", 33554432);
        */

        // TODO implement a partitioning scheme
        //props.put("partitioner.class", "");

        LOG.debug("getProducer({}): using config {}", topic, props);
        final ProducerConfig config = new ProducerConfig(props);
        final Producer<String, String> producer = new Producer<>(config);

        return new MessageProducer<T>() {
            @Override
            public void send(final T message) {
                LOG.debug("getProducer({}): config={}: sending message {}", topic, props, message);
                final KeyedMessage<String,String> record = new KeyedMessage<>(topic, module.marshal(message));
                producer.send(record);
                //LOG.debug("getProducer({}): sent message {}", topic, message);
            }
            
        };
    }

    public String getKafkaAddress() {
        return m_kafkaAddress;
    }
    public void setKafkaAddress(final String kafkaAddress) {
        m_kafkaAddress = kafkaAddress;
    }
    
    public String getZookeeperHost() {
        return m_zookeeperHost;
    }
    public void setZookeeperHost(final String zookeeperHost) {
        m_zookeeperHost = zookeeperHost;
    }

    public Integer getZookeeperPort() {
        return m_zookeeperPort;
    }
    public void setZookeeperPort(final Integer zookeeperPort) {
        m_zookeeperPort = zookeeperPort;
    }

    public String getGroupId() {
        return m_groupId;
    }
    public void setGroupId(final String groupId) {
        m_groupId = groupId;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        Objects.ensureNotNull(m_kafkaAddress);
        Objects.ensureNotNull(m_zookeeperHost);
        Objects.ensureNotNull(m_zookeeperPort);
        Objects.ensureNotNull(m_groupId);
        LOG.debug("KafkaRemoteMessageProducerFactory: kafkaAddress={}, zookeeperHost={}, zookeeperPort={}, groupId={}", m_kafkaAddress, m_zookeeperHost, m_zookeeperPort, m_groupId);
    }
}
