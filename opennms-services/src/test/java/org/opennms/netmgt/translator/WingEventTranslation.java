package org.opennms.netmgt.translator;

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import org.junit.Before;
import org.junit.Test;
import org.opennms.core.test.MockLogAppender;
import org.opennms.core.test.db.MockDatabase;
import org.opennms.netmgt.config.EventTranslatorConfigFactory;
import org.opennms.netmgt.dao.mock.EventAnticipator;
import org.opennms.netmgt.dao.mock.MockEventIpcManager;
import org.opennms.netmgt.mock.MockNetwork;
import org.opennms.netmgt.mock.OutageAnticipator;

public class WingEventTranslation {
	private EventTranslator m_translator;
    private String m_passiveStatusConfiguration = getStandardConfig();
    private MockEventIpcManager m_eventMgr;
    private MockDatabase m_db;
    private MockNetwork m_network;
    private EventAnticipator m_anticipator;
    private OutageAnticipator m_outageAnticipator;
    private EventTranslatorConfigFactory m_config;
	@Before
    public void setUp() throws Exception {
//        MockUtil.println("------------ Begin Test "+getName()+" --------------------------");
        MockLogAppender.setupLogging();

        createMockNetwork();
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

	private String getStandardConfig() {
		return null;
	}
	private void createAnticipators() {
		// TODO Auto-generated method stub
		
	}
	private void createMockDb() {
		// TODO Auto-generated method stub
		
	}
	private void createMockNetwork() {
		// TODO Auto-generated method stub
		
	}
	@Test
	public void test() {
		
	}

}
