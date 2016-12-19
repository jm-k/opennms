package org.opennms.core.ipc.sink.kafka;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.io.FileUtils;
import org.apache.zookeeper.server.NIOServerCnxnFactory;
import org.apache.zookeeper.server.ServerCnxnFactory;
import org.apache.zookeeper.server.ServerConfig;
import org.apache.zookeeper.server.ZKDatabase;
import org.apache.zookeeper.server.ZooKeeperServer;
import org.apache.zookeeper.server.persistence.FileTxnSnapLog;
import org.apache.zookeeper.server.quorum.QuorumPeerConfig;
import org.junit.After;
import org.opennms.core.test.camel.CamelBlueprintTest;

import com.google.common.io.Files;

import kafka.metrics.KafkaMetricsReporter;
import kafka.server.KafkaConfig;
import kafka.server.KafkaServer;
import scala.Option;
import scala.collection.mutable.Buffer;

public abstract class KafkaTestCase extends CamelBlueprintTest {
    private static KafkaConfig kafkaConfig;

    private KafkaServer kafkaServer;
    private ZooKeeperServer zkServer;

    private static AtomicInteger kafkaPort = new AtomicInteger(9092);
    private static AtomicInteger zookeeperPort = new AtomicInteger(2181);

    @After
    public void shutDownKafka(){
        if (kafkaServer != null) {
            kafkaServer.shutdown();
        }
        if (zkServer != null) {
            zkServer.shutdown();
        }
    }

    @Override
    public void setUp() throws Exception {
        getAvailablePort(zookeeperPort, 2281);
        getAvailablePort(kafkaPort, 9192);

        // Delete any existing Kafka log directory
        FileUtils.deleteDirectory(new File("target/kafka-log"));

        final String localhost = getLocalhost();

        final QuorumPeerConfig config = new QuorumPeerConfig() {
            public String getDataDir() {
                if (dataDir == null) {
                    dataDir = Files.createTempDir().getAbsolutePath();
                }
                return super.getDataDir();
            }
            public String getDataLogDir() {
                if (dataLogDir == null) {
                    dataLogDir = new File("target/kafka-log").getAbsolutePath();
                }
                return super.getDataLogDir();
            }
        };
        zkServer = TestZooKeeperServer.runFromConfig(config);
        final ServerCnxnFactory factory = NIOServerCnxnFactory.createFactory(zookeeperPort.get(), 100);
        factory.startup(zkServer);

        Thread.sleep(3000);

        final Properties properties = new Properties();
        properties.put("broker.id", "1");
        properties.put("auto.create.topics.enable", "true");
        properties.put("enable.zookeeper", "true");
        properties.put("host.name", localhost);
        properties.put("log.dir", "target/kafka-log");
        properties.put("port", String.valueOf(kafkaPort.get()));
        properties.put("zookeeper.connect", localhost + ":" + zookeeperPort.get());
        
        System.err.println("Kafka server properties: " + properties);
        try {
            kafkaConfig = new KafkaConfig(properties);

            final List<KafkaMetricsReporter> kmrList = new ArrayList<>();
            final Buffer<KafkaMetricsReporter> metricsList = scala.collection.JavaConversions.asScalaBuffer(kmrList);
            kafkaServer = new KafkaServer(kafkaConfig, new SystemTime(), Option.<String>empty(), metricsList);
            kafkaServer.startup();
            Thread.sleep(5000);
        } catch(final Exception e) {
            e.printStackTrace();
        }

        final String kafkaAddress = localhost + ":" + kafkaPort.get();
        final String zookeeperAddress = localhost + ":" + zookeeperPort.get();

        System.setProperty("org.opennms.core.ipc.sink.kafka.kafkaAddress", kafkaAddress);
        System.setProperty("org.opennms.core.ipc.sink.kafka.zookeeperHost", localhost);
        System.setProperty("org.opennms.core.ipc.sink.kafka.zookeeperPort", String.valueOf(zookeeperPort.get()));

        System.err.println("Kafka Address: " + kafkaAddress);
        System.err.println("Zookeeper Address: " + zookeeperAddress);

        super.setUp();
    }

    @Override
    protected String setConfigAdminInitialConfiguration(final Properties props) {
        final String localhost = getLocalhost();

        props.put("kafkaAddress", localhost + ":" + kafkaPort.get());
        props.put("zookeeperHost", localhost);
        props.put("zookeeperPort", String.valueOf(zookeeperPort.get()));

        return "org.opennms.core.ipc.sink.kafka";
    }

    private static int getAvailablePort(final AtomicInteger current, final int max) {
        while (current.get() < max) {
            try (final ServerSocket socket = new ServerSocket(current.get())) {
                return socket.getLocalPort();
            } catch (final Throwable e) {}
            current.incrementAndGet();
        }
        throw new IllegalStateException("Can't find an available network port");
    }
    
    private static String getLocalhost() {
        String address = "localhost";
        try {
            // This is a workaround for people using OS X Lion.  On Lion when a process tries to connect to a link-local
            // address it takes 5 seconds to establish the connection for some reason.  So instead of using 'localhost'
            // which could return the link-local address randomly, we'll manually resolve it and look for an address to
            // return that isn't link-local.  If for some reason we can't find an address that isn't link-local then
            // we'll fall back to the default lof just looking up 'localhost'.
            for (InetAddress a : InetAddress.getAllByName("localhost")) {
                if (!a.isLinkLocalAddress()) {
                    address = a.getHostAddress();
                    break;
                }
            }
        }
        catch (UnknownHostException e) {
            // Something went wrong, just default to the existing approach of using 'localhost'.
        }
        return address;
    }

    private static class TestZooKeeperServer extends ZooKeeperServer {
        public TestZooKeeperServer(final FileTxnSnapLog snapLog, final ServerConfig config) throws IOException {
            super(snapLog, config.getTickTime(), config.getMinSessionTimeout(), config.getMaxSessionTimeout(), null, new ZKDatabase(snapLog));
        }

        public static TestZooKeeperServer runFromConfig(final QuorumPeerConfig config) throws IOException {
            final ServerConfig serverConfig = new ServerConfig();
            serverConfig.readFrom(config);
            final FileTxnSnapLog snapLog = new FileTxnSnapLog(new File(config.getDataLogDir()), new File(config.getDataDir()));
            return new TestZooKeeperServer(snapLog, serverConfig);
        }

        protected void registerJMX() {
            // NOP
        }

        @Override
        protected void unregisterJMX() {
            // NOP
        }
    }
}
