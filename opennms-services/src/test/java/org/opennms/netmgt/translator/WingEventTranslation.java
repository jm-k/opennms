package org.opennms.netmgt.translator;

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.opennms.core.db.DataSourceFactory;
import org.opennms.core.test.MockLogAppender;
import org.opennms.core.test.db.MockDatabase;
import org.opennms.netmgt.config.EventTranslatorConfigFactory;
import org.opennms.netmgt.dao.mock.EventAnticipator;
import org.opennms.netmgt.dao.mock.MockEventIpcManager;
import org.opennms.netmgt.mock.MockNetwork;
import org.opennms.netmgt.mock.OutageAnticipator;

public class WingEventTranslation {
	private EventTranslator m_translator;
	private String m_passiveStatusConfiguration;
    private MockEventIpcManager m_eventMgr;
    private MockDatabase m_db;
    private MockNetwork m_network;
    private EventAnticipator m_anticipator;
    private OutageAnticipator m_outageAnticipator;
    private EventTranslatorConfigFactory m_config;
	@Before
    public void setUp() throws Exception {
//        MockUtil.println("------------ Begin Test "+getName()+" --------------------------");

    	m_passiveStatusConfiguration = getStandardConfig();
		
		
        MockLogAppender.setupLogging();

        createBasicMockNetwork();
        createMockDb();
        createAnticipators();

        m_eventMgr = new MockEventIpcManager();
        m_eventMgr.setEventWriter(m_db);
        m_eventMgr.setEventAnticipator(m_anticipator);
        m_eventMgr.addEventListener(m_outageAnticipator);
        m_eventMgr.setSynchronous(true);

        InputStream rdr = new ByteArrayInputStream(m_passiveStatusConfiguration.getBytes("UTF-8"));
        m_config = new EventTranslatorConfigFactory(rdr, m_db);
        EventTranslatorConfigFactory.setInstance(m_config);
        
        m_translator = EventTranslator.getInstance();
        m_translator.setEventManager(m_eventMgr);
        m_translator.setConfig(EventTranslatorConfigFactory.getInstance());
        m_translator.setDataSource(m_db);
        
        m_translator.init();
        m_translator.start();
        
    }
	@After
    public void tearDown() throws Exception {
        m_eventMgr.finishProcessingEvents();
        m_translator.stop();
        sleep(200);
        MockLogAppender.assertNoWarningsOrGreater();
        m_db.drop();
//        MockUtil.println("------------ End Test "+getName()+" --------------------------");
//        super.tearDown();
    }
	private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
        }
    }
    
	private String getStandardConfig() throws IOException {
		String filaname = "/wingevents/translator-configuration.xml";
		assertNotNull(getClass().getResource(filaname));
		String realFilenamegetClass = getClass().getResource(filaname).getFile();
		byte[] encoded = Files.readAllBytes(Paths.get(realFilenamegetClass));
		String encoding = "UTF-8";
		return new String(encoded, encoding);
	}
	private void createAnticipators() {
        m_anticipator = new EventAnticipator();
        m_outageAnticipator = new OutageAnticipator(m_db);
		
	}
	private void createMockDb() throws Exception {

        m_db = new MockDatabase();
        m_db.populate(m_network);
        DataSourceFactory.setInstance(m_db);
		
	}
	private void createBasicMockNetwork() {
		//Basic network : controller ....
		//No passive services
	        m_network = new MockNetwork();
	        m_network.setCriticalService("ICMP");
	        m_network.addNode(1, "rfs-backup");
	        m_network.addInterface("172.16.1.54");
	        m_network.addService("ICMP");
	        m_network.addService("WIngAP1");
	        m_network.addService("WingAP2");
	}
	@Test
	public void test() {
		
	}

}
