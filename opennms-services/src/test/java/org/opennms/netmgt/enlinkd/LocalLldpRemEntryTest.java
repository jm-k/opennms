package org.opennms.netmgt.enlinkd;

import static org.junit.Assert.*;
import static org.opennms.core.utils.InetAddressUtils.str;

import java.net.InetAddress;
import java.util.LinkedList;

import org.junit.Test;
import org.opennms.core.utils.InetAddressUtils;
import org.opennms.netmgt.enlinkd.snmp.LldpLocPortGetter;
import org.opennms.netmgt.enlinkd.snmp.LldpRemManTableTracker;
import org.opennms.netmgt.enlinkd.snmp.LldpRemTableTracker;
import org.opennms.netmgt.model.LldpLink;
import org.opennms.netmgt.model.OnmsNode;
import org.opennms.netmgt.snmp.SnmpAgentConfig;
import org.opennms.netmgt.snmp.SnmpConfiguration;
import org.opennms.netmgt.snmp.SnmpUtils;
import org.opennms.netmgt.snmp.SnmpWalker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LocalLldpRemEntryTest {
	 private Logger LOG = LoggerFactory.getLogger(LldpRemTableTracker.class);
	@Test
	public void test() {
	   
		String TARGET = "172.16.5.60";
        final LldpLocPortGetter lldpLocPort = new LldpLocPortGetter(getPeer(TARGET));
        String trackerName = "lldpRemTable";
        LinkedList<LldpLink> lldpLinks = new LinkedList<LldpLink>();
        LldpRemTableTracker lldpRemTable = new LldpRemTableTracker() {
        	@Override
        	public void processLldpRemRow(final LldpRemRow row) {
        		LldpLink res = row.getLldpLink(lldpLocPort);
        		
        		OnmsNode node = new OnmsNode("172.16.5.60");
        		node.setId(12);
				res.setNode(node);
				lldpLinks.add(res);
        	}
        };
        LOG.info( "run: collecting {} on: {}",trackerName, TARGET);
       SnmpWalker walker = SnmpUtils.createWalker(getPeer(TARGET), trackerName, lldpRemTable);
        walker.start();
        
        try {
            walker.waitFor();
            if (walker.timedOut()) {
            	LOG.info(
                        "run:Aborting node scan : Agent timed out while scanning the {} table", trackerName);
            }  else if (walker.failed()) {
            	LOG.info(
                        "run:Aborting node scan : Agent failed while scanning the {} table: {}", trackerName,walker.getErrorMessage());
            }
        } catch (final InterruptedException e) {
            LOG.error("run: collection interrupted, exiting",e);
            return;
        }
        
        for (LldpLink lldplink: lldpLinks){
        	assertNotNull(lldplink.getLLdpRemTimeMark());
        	assertEquals("7435",lldplink.getLLdpRemTimeMark());
        	assertNotNull(lldplink.getLldpRemIndex());
        	assertEquals("1",lldplink.getLldpRemIndex());
        	trackerName = "lldpManTable";
        	LldpRemManTableTracker lldpManTable = new LldpRemManTableTracker(lldplink) {
        		@Override
            	public void processLldpRemManRow(final LldpRemManRow row) {
            		assertEquals((Integer)4,row.getlldpRemManAddrSubtype());
            		assertEquals("172.16.5.61",str(row.getlldpRemManAddr()));
            	}
            };
            walker = SnmpUtils.createWalker(getPeer(TARGET), trackerName, lldpManTable);
            walker.start();
            try {
                walker.waitFor();
                if (walker.timedOut()) {
                	LOG.info(
                            "run:Aborting node scan : Agent timed out while scanning the {} table", trackerName);
                }  else if (walker.failed()) {
                	LOG.info(
                            "run:Aborting node scan : Agent failed while scanning the {} table: {}", trackerName,walker.getErrorMessage());
                }
            } catch (final InterruptedException e) {
                LOG.error("run: collection interrupted, exiting",e);
                return;
            }
            
	}
	}

	private SnmpAgentConfig getPeer(String target) {
		SnmpConfiguration conf = new SnmpConfiguration();
		conf.setReadCommunity("public");
		conf.setVersion(2);
		return new SnmpAgentConfig(InetAddressUtils.getInetAddress(target),conf);
	}
	@Test
	public void test2() {
		String TARGET = "172.16.5.248";

        final LldpLocPortGetter lldpLocPort = new LldpLocPortGetter(getPeer(TARGET));
        String trackerName = "lldpRemTable";
        LinkedList<LldpLink> lldpLinks = new LinkedList<LldpLink>();
        LldpRemTableTracker lldpRemTable = new LldpRemTableTracker() {
        	@Override
        	public void processLldpRemRow(final LldpRemRow row) {
        		LldpLink res = row.getLldpLink(lldpLocPort);
        		
        		OnmsNode node = new OnmsNode(TARGET);
        		node.setId(12);
				res.setNode(node);
				lldpLinks.add(res);
        	}
        };
        LOG.info( "run: collecting {} on: {}",trackerName, TARGET);
       SnmpWalker walker = SnmpUtils.createWalker(getPeer(TARGET), trackerName, lldpRemTable);
        walker.start();
        
        try {
            walker.waitFor();
            if (walker.timedOut()) {
            	LOG.info(
                        "run:Aborting node scan : Agent timed out while scanning the {} table", trackerName);
            }  else if (walker.failed()) {
            	LOG.info(
                        "run:Aborting node scan : Agent failed while scanning the {} table: {}", trackerName,walker.getErrorMessage());
            }
        } catch (final InterruptedException e) {
            LOG.error("run: collection interrupted, exiting",e);
            return;
        }
        
        for (LldpLink lldplink: lldpLinks){
        	assertNotNull(lldplink.getLLdpRemTimeMark());
        	System.out.println("TimeMark" + (lldplink.getLLdpRemTimeMark()));
        	assertNotNull(lldplink.getLldpRemIndex());
        	assertEquals("1",lldplink.getLldpRemIndex());
        	trackerName = "lldpManTable";
        	LldpRemManTableTracker lldpManTable = new LldpRemManTableTracker(lldplink) {
        		@Override
            	public void processLldpRemManRow(final LldpRemManRow row) {
            		assertEquals((Integer)4,row.getlldpRemManAddrSubtype());
            		System.out.println("ManAddr" + str(row.getlldpRemManAddr()));
            	}
            };
            walker = SnmpUtils.createWalker(getPeer(TARGET), trackerName, lldpManTable);
            walker.start();
            try {
                walker.waitFor();
                if (walker.timedOut()) {
                	LOG.info(
                            "run:Aborting node scan : Agent timed out while scanning the {} table", trackerName);
                }  else if (walker.failed()) {
                	LOG.info(
                            "run:Aborting node scan : Agent failed while scanning the {} table: {}", trackerName,walker.getErrorMessage());
                }
            } catch (final InterruptedException e) {
                LOG.error("run: collection interrupted, exiting",e);
                return;
            }
            
	}
	}
}
