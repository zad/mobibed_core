package drcl.inet.transport;


import java.util.concurrent.ConcurrentHashMap;

import drcl.comp.ActiveComponent;
import drcl.comp.Port;
import drcl.comp.lib.bytestream.ByteStreamContract;
import drcl.inet.InetPacket;
import drcl.inet.host.MobibedUtils;

/**
 * {@link TCPServer} supports multiple TCP clients at the same time
 * @author andong
 *
 */
public class TCPServer extends drcl.inet.Protocol 
implements Connection, TCPConstants, ActiveComponent{
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	public String getName()
	{ return "TCPServer"; }
	
	ConcurrentHashMap<Integer, TCPb> tcpMap;
	private TCPb default_tcp;

	
	
	
	public String[] getDebugLevelNames()
	{ return DEBUG_LEVELS; }
	
	public TCPServer()
	{ super(); }
	
	public TCPServer(String id_)
	{ super(id_); }
	
	public void _start(){
		tcpMap = new ConcurrentHashMap<Integer, TCPb>();
		default_tcp = new TCPb("default", 0, 0,
				isDebugEnabled(),this.getDebugLevelNames(), 
				upPort, downPort, cwndPort, srttPort, sstPort, cwndCntPort, awndPort,
				implementation, maxburst, MSS, SACK, TCPPacket.scale);
	}
	
	private int port;
	public void setLocalPort(int p){
		port = p;
	}
	
	String implementation = RENO;
	boolean maxburst = true;
	
	int MSS = 512;
	
	public void setMSS(int mss)
	{
		MSS = mss;
	}
	
	public void setMaxburstEnabled(boolean max)
	{ maxburst = max;}
	
	public void setImplementation(String impl_)
	{
		implementation = impl_;
	}
	
	private boolean SACK = false;
	
	public void setSackEnabled(boolean sack_){
		SACK = sack_;
	}
	
	/** Port to export the congestion window changed event. */
	protected Port cwndPort       = addEventPort(CWND_PORT_ID);
	
	protected Port awndPort       = addEventPort(AWND_PORT_ID);
	
	protected Port sstPort       = addEventPort(SST_PORT_ID);
	/** Port to export the SRTT event. */
	protected Port srttPort        = addEventPort(SRTT_PORT_ID);
	
	protected Port cwndCntPort = addEventPort(CWND_CNT_PORT_ID);
	
	protected void dataArriveAtUpPort(Object data_, Port upPort_)
	{
		ByteStreamContract.Message msg_ = (ByteStreamContract.Message) data_;
		String key_ = msg_.getKey();
		if(tcpMap.containsKey(key_)){
			tcpMap.get(key_).dataArriveAtUpPort(data_, upPort_);
		}else
			error("dataArriveAtUpPort", key_);
	}
	
	/** The down port follows the {@link drcl.inet.contract.PktDelivery}
	 * contract. */
	protected synchronized void dataArriveAtDownPort(Object data_,
					Port downPort_) 
	{
		try {
//			long peer = ((InetPacket)data_).getSource();
			TCPPacket tcpp = (TCPPacket)data_;
			int remotePort = tcpp.getSPort();
			TCPb tcp;
			if(tcpp.isSYN() && !tcpp.isACK()){
				// new TCP
				tcp = default_tcp;
				default_tcp = null;
				tcp.setID(Integer.toString(remotePort));
				tcp.setRemotePort(remotePort);
				// connect MTCPSink with this TCPSink
				this.addComponent(tcp);
				
				tcpMap.put(remotePort, tcp);
			}else
				tcp = tcpMap.get(remotePort);
			if(tcp !=null)
				tcp.dataArriveAtDownPort(data_, downPort_);
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
		} finally {
			if(default_tcp == null){
				default_tcp = new TCPb("default", 0, 0,
						isDebugEnabled(),this.getDebugLevelNames(), 
						upPort, downPort, cwndPort, srttPort, sstPort, cwndCntPort, awndPort,
						implementation, maxburst, MSS, SACK, TCPPacket.scale);	
			}
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

	@Override
	public void setMaxReceiveBufferSize(int size) {
		TCP.TCP_MAX_RMEM = size;
	}

	@Override
	public void setMaxSendBufferSize(int size) {
		TCP.TCP_MAX_WMEM = size;
		
	}
}
