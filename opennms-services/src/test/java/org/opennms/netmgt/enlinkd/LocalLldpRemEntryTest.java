package org.opennms.netmgt.enlinkd;

import static org.junit.Assert.*;
import static org.opennms.core.utils.InetAddressUtils.str;

import java.net.InetAddress;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

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

import com.sun.tools.javac.util.Pair;

public class LocalLldpRemEntryTest {
	 private Logger LOG = LoggerFactory.getLogger(LldpRemTableTracker.class);
	 
	 class RemManCollector {
     	private Comparator<int[]> comparator = new Comparator<int[]> (){
				@Override
				public int compare(int[] left, int[] right) {
					final int cmp0 = Integer.compare(left[0],right[0]);
					if (cmp0 ==0 ) {
						final  int cmp1 = Integer.compare(left[1],right[1]);
						if (cmp1 == 0) {
							return Integer.compare(left[2],right[2]);
						} else {
							return cmp1;
						}
						
					} else {
						return cmp0;
					}
				}
     		
     	};
			private SortedMap<int[],Pair<LldpLink,List<InetAddress>> > m_map = new TreeMap<int[], Pair<LldpLink,List<InetAddress>>>(comparator);

			public void add(int[] lldpManIndex, LldpLink lldplink) {
				Pair<LldpLink, List<InetAddress>> pair = new Pair<LldpLink, List<InetAddress>>(lldplink, new LinkedList<InetAddress>());
				m_map.put(lldpManIndex, pair);
				
			}

			public List<InetAddress> getLldpLink(int lLdpRemTimeMark, int lldpLocalPortNum, int lldpRemIndex) {
				final int [] key =  {lLdpRemTimeMark,lldpLocalPortNum,lldpRemIndex,};
				return m_map.get(key).snd;
			}

			public void addToLldpLink(int[] lldpManIndex, InetAddress lldpRemManAddr) {
				m_map.get(lldpManIndex).snd.add(lldpRemManAddr);
				
			}

			public  Collection<Pair<LldpLink,List<InetAddress>>> getValues() {
				return m_map.values();
			}
     	
     }
	@Test
	public void test() {
	   
		String TARGET = "172.16.5.60";
        final LldpLocPortGetter lldpLocPort = new LldpLocPortGetter(getPeer(TARGET));
        String trackerName = "lldpRemTable";
        RemManCollector remManCollector= new RemManCollector();
        LldpRemTableTracker lldpRemTable = new LldpRemTableTracker() {
        	@Override
        	public void processLldpRemRow(final LldpRemRow row) {
        		LldpLink res = row.getLldpLink(lldpLocPort);
        		
        		OnmsNode node = new OnmsNode("172.16.5.60");
        		node.setId(12);
				res.setNode(node);
				remManCollector.add(row.getLldpRemRelIndex(),res);
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
        
        
        trackerName = "lldpManTable";
        LldpRemManTableTracker lldpManTable = new LldpRemManTableTracker() {
    		@Override
        	public void processLldpRemManRow(final LldpRemManRow row) {
    			assertEquals(7435,row.getLLdpRemTimeMark());
    			assertEquals(26,row.getLLdpRemLocalPortNum());
        		assertEquals(1,row.getlldpRemIndex());
        		assertEquals(1,row.getlldpRemManAddrSubtype()); //ipv4
        		assertEquals(4,row.getlldpRemManAddrLength()); 
        		assertEquals("172.16.5.61",str(row.getlldpRemManAddr()));
        		System.out.println("ManAddr" + str(row.getlldpRemManAddr()));
        		assertEquals(3,row.getlldpManIndex().length);
        		assertEquals(7435,row.getlldpManIndex()[0]);
        		assertEquals(26,row.getlldpManIndex()[1]);
        		assertEquals(1,row.getlldpManIndex()[2]);
        		remManCollector.addToLldpLink(row.getlldpManIndex(),row.getlldpRemManAddr());
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
        assertEquals(1,remManCollector.getValues().size());
        for (Pair<LldpLink,List<InetAddress>> lldplink : remManCollector.getValues()){
        	assertEquals(1,(lldplink.snd.size()));
        	List<InetAddress> res = lldplink.snd;
        	assertNotNull(res);
        	assertEquals(1,res.size());
        	assertEquals("172.16.5.61",str(res.get(0)));
            
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
        
        
        RemManCollector remManCollector= new RemManCollector();
        
        LldpRemTableTracker lldpRemTable = new LldpRemTableTracker() {
        	@Override
        	public void processLldpRemRow(final LldpRemRow row) {
        		LldpLink res = row.getLldpLink(lldpLocPort);
        		
        		OnmsNode node = new OnmsNode(TARGET);
        		node.setId(12);
				res.setNode(node);
				remManCollector.add(row.getLldpRemRelIndex(),res);
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
       
        trackerName = "lldpManTable";
        LldpRemManTableTracker lldpManTable = new LldpRemManTableTracker() {
    		@Override
        	public void processLldpRemManRow(final LldpRemManRow row) {
        		remManCollector.addToLldpLink(row.getlldpManIndex(),row.getlldpRemManAddr());
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
        
        

		int cpt =0;
        for (Pair<LldpLink,List<InetAddress>> lldplink : remManCollector.getValues()){
        	
        	List<InetAddress> ias = lldplink.snd;
        	if(ias != null ){
        		System.out.println(lldplink);
        		for (InetAddress ia: ias){
					cpt++;
					
        			System.out.println(str(ia));
        		}
        	}
        	
	}
        assertEquals(16,cpt);
       
        
        
        
	}
}
