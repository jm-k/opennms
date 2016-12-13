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

import static com.jayway.awaitility.Awaitility.await;
import static java.util.concurrent.TimeUnit.MINUTES;
import static org.hamcrest.Matchers.equalTo;

import java.util.Dictionary;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.util.KeyValueHolder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.opennms.core.ipc.sink.api.MessageConsumer;
import org.opennms.core.ipc.sink.api.MessageProducer;
import org.opennms.core.ipc.sink.api.MessageProducerFactory;
import org.opennms.core.ipc.sink.api.SinkModule;
import org.opennms.core.ipc.sink.kafka.HeartbeatSinkPerfIT.HeartbeatGenerator;
import org.opennms.core.ipc.sink.kafka.heartbeat.Heartbeat;
import org.opennms.core.ipc.sink.kafka.heartbeat.HeartbeatModule;
import org.opennms.core.ipc.sink.test.ThreadLockingMessageConsumer;
import org.opennms.core.test.OpenNMSJUnit4ClassRunner;
import org.opennms.core.test.camel.CamelBlueprintTest;
import org.opennms.minion.core.api.MinionIdentity;
import org.opennms.test.JUnitConfigurationEnvironment;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;

@RunWith(OpenNMSJUnit4ClassRunner.class)
@ContextConfiguration(locations={
        "classpath:/META-INF/opennms/applicationContext-soa.xml",
        "classpath:/META-INF/opennms/applicationContext-mockDao.xml",
        "classpath:/META-INF/opennms/applicationContext-proxy-snmp.xml",
        "classpath:/META-INF/opennms/applicationContext-ipc-sink-server-kafka.xml"
})
@JUnitConfigurationEnvironment
public class HeartbeatSinkBlueprintIT extends CamelBlueprintTest {

    private static final String REMOTE_LOCATION_NAME = "remote";

    @Autowired
    private MessageProducerFactory localMessageProducerFactory;

    @Autowired
    private KafkaMessageConsumerManager consumerManager;

    @SuppressWarnings( "rawtypes" )
    @Override
    protected void addServicesOnStartup(Map<String, KeyValueHolder<Object, Dictionary>> services) {
        services.put(MinionIdentity.class.getName(),
                new KeyValueHolder<Object, Dictionary>(new MinionIdentity() {
                    @Override
                    public String getId() {
                        return "0";
                    }
                    @Override
                    public String getLocation() {
                        return REMOTE_LOCATION_NAME;
                    }
                }, new Properties()));
    }

    @Override
    protected String getBlueprintDescriptor() {
        return "classpath:/OSGI-INF/blueprint/blueprint-ipc-client.xml";
    }

    @Override
    public boolean isCreateCamelContextPerClass() {
        return true;
    }

    @Override
    protected Long getCamelContextCreationTimeout() {
        return 5L;
    }

    @Override
    public void doPreSetup() throws Exception {
        super.doPreSetup();
        
        consumerManager.setKafkaAddress(System.getProperty("org.opennms.core.ipc.sink.kafka.kafkaAddress"));
        consumerManager.setZookeeperHost(System.getProperty("org.opennms.core.ipc.sink.kafka.zookeeperHost"));
        consumerManager.setZookeeperPort(Integer.getInteger("org.opennms.core.ipc.sink.kafka.zookeeperPort"));
        consumerManager.afterPropertiesSet();
    }

    @Test(timeout=60000)
    public void canProduceAndConsumerMessages() throws Exception {
        System.err.println("starting");
        HeartbeatModule module = new HeartbeatModule();

        AtomicInteger heartbeatCount = new AtomicInteger();
        consumerManager.registerConsumer(new MessageConsumer<Heartbeat>() {
            @Override
            public SinkModule<Heartbeat> getModule() {
                return module;
            }

            @Override
            public void handleMessage(Heartbeat heartbeat) {
                heartbeatCount.incrementAndGet();
            }
        });

        MessageProducer<Heartbeat> localProducer = localMessageProducerFactory.getProducer(module);
        localProducer.send(new Heartbeat());
        await().atMost(1, MINUTES).until(() -> heartbeatCount.get(), equalTo(1));

        MessageProducerFactory remoteMessageProducerFactory = context.getRegistry().lookupByNameAndType("kafkaRemoteMessageProducerFactory", MessageProducerFactory.class);
        MessageProducer<Heartbeat> remoteProducer = remoteMessageProducerFactory.getProducer(module);
        remoteProducer.send(new Heartbeat());
        await().atMost(1, MINUTES).until(() -> heartbeatCount.get(), equalTo(2));
    }

    @Test(timeout=60000)
    public void canConsumeMessagesInParallel() throws Exception {
        final int NUM_CONSUMER_THREADS = 7;

        final HeartbeatModule parallelHeartbeatModule = new HeartbeatModule() {
            @Override
            public int getNumConsumerThreads() {
                return NUM_CONSUMER_THREADS;
            }
        };

        final ThreadLockingMessageConsumer<Heartbeat> consumer = new ThreadLockingMessageConsumer<>(parallelHeartbeatModule);

        final CompletableFuture<Integer> future = consumer.waitForThreads(NUM_CONSUMER_THREADS);
        consumerManager.registerConsumer(consumer);

        final MessageProducerFactory remoteMessageProducerFactory = context.getRegistry().lookupByNameAndType("kafkaRemoteMessageProducerFactory", MessageProducerFactory.class);
        final MessageProducer<Heartbeat> producer = remoteMessageProducerFactory.getProducer(HeartbeatModule.INSTANCE);

        final HeartbeatGenerator generator = new HeartbeatGenerator(producer, 100.0);
        generator.start();

        // Wait until we have NUM_CONSUMER_THREADS locked
        future.get();

        // Take a snooze
        Thread.sleep(TimeUnit.SECONDS.toMillis(5));

        System.err.println("extra threads: " + consumer.getNumExtraThreadsWaiting());

        // Verify that there aren't more than NUM_CONSUMER_THREADS waiting
        assertEquals(0, consumer.getNumExtraThreadsWaiting());

        generator.stop();
    }

}
