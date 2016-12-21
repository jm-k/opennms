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

import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import org.I0Itec.zkclient.ZkClient;
import org.I0Itec.zkclient.ZkConnection;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.opennms.core.camel.JmsQueueNameFactory;
import org.opennms.core.ipc.sink.api.Message;
import org.opennms.core.ipc.sink.api.MessageConsumer;
import org.opennms.core.ipc.sink.api.MessageConsumerManager;
import org.opennms.core.ipc.sink.api.SinkModule;
import org.opennms.core.logging.Logging;
import org.opennms.core.logging.Logging.MDCCloseable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;

import com.google.common.collect.MapMaker;

import kafka.admin.AdminUtils;
import kafka.admin.RackAwareMode;
import kafka.utils.ZKStringSerializer$;
import kafka.utils.ZkUtils;

public class KafkaMessageConsumerManager implements MessageConsumerManager, InitializingBean {
    private static final Logger LOG = LoggerFactory.getLogger(KafkaMessageConsumerManager.class);

    private static final ExecutorService m_pool = Executors.newCachedThreadPool();

    @Value("${org.opennms.core.ipc.sink.kafka.kafkaAddress}")
    private String m_kafkaAddress;

    @Value("${org.opennms.core.ipc.sink.kafka.zookeeperHost}")
    private String m_zookeeperHost;

    @Value("${org.opennms.core.ipc.sink.kafka.zookeeperPort}")
    private Integer m_zookeeperPort;

    @Value("${org.opennms.core.ipc.sink.kafka.groupId}")
    private String m_groupId;

    private final ConcurrentMap<SinkModule<Message,Message>,String> m_topicsByModule = new MapMaker().concurrencyLevel(2).makeMap();
    private final ConcurrentMap<String,SinkModule<Message,Message>> m_modulesByTopic = new MapMaker().concurrencyLevel(2).makeMap();
    private final ConcurrentMap<SinkModule<Message,Message>,KafkaTopicProcessor<Message,Message>> m_topicProcessorsByModule = new MapMaker().concurrencyLevel(2).makeMap();

    private Properties m_config;
    private ConsumerReader m_consumerReader;

    private ZkUtils m_zkUtils;

    public KafkaMessageConsumerManager() {
    }

    public Properties getConfig() {
        return m_config;
    }

    public void setKafkaAddress(final String kafkaAddress) {
        m_kafkaAddress = kafkaAddress;
    }

    public void setZookeeperHost(final String zookeeperHost) {
        m_zookeeperHost = zookeeperHost;
    }

    public void setZookeeperPort(final Integer zookeeperPort) {
        m_zookeeperPort = zookeeperPort;
    }

    public void setGroupId(final String groupId) {
        m_groupId = groupId;
    }

