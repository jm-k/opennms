package org.opennms.netmgt.translator;

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.exolab.castor.xml.MarshalException;
import org.exolab.castor.xml.ValidationException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.opennms.core.db.DataSourceFactory;
import org.opennms.core.test.MockLogAppender;
import org.opennms.core.test.db.MockDatabase;
import org.opennms.core.utils.InetAddressUtils;
import org.opennms.netmgt.config.EventTranslatorConfigFactory;
import org.opennms.netmgt.config.translator.Assignment;
import org.opennms.netmgt.config.translator.EventTranslationSpec;
import org.opennms.netmgt.config.translator.Mapping;
import org.opennms.netmgt.config.translator.Mappings;
import org.opennms.netmgt.config.translator.Value;
import org.opennms.netmgt.dao.mock.EventAnticipator;
import org.opennms.netmgt.dao.mock.MockEventIpcManager;
import org.opennms.netmgt.events.api.EventConstants;
import org.opennms.netmgt.mock.MockEventUtil;
import org.opennms.netmgt.mock.MockNetwork;
import org.opennms.netmgt.mock.OutageAnticipator;
import org.opennms.netmgt.model.events.EventBuilder;
import org.opennms.netmgt.scriptd.helper.SnmpTrapHelper;
import org.opennms.netmgt.scriptd.helper.SnmpTrapHelperException;
import org.opennms.netmgt.snmp.SnmpInstId;
import org.opennms.netmgt.snmp.SnmpObjId;
import org.opennms.netmgt.snmp.SnmpTrapBuilder;
import org.opennms.netmgt.snmp.SnmpValue;
import org.opennms.netmgt.snmp.TrapIdentity;
import org.opennms.netmgt.snmp.mock.MockSnmpValue;
import org.opennms.netmgt.snmp.snmp4j.Snmp4JV2TrapBuilder;
import org.opennms.netmgt.trapd.EventCreator;
import org.opennms.netmgt.xml.event.Event;
import org.opennms.netmgt.xml.event.Logmsg;
import org.opennms.netmgt.xml.event.Parm;
import org.opennms.protocols.snmp.SnmpObjectId;
import org.snmp4j.smi.Integer32;
import static org.opennms.core.utils.InetAddressUtils.str;

public class WingEventTranslation {
	private EventTranslator m_translator;
	private String m_passiveStatusConfiguration;
    private MockEventIpcManager m_eventMgr;
    private MockDatabase m_db;
    private MockNetwork m_network;
    private EventAnticipator m_anticipator;
    private OutageAnticipator m_outageAnticipator;
    private EventTranslatorConfigFactory m_config;
    /*
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
    }*/
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
	public void testClientInfo () {
		String is="Client '54-35-30-50-64-EF' IP address '10.155.5.38', bssid '84-24-8D-EA-1A-02' of radio 'apifms-DEC3E4:R1' signal-strength -69dBm";
		//String res = getRegexp(is);
		Pattern p = Pattern.compile("Client '(.*)' IP address '(.*)', bssid '(.*)' of radio '(.*)' signal-strength (.*)dBm");
		Matcher resm = p.matcher(is);
		assertTrue(resm.matches());
		assertEquals(5,resm.groupCount());
		assertEquals("54-35-30-50-64-EF",resm.group(1));
		assertEquals("10.155.5.38",resm.group(2));
		assertEquals("84-24-8D-EA-1A-02",resm.group(3));
		assertEquals("apifms-DEC3E4:R1",resm.group(4));
		assertEquals("-69",resm.group(5));
	}

	@Test 
	public void testAssociate () {
		String is="Client '20-62-74-D8-F4-B4' associated to wlan 'WlanPermanent' ssid 'permanent' on radio 'ap300-E5350C:R1'";
		//String res = getRegexp(is);
		Pattern p = Pattern.compile("Client '(.*)' associated to wlan '(.*)' ssid '(.*)' on radio '(.*):(.*)'");
		//assertEquals("Client '(.*)' associated to wlan '(.*)' ssid '(.*)' on radio '(.*)'",res);
		Matcher resm = p.matcher(is);
		assertTrue(resm.matches());
		assertEquals(5,resm.groupCount());
		assertEquals("20-62-74-D8-F4-B4",resm.group(1));
		assertEquals("WlanPermanent",resm.group(2));
		assertEquals("permanent",resm.group(3));
		assertEquals("ap300-E5350C",resm.group(4));
		assertEquals("R1",resm.group(5));
	}
	@Test 
	public void testConfigCommit () {
		String is="Configuration commit by user 'admin' (mapsh) from '172.16.2.57'";
		String res = getRegexp(is);
		System.out.println(res);
		assertEquals("Configuration commit by user '(.*)' (mapsh) from '(.*)'",res);
	}
	@Test 
	public void testDisaciasated1 () {
		String is="Client '9C-65-B0-24-32-80' disassociated from wlan 'WlanEduspot' radio 'ap300-24583E:R1': inactivity timer expired (reason code:4)";
		String res = getRegexp(is);
		System.out.println(res);
		assertEquals("Client '(.*)' disassociated from wlan '(.*)' radio '(.*)': inactivity timer expired (reason code:4)",res);
	}
	@Test
	public void testBuildTranslation () throws MarshalException, ValidationException {
		EventTranslationSpec spec = new EventTranslationSpec();
		spec.setUei("uei.opennms.org/traps/WING-MIB/wingTrapDot11ClientAssociated");
		Mapping mapping = new Mapping();
		Assignment ueiMap = new Assignment();
		ueiMap.setType("field");
		ueiMap.setName("uei");
		
		 Value ueiValue = new Value();
		 ueiValue.setType("constant");
		 ueiValue.setResult("uei.univ.fr/wifi/client/MUInfo/SignalStrength");
		
		ueiMap.setValue(ueiValue);
		
		mapping.getAssignmentCollection().add(ueiMap);
		Mappings mappings = new Mappings();
		mappings.addMapping(mapping);
		spec.setMappings(mappings);
		StringWriter s = new StringWriter();
		spec.marshal(s);
		System.out.println(s.toString());
		
	}
	
	
	
	
	
