package org.opennms.core.ipc.sink.kafka;

import org.opennms.core.ipc.sink.api.MessageConsumerManager;
import org.opennms.core.logging.Logging;
import org.opennms.core.logging.Logging.MDCCloseable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ConfigurationCondition;
import org.springframework.context.annotation.ImportResource;
import org.springframework.core.type.AnnotatedTypeMetadata;

@Configuration
@Conditional(ConditionalKafkaSinkContext.Condition.class)
@ImportResource("/META-INF/opennms/applicationContext-ipc-sink-server-kafka.xml")
public class ConditionalKafkaSinkContext {
    private static final Logger LOG = LoggerFactory.getLogger(ConditionalKafkaSinkContext.class);
    private static final String ENABLE_KAFKA_SINK_PROP = "org.opennms.core.ipc.sink.kafka.enable";

    static class Condition implements ConfigurationCondition {
        @Override
        public ConfigurationPhase getConfigurationPhase() {
            return ConfigurationPhase.PARSE_CONFIGURATION;
        }
        @Override
        public boolean matches(final ConditionContext context, final AnnotatedTypeMetadata metadata) {
            final Boolean enabled = Boolean.getBoolean(ENABLE_KAFKA_SINK_PROP);
            try (MDCCloseable mdc = Logging.withPrefixCloseable(MessageConsumerManager.LOG_PREFIX)) {
                LOG.debug("Enable Kafka Sink: {}", enabled);
            }
            return enabled;
        }
   }
}
