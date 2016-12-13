package org.opennms.core.ipc.sink.kafka;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

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

import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.Multimap;

import joptsimple.internal.Objects;
import kafka.consumer.ConsumerConfig;
import kafka.consumer.ConsumerIterator;
import kafka.consumer.KafkaStream;
import kafka.javaapi.consumer.ConsumerConnector;

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

    private final Map<SinkModule<Message>, TopicConsumer<Message>> m_topicConsumersByModule = new HashMap<>();
    private final Multimap<SinkModule<Message>, MessageConsumer<Message>> m_consumersByModule = LinkedListMultimap.create();

    private Properties m_config;

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

    @SuppressWarnings("unchecked")
    @Override
    public <T extends Message> void dispatch(final SinkModule<T> module, final T message) {
        LOG.trace("Dispatching message to module {}: {}", module, message);
        m_consumersByModule.get((SinkModule<Message>) module)
        .forEach(c -> c.handleMessage(message));
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T extends Message> void registerConsumer(final MessageConsumer<T> consumer) throws Exception {
        if (consumer == null) {
            return;
        }

        try (MDCCloseable mdc = Logging.withPrefixCloseable(MessageConsumerManager.LOG_PREFIX)) {
            LOG.info("Registering consumer: {}", consumer);
            final SinkModule<T> module = consumer.getModule();

            if (!m_consumersByModule.containsEntry(module, consumer)) {
                m_consumersByModule.put((SinkModule<Message>)module, (MessageConsumer<Message>)consumer);
            }

            if (!m_topicConsumersByModule.containsKey(module)) {
                final TopicConsumer<T> newConsumer = new TopicConsumer<T>(module, this, m_config);
                m_topicConsumersByModule.put((SinkModule<Message>)module, (TopicConsumer<Message>)newConsumer);
                newConsumer.start();
            }
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T extends Message> void unregisterConsumer(final MessageConsumer<T> consumer) throws Exception {
        LOG.info("Unregistering consumer: {}", consumer);
        final SinkModule<T> module = consumer.getModule();
        m_consumersByModule.remove(module, consumer);
        if (m_consumersByModule.get((SinkModule<Message>)module).isEmpty()) {
            // no more consumers for this topic, close down the threads
            final TopicConsumer<T> topicConsumer = (TopicConsumer<T>) m_topicConsumersByModule.remove(module);
            topicConsumer.stop();
        }
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        Objects.ensureNotNull(m_kafkaAddress);
        Objects.ensureNotNull(m_zookeeperHost);
        Objects.ensureNotNull(m_zookeeperPort);
        Objects.ensureNotNull(m_groupId);

        final Properties props = new Properties();
        //props.put("bootstrap.servers", m_kafkaAddress);
        props.put("zookeeper.connect", m_zookeeperHost + ":" + m_zookeeperPort);
        props.put("group.id", m_groupId);
        props.put("zookeeper.session.timeout.ms", "400");
        props.put("zookeeper.sync.time.ms", "200");
        props.put("auto.commit.interval.ms", "1000");
        /*
        props.put("enable.auto.commit", "true");
        props.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        props.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
         */
        m_config = props;
    }

    private static final class TopicConsumer<T extends Message> {
        private final SinkModule<T> m_module;
        private final ConsumerConnector m_consumerConnector;
        private final MessageConsumerManager m_manager;
        private final List<Future<?>> m_futures = new ArrayList<>();
        private final String m_topic;

        public TopicConsumer(final SinkModule<T> module, final MessageConsumerManager manager, final Properties config) {
            m_module = module;
            m_manager = manager;
            m_consumerConnector = kafka.consumer.Consumer.createJavaConsumerConnector(new ConsumerConfig(config));

            final JmsQueueNameFactory topicFactory = new JmsQueueNameFactory(KafkaSinkConstants.KAFKA_TOPIC_PREFIX, module.getId());
            m_topic = topicFactory.getName();

            LOG.info("TopicConsumer({}): started Kafka consumer with config {}", m_topic, config);
        }

        public void start() {
            final int threads = m_module.getNumConsumerThreads();
            LOG.info("TopicConsumer({}): listening with {} threads.", m_topic, threads);

            final Map<String,Integer> topicCountMap = new HashMap<>();
            topicCountMap.put(m_topic, Integer.valueOf(threads));

            final Map<String, List<KafkaStream<byte[], byte[]>>> consumerMap = m_consumerConnector.createMessageStreams(topicCountMap);
            LOG.debug("TopicConsumer({}): got consumer map: {}", m_topic, consumerMap.keySet());

            final List<KafkaStream<byte[], byte[]>> streams = consumerMap.get(m_topic);
            LOG.debug("TopicConsumer({}): got {} streams.", m_topic, streams.size());

            for (final KafkaStream<byte[], byte[]> stream : streams) {
                LOG.debug("TopicConsumer({}): Scheduling stream.", m_topic);
                m_futures.add(m_pool.submit(new Runnable() {
                    @Override
                    public void run() {
                        try (MDCCloseable mdc = Logging.withPrefixCloseable(MessageConsumerManager.LOG_PREFIX)) {
                            LOG.debug("TopicConsumer({}): starting listening.", m_topic);
                            final ConsumerIterator<byte[], byte[]> it = stream.iterator();
                            while (it.hasNext()) {
                                final String message = new String(it.next().message());
                                LOG.trace("TopicConsumer({}): dispatching {}", m_topic, message);
                                final T messageObject = m_module.unmarshal(message);
                                m_manager.dispatch(m_module, messageObject);
                            }
                            LOG.debug("TopicConsumer({}): finished listening.", m_topic);
                        };
                    }
                }));
            }

            LOG.debug("TopicConsumer({}): finished launching {} streams.", m_topic, m_futures.size());
        }

        public void stop() {
            LOG.info("TopicConsumer({}): stopping listening to {} threads.", m_topic, m_futures.size());
            for (final Future<?> future : m_futures) {
                future.cancel(true);
            }
            m_futures.clear();
        }
    }

}
