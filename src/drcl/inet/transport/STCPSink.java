package drcl.inet.transport;

import drcl.comp.Port;
import drcl.comp.lib.bytestream.ByteStreamContract;
import drcl.inet.InetPacket;
import drcl.inet.host.SyncMobibedSocket;
import drcl.util.TimeLog;

public class STCPSink extends TCPSink{

	
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	public STCPSink(String id_, long peer_, int port_)
	{
		super(id_);
		peer = peer_;
		remotePort = port_;
		state = LISTEN;
	}
	
	/** The down port follows the {@link drcl.inet.contract.PktDelivery}
	 * contract. */
	protected void dataArriveAtDownPort(Object data_,
					Port downPort_) 
	{
		TCPPacket tcpp = null;
		try {
//			TimeLog.add("4. " + System.nanoTime() + " " + System.currentTimeMillis());
			tcpp = (TCPPacket)data_;
			
			switch(state){
			case LISTEN:
				if(tcpp.isSYN())
				{
					// recv SYN and send SYN_ACK
					recvSYN(tcpp);
					ack_syn_fin(true, false, tcpp.getTS());
					state = SYN_RCVD;
				}
				break;
			case SYN_RCVD:
				// recv ACK for SYN_ACK
				state = ESTABLISHED;
				if(isDebugEnabled() && (isDebugEnabledAt(DEBUG_SAMPLE)
						|| isDebugEnabledAt(DEBUG_RECV)))
					debug("received SYN ACK ACK. the connection is established.");
				break;
			case ESTABLISHED:
				long startT = System.nanoTime();
				if(tcpp.isFIN() && tcpp.isACK()){
					recvFINACK(tcpp);
					ack_syn_fin(false, true,tcpp.getTS());
					state = CLOSED;
					if(isDebugEnabled() && (isDebugEnabledAt(DEBUG_SAMPLE)
							|| isDebugEnabledAt(DEBUG_RECV)))
						debug("received FIN. the connection is closed.");
					upPort.doLastSending(new ByteStreamContract.Message(
							ByteStreamContract.STOP));
					getParent().removeComponent(this);
					SyncMobibedSocket.SOCKET_ON = false;
					drcl.ruv.System.WAITING_CNT = 1;
				}else
				{
					recv(tcpp, tcpp.getPacketSize()-tcpp.getHeaderSize());
					tcpp = null;
				}
				if(isDebugEnabled())debug("STCPSink receive pkt " + (System.nanoTime() - startT)
						+ " at " + System.nanoTime() + " " + System.currentTimeMillis());
				
				break;
			}
		}
		catch (Exception e_) {
			e_.printStackTrace();
			error(data_, "dataArriveAtDownPort()", downPort_,
							"unrecognized data? " + e_);
		} finally {
			if(tcpp != null) tcpp.free();
		}
	}
	
	/** 
	 * 
	 */
    protected void recvSYN(TCPPacket tcpp) {
    	rcv_nxt = tcpp.getSeqNo()+1;
		if(isDebugEnabled() && (isDebugEnabledAt(DEBUG_SAMPLE)
				|| isDebugEnabledAt(DEBUG_RECV)))
			debug("received SYN with seq = " + rcv_nxt);
	}

	/** 
	 * 
	 */
    protected void recvFINACK(TCPPacket tcpp) {
    	rcv_nxt = tcpp.getSeqNo();
    	this.snd_nxt = tcpp.getAckNo();
		if(isDebugEnabled() && (isDebugEnabledAt(DEBUG_SAMPLE)
				|| isDebugEnabledAt(DEBUG_RECV)))
			debug("received FIN+ACK with seq = " + rcv_nxt + 
					" ack = " + snd_nxt);
	}
    
	/** Sends an acknowledgement packet. */
	protected void ack_syn_fin(boolean syn_, boolean fin_, long aTS_)
	{
		TCPPacket pkt_;
		if(syn_) // send syn ack
			pkt_ = new TCPPacket(connection.getLocalPort(), 
					connection.getRemotePort(),
					getSeqNo(), rcv_nxt/*ackno+1*/, getAvailableReceivingBuffers()>>wind_scal,
					true/*ack*/, syn_, fin_, -1000/*TS*/, aTS_,
					NS_COMPATIBLE? 20: 30, 0, null);
		else
			pkt_ = new TCPPacket(connection.getLocalPort(), 
					connection.getRemotePort(),
					getSeqNo(), rcv_nxt+1/*ackno*/, getAvailableReceivingBuffers()>>wind_scal,
					true/*ack*/, syn_, fin_, -1000/*TS*/, aTS_,
					NS_COMPATIBLE? 20: 30, 0, null);
		if (isDebugEnabled() && (isDebugEnabledAt(DEBUG_SAMPLE)
								|| isDebugEnabledAt(DEBUG_SEND)))
			if(syn_)
				debug("SEND SYN ACK: " + (pkt_.getAckNo()/MSS)
						+ "/" + pkt_.getAckNo());
			else
				debug("SEND ACK: " + (pkt_.getAckNo()/MSS)
							+ "/" + pkt_.getAckNo());
	

		// defined in Protocol.java  
		// void forward(PacketBody p_, long src_, long dest_, int dest_ulp_,
		// boolean routerAlert_, int TTL, int ToS): route-lookup forwarding 
		if(drcl.ruv.System.SYNC)
			syncForward(pkt_, getLocalAddr(), peer, DEFAULT_PID, false, TTL, 0);
		else
			forward(pkt_, getLocalAddr(), peer, DEFAULT_PID, false, TTL, 0);
	}

	public void setWindScal(int wind_scal) {
		this.wind_scal = wind_scal;
		this.awnd_max = 0xffff << wind_scal;
		System.out.println("awnd max="+awnd_max);
	}
}