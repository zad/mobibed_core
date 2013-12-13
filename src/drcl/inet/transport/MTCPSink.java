package drcl.inet.transport;

import java.util.HashMap;

import drcl.comp.Component;
import drcl.comp.Contract;
import drcl.comp.Port;
import drcl.inet.InetPacket;
import drcl.inet.host.MobibedUtils;

public class MTCPSink extends drcl.inet.Protocol implements Connection{
	public String getName()
	{ return "mtcpsink"; }
	
	HashMap<String, STCPSink> tcpSinkMap;

	static final String[] DEBUG_LEVELS = 
		{"rcv", "send", "sack", "out-of-order", "sample"};
	public static final int DEBUG_RCV= 0;
	public static final int DEBUG_SEND = 1;
	public static final int DEBUG_SACK = 2;
	public static final int DEBUG_OOO = 3;
	public static final int DEBUG_SAMPLE = 4;
	public String[] getDebugLevelNames()
	{ return DEBUG_LEVELS; }
	
	public MTCPSink()
	{ super(); tcpSinkMap = new HashMap<String, STCPSink>();}
	
	public MTCPSink(String id_)
	{ super(id_); tcpSinkMap = new HashMap<String, STCPSink>();}
	
	int port;
	public void setLocalPort(int p){
		port = p;
	}
	
	/** The down port follows the {@link drcl.inet.contract.PktDelivery}
	 * contract. */
	protected synchronized void dataArriveAtDownPort(Object data_,
					Port downPort_) 
	{
		try {
			long peer = ((InetPacket)data_).getSource();
			TCPPacket tcpp = (TCPPacket)((InetPacket)data_).getBody();
			int remotePort = tcpp.getSPort();
			String key = MobibedUtils.LongToIPString(peer)+":"+remotePort;
			STCPSink sink;
			if(!tcpSinkMap.containsKey(key) && tcpp.isSYN()){
				// new TCPSink
				sink = new STCPSink(key, peer, remotePort);
				
				sink.setDebugEnabled(isDebugEnabled());
				sink.setDebugEnabledAt(isDebugEnabled(), 
						this.getDebugLevelNames());
				// connect MTCPSink with this TCPSink
				this.addComponent(sink);
				sink.upPort = upPort;
				sink.downPort = downPort;
				tcpSinkMap.put(key, sink);
			}else
				sink = tcpSinkMap.get(key);
			if(sink !=null)
				sink.dataArriveAtDownPort(data_, downPort_);
			else
			{
				drop(tcpp);
				debug("drop: " + tcpp);
			}
		}
		catch (Exception e_) {
			e_.printStackTrace();
			error(data_, "dataArriveAtDownPort()", downPort_,
							"unrecognized data? " + e_);
		}
	}

	@Override
	public long getPeer() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public int getRemotePort() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public long getLocalAddr() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public int getLocalPort() {
		
		return port;
	}
}
