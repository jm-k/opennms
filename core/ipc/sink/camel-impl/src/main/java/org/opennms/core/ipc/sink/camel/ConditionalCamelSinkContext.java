package org.opennms.core.ipc.sink.camel;

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
@Conditional(ConditionalCamelSinkContext.Condition.class)
@ImportResource("/META-INF/opennms/applicationContext-ipc-sink-server-camel.xml")
public class ConditionalCamelSinkContext {
    private static final Logger LOG = LoggerFactory.getLogger(ConditionalCamelSinkContext.class);
    private static final String DISABLE_CAMEL_SINK_PROP = "org.opennms.core.ipc.sink.camel.disable";

    static class Condition implements ConfigurationCondition {
        @Override
        public ConfigurationPhase getConfigurationPhase() {
            return ConfigurationPhase.PARSE_CONFIGURATION;
        }
        @Override
        public boolean matches(final ConditionContext context, final AnnotatedTypeMetadata metadata) {
            final boolean enabled = !Boolean.getBoolean(DISABLE_CAMEL_SINK_PROP);
            try (MDCCloseable mdc = Logging.withPrefixCloseable(MessageConsumerManager.LOG_PREFIX)) {
                LOG.debug("Enable Camel Sink: {}", enabled);
            }
            return enabled;
        }
   }
}
