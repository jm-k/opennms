package org.opennms.netmgt.enlinkd;

import java.net.InetAddress;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

import org.opennms.netmgt.model.LldpLink;

import com.sun.tools.javac.util.Pair;

public class RemManCollector {

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
