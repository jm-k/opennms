package org.opennms.core.ipc.sink.kafka;

import java.util.Objects;
import java.util.Properties;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.opennms.core.camel.JmsQueueNameFactory;
import org.opennms.core.ipc.sink.api.Message;
import org.opennms.core.ipc.sink.api.MessageProducer;
import org.opennms.core.ipc.sink.api.MessageProducerFactory;
import org.opennms.core.ipc.sink.api.SinkModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;

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
        props.put("bootstrap.servers", m_kafkaAddress);
        props.put("acks", "all");
        props.put("retries", 0);
        /*
        props.put("batch.size", 16384);
        props.put("linger.ms", 1);
        props.put("buffer.memory", 33554432);
        */
        props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");

        // TODO implement a partitioning scheme
        //props.put("partitioner.class", "");

        LOG.debug("getProducer({}): using config {}", topic, props);
        final Producer<String,String> producer = new KafkaProducer<>(props);

        return new MessageProducer<T>() {
            @Override
            public void send(final T message) {
                LOG.debug("getProducer({}): config={}: sending message {}", topic, props, message);
                final ProducerRecord<String,String> record = new ProducerRecord<>(topic, module.marshal(message));
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
        Objects.requireNonNull(m_kafkaAddress);
        Objects.requireNonNull(m_zookeeperHost);
        Objects.requireNonNull(m_zookeeperPort);
        Objects.requireNonNull(m_groupId);
        LOG.debug("KafkaRemoteMessageProducerFactory: kafkaAddress={}, zookeeperHost={}, zookeeperPort={}, groupId={}", m_kafkaAddress, m_zookeeperHost, m_zookeeperPort, m_groupId);
    }
}
