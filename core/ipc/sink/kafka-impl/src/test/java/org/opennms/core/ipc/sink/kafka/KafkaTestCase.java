package org.opennms.core.ipc.sink.kafka;

import java.io.File;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.UnknownHostException;
import java.util.Properties;

import org.apache.commons.io.FileUtils;
import org.apache.curator.test.TestingServer;
import org.junit.After;
import org.opennms.core.test.camel.CamelBlueprintTest;

import kafka.server.KafkaConfig;
import kafka.server.KafkaServer;

public abstract class KafkaTestCase extends CamelBlueprintTest {
    private static KafkaConfig kafkaConfig;

    private KafkaServer kafkaServer;
    private TestingServer zkTestServer;
    private int kafkaPort;
    private int zookeeperPort;

    @After
    public void shutDownKafka(){
        if (kafkaServer != null) {
            kafkaServer.shutdown();
        }
    }

    @Override
    public void setUp() throws Exception {
        zookeeperPort = getAvailablePort(2181, 2281);
        kafkaPort = getAvailablePort(9092, 9192);

        // Delete any existing Kafka log directory
        FileUtils.deleteDirectory(new File("target/kafka-log"));

        final String localhost = getLocalhost();
        zkTestServer = new TestingServer(zookeeperPort);
        Properties properties = new Properties();
        properties.put("broker.id", "5001");
        properties.put("enable.zookeeper", "true");
        properties.put("host.name", localhost);
        properties.put("log.dir", "target/kafka-log");
        properties.put("port", String.valueOf(kafkaPort));
        properties.put("zookeeper.connect",zkTestServer.getConnectString());
        
        System.err.println("Kafka server properties: " + properties);
        try {
            kafkaConfig = new KafkaConfig(properties);
            kafkaServer = new KafkaServer(kafkaConfig, null);
            kafkaServer.startup();
        } catch(final Exception e) {
            e.printStackTrace();
        }

        final String kafkaAddress = localhost + ":" + kafkaPort;
        final String zookeeperAddress = localhost + ":" + zookeeperPort;

        System.setProperty("org.opennms.core.ipc.sink.kafka.kafkaAddress", kafkaAddress);
        System.setProperty("org.opennms.core.ipc.sink.kafka.zookeeperHost", localhost);
        System.setProperty("org.opennms.core.ipc.sink.kafka.zookeeperPort", String.valueOf(zookeeperPort));

        System.err.println("Kafka Address: " + kafkaAddress);
        System.err.println("Zookeeper Address: " + zookeeperAddress);

        super.setUp();
    }

    @Override
    protected String setConfigAdminInitialConfiguration(final Properties props) {
        final String localhost = getLocalhost();

        props.put("kafkaAddress", localhost + ":" + kafkaPort);
        props.put("zookeeperHost", localhost);
        props.put("zookeeperPort", String.valueOf(zookeeperPort));

        return "org.opennms.core.ipc.sink.kafka";
    }

    private static int getAvailablePort(final int min, final int max) {
        for (int i = min; i <= max; i++) {
            try (final ServerSocket socket = new ServerSocket(i)) {
                return socket.getLocalPort();
            } catch (final Throwable e) {}
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
}