    @Override
    public <S extends Message, T extends Message> void dispatch(final SinkModule<S,T> module, final T message) {
        if (LOG.isTraceEnabled()) {
            try (MDCCloseable mdc = Logging.withPrefixCloseable(MessageConsumerManager.LOG_PREFIX)) {
                final String topic = m_topicsByModule.get(module);
                LOG.trace("dispatch({}): sending message {}", topic, message);
            }
        }
        m_topicProcessorsByModule.get(module).dispatch(message);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <S extends Message, T extends Message> void registerConsumer(final MessageConsumer<S,T> consumer) throws Exception {
        if (consumer == null) {
            return;
        }

        try (MDCCloseable mdc = Logging.withPrefixCloseable(MessageConsumerManager.LOG_PREFIX)) {
            LOG.info("Registering consumer: {}", consumer);

            final SinkModule<S,T> module = consumer.getModule();

            final JmsQueueNameFactory topicFactory = new JmsQueueNameFactory(KafkaSinkConstants.KAFKA_TOPIC_PREFIX, module.getId());
            final String topic = topicFactory.getName();

            m_topicsByModule.put((SinkModule<Message,Message>) module, topic);
            m_modulesByTopic.put(topic, (SinkModule<Message,Message>) module);

            if (!m_topicProcessorsByModule.containsKey(module)) {
                final KafkaTopicProcessor<Message,Message> newTopicProcessor = new KafkaTopicProcessor<>((SinkModule<Message,Message>) module);
                for (int i=0; i < module.getNumConsumerThreads(); i++) {
                    m_pool.submit(newTopicProcessor);
                }
                m_topicProcessorsByModule.put((SinkModule<Message,Message>) module, newTopicProcessor);
            }
            KafkaTopicProcessor<Message,Message> tp = m_topicProcessorsByModule.get((SinkModule<Message,Message>) module);
            tp.addConsumer((MessageConsumer<Message,Message>) consumer);

            startKafkaConsumerReaderIfNecessary();
            m_consumerReader.subscribe(m_topicsByModule.values());
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public <S extends Message, T extends Message> void unregisterConsumer(final MessageConsumer<S,T> consumer) throws Exception {
        try(MDCCloseable mdc = Logging.withPrefixCloseable(MessageConsumerManager.LOG_PREFIX)) {
            LOG.info("Unregistering consumer: {}", consumer);

            final SinkModule<S,T> module = consumer.getModule();

            final JmsQueueNameFactory topicFactory = new JmsQueueNameFactory(KafkaSinkConstants.KAFKA_TOPIC_PREFIX, module.getId());
            final String topic = topicFactory.getName();

            final KafkaTopicProcessor<Message,Message> tp = m_topicProcessorsByModule.get(module);
            if (tp != null) {
                tp.removeConsumer((MessageConsumer<Message,Message>) consumer);

                if (tp.size() == 0) {
                    m_modulesByTopic.remove(topic);
                    m_topicsByModule.remove(module);
                }
            }

            if (m_modulesByTopic.size() == 0) {
                stopKafkaConsumerReader();
            } else if (m_consumerReader != null) {
                m_consumerReader.subscribe(m_modulesByTopic.keySet());
            } else {
                LOG.warn("Topics are still subscribed, but we have no consumer reader!  This should not happen.");
            }
        }
    }

    void unregisterAllConsumers() throws Exception {
        for (final KafkaTopicProcessor<Message,Message> tp : m_topicProcessorsByModule.values()) {
            final Collection<MessageConsumer<Message,Message>> consumers = tp.getConsumers();
            for (final MessageConsumer<Message,Message> consumer : consumers) {
                unregisterConsumer(consumer);
            }
        }
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        try (MDCCloseable mdc = Logging.withPrefixCloseable(MessageConsumerManager.LOG_PREFIX)) {
            Objects.requireNonNull(m_kafkaAddress);
            Objects.requireNonNull(m_zookeeperHost);
            Objects.requireNonNull(m_zookeeperPort);
            Objects.requireNonNull(m_groupId);
    
            final Properties props = new Properties();
            props.put("bootstrap.servers", m_kafkaAddress);
            props.put("group.id", m_groupId);
            props.put("enable.auto.commit", "true");
            props.put("auto.commit.interval.ms", "1000");
            props.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
            props.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
            m_config = props;
            LOG.debug("KafkaMessageConsumerManager: initialized with kafkaAddress={}, zookeeperAddress={}, groupId={}", m_kafkaAddress, m_zookeeperHost + ":" + m_zookeeperPort, m_groupId);
        }
    }

    protected void startKafkaConsumerReaderIfNecessary() {
        if (m_consumerReader == null) {
            try (MDCCloseable mdc = Logging.withPrefixCloseable(MessageConsumerManager.LOG_PREFIX)) {
                final String zookeeperConnection = m_zookeeperHost + ":" + m_zookeeperPort;
                LOG.debug("Starting Kafka Consumer Reader: kafkaAddress={}, zookeeperAddress={}, groupId={}", m_kafkaAddress, zookeeperConnection, m_groupId);
                final KafkaConsumer<String,String> consumer = new KafkaConsumer<String,String>(m_config);
                final ZkClient client = new ZkClient(zookeeperConnection, 30000, 30000, ZKStringSerializer$.MODULE$);
                m_zkUtils = new ZkUtils(client, new ZkConnection(zookeeperConnection), false);
                m_consumerReader = new ConsumerReader(consumer, m_zkUtils);
                m_pool.submit(m_consumerReader);
            }
        }
    }

    protected void stopKafkaConsumerReader() {
        try (MDCCloseable mdc = Logging.withPrefixCloseable(MessageConsumerManager.LOG_PREFIX)) {
            LOG.debug("Stopping Kafka Consumer Reader.");
            if (m_consumerReader != null) {
                m_consumerReader.unsubscribe();
                m_consumerReader.stop();
                m_consumerReader = null;
            }
            if (m_zkUtils != null) {
                m_zkUtils.close();
                m_zkUtils = null;
            }
        }
    }

    protected KafkaTopicProcessor<Message,Message> getTopicProcessor(final String topic) {
        final SinkModule<Message,Message> module = m_modulesByTopic.get(topic);
        return m_topicProcessorsByModule.get(module);
    }

    private final class ConsumerReader implements Runnable {
        private KafkaConsumer<String, String> m_kafkaConsumer;
        private ZkUtils m_zkUtils;
        private CopyOnWriteArraySet<String> m_topics = new CopyOnWriteArraySet<>();
        private AtomicBoolean m_modified = new AtomicBoolean(false);
        private AtomicBoolean m_running = new AtomicBoolean(true);

        public ConsumerReader(final KafkaConsumer<String, String> consumer, final ZkUtils zkUtils) {
            m_kafkaConsumer = consumer;
            m_zkUtils = zkUtils;
        }

        public void subscribe(final Collection<String> topics) {
            if (topics.size() > 0) {
                m_topics.addAll(topics);
                m_topics.retainAll(topics);
            } else {
                m_topics.clear();
            }
            m_modified.set(true);
        }

        public void unsubscribe() {
            m_topics.clear();
            m_modified.set(true);
        }

        public void stop() {
            m_running.set(false);
        }

        @Override
        public void run() {
            try (MDCCloseable mdc = Logging.withPrefixCloseable(MessageConsumerManager.LOG_PREFIX)) {
                LOG.debug("ConsumerReader: Starting consuming from Kafka.");
                while (m_running.get()) {
                    //LOG.debug("ConsumerReader: Polling for new records.");
                    final boolean modified = m_modified.getAndSet(false);
                    if (modified) {
                        final Set<String> topics = new HashSet<>(m_topics);
                        LOG.debug("ConsumerReader: Topic list has been modified.  Updating subscriptions: {}", topics);
                        if (topics.isEmpty()) {
                            m_kafkaConsumer.unsubscribe();
                        } else {
                            for (final String topic : topics) {
                                if (!AdminUtils.topicExists(m_zkUtils, topic)) {
                                    LOG.debug("ConsumerReader: Topic ({}) does not exist.  Creating.", topic);
                                    AdminUtils.createTopic(m_zkUtils, topic, 1, 1, new Properties(), RackAwareMode.Disabled$.MODULE$);
                                    LOG.debug("ConsumerReader: Finished creating topic {}", topic);
                                }
                            }
                            m_kafkaConsumer.subscribe(topics);
                        }
                    }
                    final ConsumerRecords<String, String> records = m_kafkaConsumer.poll(100);
                    LOG.debug("ConsumerReader: Got {} records.", records.count());
                    for (final ConsumerRecord<String, String> record : records) {
                        final String topic = record.topic();
                        final KafkaTopicProcessor<Message,Message> tp = getTopicProcessor(topic);
                        tp.queue(record);
                    }
                }
                LOG.debug("ConsumerReader: Closing Kafka consumer.");
                m_kafkaConsumer.close();
            }
        }
    }

    private static final class KafkaTopicProcessor<S extends Message, T extends Message> implements Runnable {
        private final SinkModule<S,T> m_module;
        private final String m_topic;

        private final Set<MessageConsumer<S,T>> m_consumers = new CopyOnWriteArraySet<>();
        private final int m_queueSize = 100; // TODO make configurable (in SinkModule?)
        private final LinkedBlockingQueue<ConsumerRecord<String, String>> m_queue = new LinkedBlockingQueue<>(m_queueSize );

        public KafkaTopicProcessor(final SinkModule<S,T> module) {
            try (MDCCloseable mdc = Logging.withPrefixCloseable(MessageConsumerManager.LOG_PREFIX)) {
                m_module = module;
                final JmsQueueNameFactory topicFactory = new JmsQueueNameFactory(KafkaSinkConstants.KAFKA_TOPIC_PREFIX, module.getId());
                m_topic = topicFactory.getName();
                LOG.info("KafkaTopicProcessor({}): Initialized.", m_topic);
            }
        }

        public Collection<MessageConsumer<S,T>> getConsumers() {
            return m_consumers;
        }

        public int size() {
            return m_consumers.size();
        }

        public void addConsumer(final MessageConsumer<S,T> consumer) {
            try (MDCCloseable mdc = Logging.withPrefixCloseable(MessageConsumerManager.LOG_PREFIX)) {
                LOG.debug("KafkaTopicProcessor({}): Adding consumer: {}", m_topic, consumer);
                m_consumers.add(consumer);
            }
        }

        public void removeConsumer(final MessageConsumer<S,T> consumer) {
            try (MDCCloseable mdc = Logging.withPrefixCloseable(MessageConsumerManager.LOG_PREFIX)) {
                LOG.debug("KafkaTopicProcessor({}): Removing consumer: {}", m_topic, consumer);
                m_consumers.remove(consumer);
            }
        }

        public void queue(final ConsumerRecord<String,String> record) {
            try (MDCCloseable mdc = Logging.withPrefixCloseable(MessageConsumerManager.LOG_PREFIX)) {
                LOG.debug("KafkaTopicProcessor({}): Queueing record: {}", m_topic, record);
                try {
                    m_queue.put(record);
                } catch (final InterruptedException e) {
                    LOG.debug("Interrupted while queueing {}", record.value(), e);
                }
            }
        }

        public void dispatch(final T message) {
            try (MDCCloseable mdc = Logging.withPrefixCloseable(MessageConsumerManager.LOG_PREFIX)) {
                LOG.debug("KafkaTopicProcessor({}): Dispatching message to {} consumers: {}", m_topic, m_consumers.size(), message);
                m_consumers.forEach(c -> c.handleMessage(message));
            }
        }

        @Override
        public void run() {
            try (MDCCloseable mdc = Logging.withPrefixCloseable(MessageConsumerManager.LOG_PREFIX)) {
                LOG.info("KafkaTopicProcessor({}): Starting queue processing.", m_topic);
                while (true) {
                    try {
                        final ConsumerRecord<String, String> record = m_queue.take();
                        LOG.info("KafkaTopicProcessor({}): Got record: {}", m_topic, record);
                        if (!m_topic.equals(record.topic())) {
                            LOG.warn("KafkaTopicProcessor({}): Record topic ({}) does not match thread processor topic. Skipping.", m_topic, record.topic());
                        } else {
                            dispatch(m_module.unmarshal(record.value()));
                        }
                    } catch (final InterruptedException e) {
                        LOG.warn("KafkaTopicProcessor({}): Interrupted.", m_topic, e);
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }
    }
}
