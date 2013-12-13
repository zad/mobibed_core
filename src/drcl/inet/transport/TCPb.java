// @(#)TCPb.java   7/2006
// Copyright (c) 1998-2006, Distributed Real-time Computing Lab (DRCL) 
// All rights reserved.
// 
// Redistribution and use in source and binary forms, with or without
// modification, are permitted provided that the following conditions are met:
// 
// 1. Redistributions of source code must retain the above copyright notice,
//    this list of conditions and the following disclaimer. 
// 2. Redistributions in binary form must reproduce the above copyright notice,
//    this list of conditions and the following disclaimer in the documentation
//    and/or other materials provided with the distribution. 
// 3. Neither the name of "DRCL" nor the names of its contributors may be used
//    to endorse or promote products derived from this software without specific
//    prior written permission. 
// 
// THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
// AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
// IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
// ARE DISCLAIMED. IN NO EVENT SHALL THE REGENTS OR CONTRIBUTORS BE LIABLE FOR
// ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
// DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
// SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
// CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
// OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
// OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
// 

package drcl.inet.transport;


import drcl.comp.*;

import drcl.comp.lib.bytestream.ByteStreamContract;
import drcl.inet.host.SyncMobibedSocket;
import drcl.util.TimeLog;


/**
Bi-directional (Single-session) TCP Protocol.
 
This class implements both the TCP sender and receiver.
Basically it is a result of carefully merging {@link TCP} and {@link TCPSink}.

<p>In the current implementation, ACKs are not piggy-backed in data segments.
This will be fixed shortly.
  
@version 1.0, 7/2001
 */
public class TCPb extends TCP implements ActiveComponent
{ 
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	/** Sets to true to make TCP ns compatible */
	public static boolean NS_COMPATIBLE = false;

//	static {
//		setContract(TCPb.class, "*@" + drcl.net.Module.PortGroup_UP,
//			new drcl.comp.lib.bytestream.ByteStreamContract(
//				Contract.Role_REACTOR));
//	}

	public String getName()
	{ return "tcp"; }
	
//	// seq# of first byte of each received packet
//	STCPSink tcpsink = new STCPSink("rcv", peer, remotePort) {
//		// make sure the sending and receiving side see the same local addr
//		public long getLocalAddr()
//		{ return TCPb.this.getLocalAddr(); }
//
//		public long getSeqNo()
//		{ return TCPb.this.snd_nxt; }
//	};v

	STCPSink tcpsink;
	
	protected int RBUFFER_SIZE = 110592;
	
	
	
	public void setReceivingBuffers(int awnd_)
	{ RBUFFER_SIZE = awnd_; }
	
	public void setWindowScale(int ws)
	{ 
		wind_scal = ws;
		TCPPacket.scale = ws;
		if(tcpsink != null)
			tcpsink.setWindScal(ws);
	}
	
	protected boolean DRWA = false;

	private boolean delayack_ = false;
	
	public void setDRWA(boolean b)
	{ DRWA = b; }
	
	void initTCPSink()
	{
		if(isDebugEnabled())
			debug("init TCPSink");
		tcpsink = new STCPSink("tcpsink",peer,remotePort){
			private static final long serialVersionUID = 1L;
			// make sure the sending and receiving side see the same local address
			public long getLocalAddr()
			{ return TCPb.this.getLocalAddr(); }
			public long getSeqNo()
			{ return TCPb.this.snd_nxt; }
			public int getRemotePort(){
				return TCPb.this.remotePort;
			}
			public long getPeer(){
				return TCPb.this.peer;
			}
		};
		addComponent(tcpsink);
		tcpsink.seqNoPort.connectTo(addEventPort(SEQNO_RCV_PORT_ID));
		tcpsink.upPort = upPort;
		tcpsink.downPort = downPort;
		tcpsink.setConnection(this);
		tcpsink.setDebugEnabled(isDebugEnabled());
		tcpsink.setDebugEnabledAt(isDebugEnabled(), 
				this.getDebugLevelNames());
		tcpsink.setMSS(this.getMSS()); 
		tcpsink.setSackEnabled(SACK);
		tcpsink.setDelayACKEnabled(delayack_ );
//		tcpsink.setReceivingBuffers(RBUFFER_SIZE);
		tcpsink.DRWA = DRWA;
		tcpsink.setWindScal(wind_scal);
	}
	
	public TCPb()
	{ super(); 	}
	
	public TCPb(String id_)
	{ super(id_);	}
	
	public void _start(){
		super._start();
		initTCPSink();
	}
	
	

	public TCPb(String id_, long peer_, int remotePort_, 
			boolean bebug, String[] levels, 
			Port upPort, Port downPort, Port cwndPort, Port srttPort, Port sstPort, Port cwndCntPort,
			Port awndPort,
			String implementation, boolean maxburst, int mSS, boolean sACK, int scale) {
		super(id_);
		peer = peer_;
		remotePort = remotePort_;
		state = CLOSED;
		this.setDebugEnabled(bebug);
		this.setDebugEnabledAt(bebug, levels);
		this.upPort = upPort;
		this.downPort = downPort;
		this.cwndPort = cwndPort;
		this.srttPort = srttPort;
		this.sstPort = sstPort;
		this.cwndCntPort = cwndCntPort;
		this.awndPort = awndPort;
		this.setImplementation(implementation);
		this.setMaxburstEnabled(maxburst);
		this.setMSS(mSS);
		this.setSackEnabled(sACK);
		this.setWindowScale(scale);
	}
	
//	public void _start(){
//		initTCPSink();
//		tcpsink.setDebugEnabled(isDebugEnabled());
//		tcpsink.setDebugEnabledAt(isDebugEnabled(), 
//				this.getDebugLevelNames());
//	}

