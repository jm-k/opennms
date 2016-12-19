/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2016 The OpenNMS Group, Inc.
 * OpenNMS(R) is Copyright (C) 1999-2016 The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is a registered trademark of The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * OpenNMS(R) is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with OpenNMS(R).  If not, see:
 *      http://www.gnu.org/licenses/
 *
 * For more information contact:
 *     OpenNMS(R) Licensing <license@opennms.org>
 *     http://www.opennms.org/
 *     http://www.opennms.com/
 *******************************************************************************/

package org.opennms.core.ipc.sink.kafka;

import java.util.Objects;
import java.util.Properties;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.opennms.core.camel.JmsQueueNameFactory;
import org.opennms.core.ipc.sink.api.Message;
import org.opennms.core.ipc.sink.api.SinkModule;
import org.opennms.core.ipc.sink.common.AbstractMessageDispatcherFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;

import com.codahale.metrics.JmxReporter;

public class KafkaRemoteMessageDispatcherFactory extends AbstractMessageDispatcherFactory<String> implements InitializingBean {
    private static final Logger LOG = LoggerFactory.getLogger(KafkaRemoteMessageDispatcherFactory.class);

    private String m_kafkaAddress = "127.0.0.1:9092";
    private String m_zookeeperHost = "127.0.0.1";
    private Integer m_zookeeperPort = 2181;
    private String m_groupId = "kafkaMessageConsumer";

    private JmxReporter reporter;

    private KafkaProducer<String,String> m_producer;

    private Properties m_config;

    @Override
    public <S extends Message, T extends Message> String getModuleMetadata(final SinkModule<S, T> module) {
        final JmsQueueNameFactory queueNameFactory = new JmsQueueNameFactory(KafkaSinkConstants.KAFKA_TOPIC_PREFIX, module.getId());
        return queueNameFactory.getName();
    }

    @Override
    public <S extends Message, T extends Message> void dispatch(SinkModule<S, T> module, String topic, T message) {
        startKafkaConsumerIfNecessary();
        LOG.debug("dispatch({}): sending message {}", topic, message);
        final ProducerRecord<String,String> record = new ProducerRecord<>(topic, module.marshal(message));
        m_producer.send(record);
    }
    
    private void startKafkaConsumerIfNecessary() {
        if (m_producer == null) {
            m_producer = new KafkaProducer<>(m_config);
            LOG.debug("KafkaRemoteMessageDispatcherFactory: started Kafka producer using config {}", m_config);
        }
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        Objects.requireNonNull(m_kafkaAddress);
        Objects.requireNonNull(m_zookeeperHost);
        Objects.requireNonNull(m_zookeeperPort);
        Objects.requireNonNull(m_groupId);

        registerJmxReporter();

        m_config = new Properties();
        m_config.put("bootstrap.servers", m_kafkaAddress);
        m_config.put("acks", "all");
        m_config.put("retries", 0);
        /*
        m_config.put("batch.size", 16384);
        m_config.put("linger.ms", 1);
        m_config.put("buffer.memory", 33554432);
        */
        m_config.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        m_config.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");

        // TODO implement a partitioning scheme
        //m_config.put("partitioner.class", "");

        if (m_producer != null) {
            m_producer.close();
            m_producer = null;
        }

        LOG.debug("KafkaRemoteMessageDispatcherFactory: initialized using config {}", m_config);
    }

    private void registerJmxReporter() {
        if (reporter == null) {
            reporter = JmxReporter.forRegistry(getMetrics())
                    .inDomain(KafkaLocalMessageDispatcherFactory.class.getPackage().getName())
                    .build();
            reporter.start();
        }
    }

    public void unregister() {
        if (reporter != null) {
            reporter.close();
            reporter = null;
        }
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
}