	private String getRegexp(String is) {
		Pattern  pattern=  Pattern.compile("(.*?)('.*?')(.*)");
		String res  = "";
		while(true){
			Matcher matcher = pattern.matcher(is);
			if (!matcher.matches()) break;
			//assertTrue(matcher.matches());
			assertEquals(3,matcher.groupCount());
			res = res+is.substring(matcher.start(1),matcher.end(1));
			//assertEquals("Client ",res);
			//assertEquals("54-35-30-50-64-EF",matcher.group(2));
			res=res+"'(.*)'";
			is=is.substring(matcher.end(2));
			System.out.println(is);
		}
		res=res+is;
		return res;
	}
	
	@Test
	public void test() throws SnmpTrapHelperException {
		Event te = createTestEvent("translationTest", "Router", "192.168.1.1", "ICMP", "Down");
        assertTrue(m_config.isTranslationEvent(te));
	}
	
	private Event createTestEvent(String type, String nodeLabel, String ipAddr, String serviceName, String status) throws SnmpTrapHelperException {
        final List<Parm> parms = new ArrayList<Parm>();
        EventCreator eventCreator = new EventCreator(null); 
		// trapdIpMgr est utilisé dans setTrapAddress(InetAddress trapAddress)
		eventCreator.setCommunity("public");
		long  timeStamp= 0;
		eventCreator.setTimeStamp(timeStamp);
		SnmpObjId entId = new SnmpInstId(".1.3.6.1.4.1.388.50.0.11");
		TrapIdentity trapIdentity = new TrapIdentity(entId, 6,7);
		eventCreator.setTrapIdentity(trapIdentity);
		eventCreator.setVersion("v2c");
		SnmpTrapHelper th = new SnmpTrapHelper();
		String [][] params = {
				{".1.3.6.1.4.1.388.50.1.2.1.1","OctetString","CLIENT_INFO"},	
				{".1.3.6.1.4.1.388.50.1.2.1.2","OctetString","DOT11"},
				{".1.3.6.1.4.1.388.50.1.2.1.3","int32","6"},
				{".1.3.6.1.4.1.388.50.1.2.1.4","int32",""},
				{".1.3.6.1.4.1.388.50.1.2.1.5","OctetString","84:24:8D:DE:C3:E4"},	
				{".1.3.6.1.4.1.388.50.1.2.1.6","OctetString","apifms-DEC3E4"},	
		};
		for (String[] pair: params){
			SnmpObjId name = new SnmpInstId(pair[0]);
			SnmpValue value = null ;
			 SnmpTrapBuilder trap = th.createV2Trap("", "0");
			th.addVarBinding(trap,pair[0],pair[1],pair[2]);

			assertEquals("",trap.getClass().getName());
			Snmp4JV2TrapBuilder impl = (Snmp4JV2TrapBuilder) trap;
			
			eventCreator.processVarBind(name, value);
		}

		InetAddress trapAddress = InetAddressUtils.addr("172.16.1.54");
		Event event = eventCreator.getEvent();
		event.setInterfaceAddress(trapAddress);
        
        
        if(nodeLabel != null) parms.add(buildParm(EventConstants.PARM_NODE_LABEL, nodeLabel));
        if(ipAddr != null) parms.add(buildParm(EventConstants.PARM_PASSIVE_IPADDR, ipAddr));
        if(serviceName != null) parms.add(buildParm(EventConstants.PARM_PASSIVE_SERVICE_NAME, serviceName));
        if(status != null) parms.add(buildParm(EventConstants.PARM_PASSIVE_SERVICE_STATUS, status));

		return createEventWithParms("uei.opennms.org/services/"+type, parms);
	}
	
	private void setTrapAddress(Event event, InetAddress trapAddress) {
	        event.setSnmphost(str(trapAddress));
	        event.setInterface(str(trapAddress));
	       // pas de node id event.setNodeid(nodeId);
	    }
    private Event createEventWithParms(String uei, List<Parm> parms) {
		Event e = MockEventUtil.createEventBuilder("Automation", uei).getEvent();
		e.setHost("localhost");
        
        e.setParmCollection(parms);
        Logmsg logmsg = new Logmsg();
        logmsg.setContent("Testing Passive Status Keeper with down status");
        e.setLogmsg(logmsg);
        return e;
	}

    private Parm buildParm(String parmName, String parmValue) {
        org.opennms.netmgt.xml.event.Value v = new org.opennms.netmgt.xml.event.Value();
        v.setContent(parmValue);
        Parm p = new Parm();
        p.setParmName(parmName);
        p.setValue(v);
        return p;
    }
}