	public void reset()
	{
		super.reset();
		tcpsink.reset();
	}
	
	public void duplicate(Object source_) 
	{ 
		super.duplicate(source_);
	}

	public void setDownPort(Port downPort_)
	{ downPort = tcpsink.downPort = downPort_; }

	public void setTTL(int ttl)
	{ super.setTTL(ttl); tcpsink.setTTL(ttl); }
	
	public void setMSS(int mss)
	{ super.setMSS(mss);}
	
	
	protected int getReceivingBuffers()
	{ return tcpsink.getReceivingBuffers();	}
	

	
	protected int getAvailableReceivingBuffers()
	{ return tcpsink.getAvailableReceivingBuffers();	}
	
	public void setSackEnabled(boolean sack_)
	{ super.setSackEnabled(sack_);  }

	public void setDelayACKEnabled(boolean delayack_)
	{ this.delayack_ = delayack_; }

	public boolean isDelayACKEnabled()
	{ return tcpsink.isDelayACKEnabled(); }

	public void setDelayACKTimeout(long v_)
	{ tcpsink.setDelayACKTimeout(v_); }

	public double getDelayACKTimeout()
	{ return tcpsink.getDelayACKTimeout(); }

	public void setPeer(long peer_)
	{ super.setPeer(peer_); tcpsink.peer = peer_; }	
	
	/** The down port follows the {@link drcl.inet.contract.PktDelivery}
	 * contract. */
	protected void dataArriveAtDownPort(Object data_,
					Port downPort_) 
	{
		TCPPacket tcppkt_ = null;
		try {
			tcppkt_ = (TCPPacket)data_;
			
			if(this.state == ESTABLISHED)
			{
				
				int bodySize = tcppkt_.getPacketSize() - tcppkt_.getHeaderSize();
				if (bodySize>0)
				{
					// data transmission
					// receive packet
					tcpsink.recv(tcppkt_, bodySize);
					// received packet may insert into buffer,
					// so I don't want to free it in the finally block
					// it will be freed in tcpsink
					if(isDebugEnabled()) debug("receive data pakcet " + tcppkt_);
					tcppkt_ = null;		
				}
				else if(tcppkt_.isACK())
					if(!tcppkt_.isFIN())
					{
						// receive ack
						if(isDebugEnabled()) debug("receive ACK " + tcppkt_);
						recv(tcppkt_);
						if (isAppStopped && sbuffer.getSize() == 0) {
							debug("try to terminate the connection");
							debug("send FinAck");
							ack_syn_fin(true, false, true);
							state = FIN_WAIT_1;
							resetRXTTimer(getTime(), "FIN_WAIN_1");
						}	
					}
					else
					{
						// connection termination
						tcpsink.dataArriveAtDownPort(data_, downPort_);
						return;
					}
			}else{
				// TCP synchronization process 
				// connection creation
				if(tcppkt_.isSYN() && !tcppkt_.isACK() && tcpsink.state == LISTEN)
				{	// receive SYN
					tcpsink.dataArriveAtDownPort(data_, downPort_);
					if(isDebugEnabled()) debug("receive SYN " + tcppkt_);
				}
				else if(tcppkt_.isACK() && tcppkt_.isSYN() && this.state == SYN_SENT)
				{	// receive SYNACK
					super.dataArriveAtDownPort(data_, downPort_);
					tcpsink.state = ESTABLISHED;
					if(isDebugEnabled()) debug("receive SYN ACK " + tcppkt_);
				} 	
				else if(tcppkt_.isACK() && tcpsink.state == SYN_RCVD)
				{	// recv SYN_ACK ACK
					if(isDebugEnabled()) debug("receive SYN ACK ACK " + tcppkt_);
					tcpsink.dataArriveAtDownPort(data_, downPort_);
					
					state = ESTABLISHED;
					this.snd_nxt = 0; 
					this.snd_una = 0; // XXX sequence number starts from 1
					// connection established
					// start the application
					ByteStreamContract.Message start = 
							new ByteStreamContract.Message(
									ByteStreamContract.START,  sbuffer.getAvailableSpace(), id);
					if(this.isDebugEnabled()) debug("send message " + start);
					upPort.doSyncSending(start);
				}else if(tcppkt_.isACK() && state == FIN_WAIT_1)
				{	
					// close current connect
					super.dataArriveAtDownPort(data_, downPort_);
					if (cwndPort._isEventExportEnabled())
					{
						cwndPort.exportEvent(CWND_EVENT,
								"CLOSE", null);
					}
					SyncMobibedSocket.SOCKET_ON = false;
					drcl.ruv.System.WAITING_CNT = 1;
				}				
			}
		}
		catch (Exception e_) {
			error(data_, "dataArriveAtDownPort()", downPort_,
							"unrecognized data: " + e_);
			e_.printStackTrace();
		}finally {
			if(tcppkt_ != null) tcppkt_.free();
		}
	}
	
//	// XXX:
//	protected long getAckNo()
//	{ return tcpsink.rcv_nxt; }
	
	// XXX:
	protected int getAvailableRcvBuffer()
	{ return tcpsink.getAvailableReceivingBuffers(); }


	public String info()
	{
		return super.info() + "\nReceiving side:\n" + tcpsink.info();
	}

	public void setRemotePort(int remotePort_) {
		remotePort = remotePort_;
	}
}
