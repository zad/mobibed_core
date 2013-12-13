// @(#)TCPSink.java   7/2006
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


import java.util.Vector;
import drcl.data.DoubleObj;
import drcl.comp.*;
import drcl.util.scalar.LongInterval;
import drcl.util.scalar.LongSpace;
import drcl.comp.lib.bytestream.ByteStreamContract;
import drcl.inet.InetPacket;
import drcl.net.Address;

/**
This component implements the single-session receiving-side TCP.

<p>By default, when this component receives a TCP packet,
it processes the timestamp option and sends back an acknowledgement.
In addition, one can enable the SACK flag to make this component
append the SACK blocks in the acknowledgement packets.
The realization of SACK is based on [RFC2018].

<p>This component also implements delayed ACK.
The delay timer is 100ms(0.1 second) as specified in [TCPILL2].
When the delayed ACK flag is enabled, an acknowledgment is sent when
(1) the delay timer expires or (2) a new data packet arrives.

<p>Since only one session is handled in this component,
open and close of a connection are not implemented,
nor is 3-way handshaking.

<p>Additional usage infomation:
<ol>
<li> To change TTL value, use {@link #setTTL(int)}.
<li> To change receiving buffer size, use {@link #setReceivingBuffers(int)}.
<li> To change delay timer period, use {@link #setDelayACKTimeout(double)}.
</ol>

References:
<ul>
<li>[TCPILL1] W. Stevens, TCP/IP Illustrated vol.1: The Protocols,
	Addison-Wesley,1994. 
<li>[TCPILL2] G. Wright and W. Stevens, TCP/IP Illustrated vol.2: The
	Implementation, Addison-Wesley,1995 
<li>[RFC793] J. Postel, Transmission Control Protocol, September 1981. 
<li>[RFC2018] M. Mathis, J. Mahdavi, S. Floyd and A. Romanow, TCP Selective
	Acknowledgment Options, Octobor 1996.
<li>[RFC2581] M. Allman, V. Paxson and W. Stevens, TCP Congestion Control,
	April 1999. 
</ul>

@see TCP
@see TCPPacket
@author Yuan Gao, Yung-ching Hsiao, Hung-ying Tyan
 */
public class TCPSink extends drcl.inet.Protocol
			implements TCPConstants, Connection, ActiveComponent
{ 

	
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	/** Sets to true to make TCP ns compatible */
	public static boolean NS_COMPATIBLE = false;

	static {
		setContract(TCPSink.class, "*@" + drcl.net.Module.PortGroup_UP,
			new drcl.comp.lib.bytestream.ByteStreamContract(
					Contract.Role_REACTOR));
	}
	
	@Override
	public void setMaxReceiveBufferSize(int size) {
		TCP.TCP_MAX_RMEM = size;
	}

	@Override
	public void setMaxSendBufferSize(int size) {
		TCP.TCP_MAX_WMEM = size;
		
	}

	public String getName()
	{ return "tcp"; }
	
	// seq# of first byte of each received packet
	Port seqNoPort = addEventPort(SEQNO_PORT_ID);

	protected boolean SACK = false;	// SACK flag	
	
	// sack blocks, sorted by the time the blocks are created, the first one
	// is the most recent
//	transient LinkedList<LongInterval> llSackBlock = null;

	/**
	 * Delay acknowledgement flag, set it as TRUE will let this sink
	 * use delay acknowledgement. Default delay timer is set as
	 * 100ms as in [TCPILL2].
	 */
	boolean DelayACK = false; // Use delayed ACK
	transient TM_EVT ACKPending = null; // Delayed ACK pending
	
	long peer = Address.NULL_ADDR;	// Used for Des in forwarding
	long delayTimer = 100; //Delay ACK timeout value (milliseconds)
	
	/* for threeway-handshaking*/
	transient int state = CLOSED;

	int TTL = 255;
	int MSS = 512;

//	int RBUFFER_SIZE = TCP.AWND_DEFAULT * MSS;	// receiving buffer size 64K
	int RBUFFER_SIZE;
	protected int wind_scal = 2;
	protected int awnd_max = Integer.MAX_VALUE;
	long WNDBG = 1;	// Receiving window beginning
	transient LongSpace receivedSeq = new LongSpace(0, 0);
		// used to calculate available receiving buffer and construct SACK
		// blocks
	
	transient Vector<TCPPacket> rbuffer = new Vector<TCPPacket>();
		// storing outstanding TCP packets (not yet sent to application)
	transient long snd_nxt = WNDBG;
		// sequence # of next byte to be sent to application
	transient long rcv_nxt = WNDBG;
		// sequence # of next byte expected to be received from the remote peer
	transient boolean appAskedForData = true; 
	Connection connection = this;
    
	/*
	 * varibles for DRWA: Dynamic Receiver Window Adjustment
	 */
	public double rtt_min = Double.MAX_VALUE, rtt_est = 0, rtt_sum = 0;
	public int rwnd = 0, lambda = 3, alpha = 7, rtt_cnt=0; // here alpha is 8 times in the paper 
	public long cwnd_est = this.RBUFFER_SIZE;
	public long ack_marked, data_rcvd;
	public boolean DRWA = false;

	public String[] getDebugLevelNames()
	{ return DEBUG_LEVELS; }
	
	public TCPSink()
	{ super(); RBUFFER_SIZE = TCP.TCP_MAX_RMEM;}
	
	public TCPSink(String id_)
	{ super(id_); RBUFFER_SIZE = TCP.TCP_MAX_RMEM;}
	

	public void _start(){
		RBUFFER_SIZE = TCP.TCP_MAX_RMEM;
	}
	
	public void reset()
	{
		super.reset();
		state = ESTABLISHED;
		if (rbuffer != null) rbuffer.removeAllElements();
		snd_nxt = WNDBG;
		rcv_nxt = WNDBG;
		appAskedForData = true;
		receivedSeq.reset(0, 0);
//		if (llSackBlock != null) 
//			llSackBlock = null;
	}

	public void duplicate(Object source_) 
	{ 
		super.duplicate(source_);
		TCPSink that_ = (TCPSink)source_;
		setTTL(that_.getTTL());
		setReceivingBuffers(that_.getReceivingBuffers());
		setMSS(that_.getMSS());
		setDelayACKEnabled(that_.isDelayACKEnabled());
		setSackEnabled(that_.isSackEnabled());
		setDelayACKTimeout(that_.getDelayACKTimeout());
	}

	public void setTTL(int ttl)
	{ TTL = ttl; }
	
	public int getTTL()
	{ return TTL;	}
		
	public void setMSS(int mss)
	{
		if (MSS != mss) {
			setReceivingBuffers(RBUFFER_SIZE / mss * mss);
			MSS = mss;
		}
	}
	
	public int getMSS()
	{ return MSS; }

	public void setReceivingBuffers(int awnd_)
	{ RBUFFER_SIZE = awnd_; }
	
	public int getReceivingBuffers()
	{ return RBUFFER_SIZE;	}
	
	public int getAvailableReceivingBuffers()
	{
		/*
		return RBUFFER_SIZE - (int)receivedSeq.getSize(snd_nxt, snd_nxt
						+ RBUFFER_SIZE);
		*/
		return RBUFFER_SIZE - (int)(rcv_nxt - snd_nxt);
	}
	
	public void setSackEnabled(boolean sack_)
	{
		SACK = sack_;
//		if (!SACK)
//			llSackBlock = null;
	}

	public boolean isSackEnabled()
	{ return SACK; }

	public void setDelayACKEnabled(boolean delayack_)
	{ DelayACK = delayack_; }

	public boolean isDelayACKEnabled()
	{ return DelayACK; }

	public void setDelayACKTimeout(long v_)
	{ delayTimer = v_; }

	public long getDelayACKTimeout()
	{ return delayTimer; }
	
	/** Handles timeout events. */
	protected void timeout(Object evt_)
	{
		switch(((TM_EVT)evt_).type) {
		case DELAY_ACK:
			synchronized (rbuffer) {
				ack_syn_fin(false, ((TM_EVT)evt_).aTS);
				ACKPending = null;
			}
			break;
		}			
	}

	
	protected void sendDataToApp() 
	{
		try {
			// send to application as many bytes as possible
			synchronized (rbuffer) {
				if (rcv_nxt == snd_nxt) return;
				boolean bufferFull_ = getAvailableReceivingBuffers() == 0;
				int i = 0;
				for (; i<rbuffer.size(); ) {
					TCPPacket pkt_ = (TCPPacket)rbuffer.elementAt(i);
					long seqno_ = pkt_.getSeqNo();
					if (seqno_ > snd_nxt) break;
					// end_: exclusive
					long end_ = seqno_ + pkt_.size - pkt_.headerSize;
					//boolean entirePkt_ = rcv_nxt >= end_;
					//if (!entirePkt_) end_ = rcv_nxt;
					ByteStreamContract.Message sendReq_ =
						new ByteStreamContract.Message(
								ByteStreamContract.RECV,
								(byte[])pkt_.getBody(),
								(int)(snd_nxt-seqno_), 
								(int)(end_-snd_nxt));
					// trick: use upPort instead of upPort_
					int len_ = (int)(end_-snd_nxt);
					upPort.doSyncSending(sendReq_);
					//int len_ = ((Integer)upPort.sendReceive(
						//					sendReq_)).intValue();
					snd_nxt = len_ >= 0? end_: end_ + len_;
					//if (entirePkt_ && len_ >= 0) // this pkt is cleared
					if (len_ >= 0) // this pkt is cleared
						i++;
					pkt_.free();
					rbuffer.remove(i-1);
					if(DRWA)
						drwa();
					// break the loop if no more bytes available or
					// application cannot receive more
					if (snd_nxt == rcv_nxt || len_ <= 0) {
						appAskedForData = len_ > 0;
						break;
					}
				}
				if(isDebugEnabled() || isDebugEnabledAt(TCPConstants.DEBUG_SEND))
					debug("snd_nxt = " + snd_nxt + " rcv_nxt = " + rcv_nxt);
				if (bufferFull_ && getAvailableReceivingBuffers() > 0)
					ack_syn_fin(false, -1000);
			}
		}
		catch (Exception e_) {
			if (e_ instanceof ClassCastException)
				error("dataArriveAtUpPort()", e_); 
			else
				e_.printStackTrace();
		}
	}	
	
	/** The down port follows the {@link drcl.inet.contract.PktDelivery}
	 * contract. */
	protected void dataArriveAtDownPort(Object data_,
					Port downPort_) 
	{
//		long start = System.nanoTime();
		try {
			peer = ((InetPacket)data_).getSource();
			TCPPacket tcpp = (TCPPacket)((InetPacket)data_).getBody();
			remotePort = tcpp.getSPort();
			recv(tcpp, tcpp.getPacketSize()-tcpp.getHeaderSize());
		}
		catch (Exception e_) {
			e_.printStackTrace();
			error(data_, "dataArriveAtDownPort()", downPort_,
							"unrecognized data? " + e_);
		}
		
	}
	
	/** Handles incoming packets. */
	protected void recv(TCPPacket pkt_, int bodySize)
	{
		long seq_, endseq_;
		seq_ = pkt_.getSeqNo();	// Get the seqence number
		if (seqNoPort._isEventExportEnabled())
			seqNoPort.exportEvent(TCP.SEQNO_EVENT,
							new DoubleObj((double)seq_/MSS), null);

		
		endseq_ = seq_ + bodySize; // exclusive
		boolean expected_ = false;
		boolean duplicate_ = false;
		// expected_: is the received segment expected (or out-of-order)?
		synchronized(rbuffer){
			expected_ = rcv_nxt == seq_
					|| seq_ < rcv_nxt && rcv_nxt < endseq_;
			duplicate_ = !expected_
					&& receivedSeq.contains(seq_, endseq_);

			// put pkt to the receiving buffer
			if (!duplicate_) {
				if(DRWA)
					this.data_rcvd = bodySize;
				receivedSeq.checkin(seq_, endseq_);
				long end_ = endseq_;
				if (end_ > snd_nxt + RBUFFER_SIZE) {
					// receiving buffer overflow, just discard the packet
					if(isDebugEnabled()) debug("receiving buffer overflow, just discard the packet");
					bodySize = 0;
					pkt_.free();
				}
				else {
					// insert the packet to rbuffer
					insertPacketToRecvBuffer(pkt_, seq_, end_);
					// update rcv_nxt:
					if (expected_) {
						if(DRWA){
							drwa_update(pkt_.getAckNo());
						}
						rcv_nxt = receivedSeq.getLongInterval(0).end;
					}
				}
			}else{
				// duplicate packet
				pkt_.free();
			}

			if (isDebugEnabled()) {
				if (isDebugEnabledAt(DEBUG_SAMPLE)
					|| isDebugEnabledAt(DEBUG_RECV)) {
					if (duplicate_)
						debug("RECEIVED: " + (seq_/MSS) + "/" + seq_ + ", "
							+ receivedSeq + ", DUPLICATE");
					else
						debug("RECEIVED: " + (seq_/MSS) + "/" + seq_ + ", "
							+ receivedSeq + "--rbuffer:" + printBuffer(true));
				}
				else if (!expected_ && !duplicate_ && (seq_ - rcv_nxt <= MSS)
					&& isDebugEnabledAt(DEBUG_OOO))
					debug("RECEIVED_OOO: " + (seq_/MSS) + "/" + seq_ + "+"
						+ (pkt_.size - pkt_.headerSize)
						+ ", expected " + rcv_nxt + "(" + (seq_ - rcv_nxt) + "), "
						+ receivedSeq + "--rbuffer:" + printBuffer(true));
			}
			
			if (!duplicate_) sendDataToApp();
		}
		

		// code below is to control how to send ACK
		if (expected_ && DelayACK) {
			// delay ACK
			if (ACKPending == null) {
				ACKPending = new TM_EVT(DELAY_ACK, delayTimer,
								pkt_.getTS());
				if(isDebugEnabled()) debug("setTimeout: ACKPending");
				setTimeout(ACKPending, ACKPending.timeout);
			}
		}
		else // ack right away
		{
			ack_syn_fin(SACK && !expected_ && !duplicate_, pkt_.getTS());
//				System.out.println(pkt_.getTS());
		}
		
	}

    private void insertPacketToRecvBuffer(TCPPacket pkt_, long seq_, long end_) {
    	boolean done_ = false;
		synchronized(rbuffer){
			for (int i=rbuffer.size()-1; i>=0; i--) {
				TCPPacket tmp_ = (TCPPacket)rbuffer.elementAt(i);
				long tmpseq_ = tmp_.getSeqNo();
				long tmpend_ = tmpseq_ + tmp_.size - tmp_.headerSize;
				if (tmpseq_ < seq_) {
					rbuffer.insertElementAt(pkt_, i+1);
					done_ = true;
					break;
				}
				else if (end_ >= tmpend_)
				{	
					// tmp_ is included in the received packet
					tmp_.free();
					rbuffer.removeElementAt(i);
				}
			}
			if (!done_)
				rbuffer.insertElementAt(pkt_, 0);
		}
	}

//	/** Processes TCP options in the packet header. */
//	protected void option_process(TCPPacket pkt_)
//	{
//	}

	/** Sends an acknowledgment packet. */
	protected void ack_syn_fin(boolean doSACK_, long aTS_)
	{
		TCPPacket pkt_;

		if (doSACK_)
			pkt_ = SACKHdr(aTS_); // check with SACK option 
		else {

			pkt_ = new TCPPacket(connection.getLocalPort(), 
				connection.getRemotePort(),
				getSeqNo(), rcv_nxt/*ackno*/, getAvailableReceivingBuffers()>>wind_scal,
				true/*ack*/, false/*syn*/, false/*fin*/, -1000/*TS*/, aTS_,
				NS_COMPATIBLE? 20: 32, 0, null);
			
		}
		if (isDebugEnabled() && (isDebugEnabledAt(DEBUG_SAMPLE)
				|| isDebugEnabledAt(DEBUG_SEND)))
			debug("SEND ACK: " + (pkt_.getAckNo()/MSS)
					+ "/" + pkt_.getAckNo() + " pkt:" + pkt_);
		// defined in Protocol.java  
		// void forward(PacketBody p_, long src_, long dest_, int dest_ulp_,
		// boolean routerAlert_, int TTL, int ToS): route-lookup forwarding 
		if(drcl.ruv.System.SYNC)
			syncForward(pkt_, getLocalAddr(), peer, DEFAULT_PID, false, TTL, 0);
		else
			forward(pkt_, getLocalAddr(), peer, DEFAULT_PID, false, TTL, 0);
	}

	private void drwa_update(long rtt) {
//		if(aTS_<0)
//			return;
//		// estimate rtt
//		double cur_rtt = now_ - aTS_;
//		
//		
//		rtt_sum += cur_rtt;
//		rtt_cnt ++;
//		rtt_est = rtt_sum/rtt_cnt;
//		System.out.println(rtt);
		if(rtt<0.05)
			return;
		rtt_est = rtt/1000.0;
		
		
		if(this.rtt_est < this.rtt_min){
			this.rtt_min = this.rtt_est;
		}
		
		if(this.cwnd_est==0){
			this.cwnd_est = this.data_rcvd;
		}
		
	}
	
	private double last_update;
	
	
	private void drwa(){
		if(rtt_est < 0.05)
			return;
		double current = getTime();
		double elapsed = current - last_update;
		if(elapsed < rtt_est)
			return;
//		if(rtt_cnt<1)
//			return;
		last_update = current;
		// update advertised window size
		this.cwnd_est = (alpha*this.cwnd_est+(8-alpha)*this.data_rcvd)/8;
		long rwnd = (long) (lambda*this.rtt_min*this.cwnd_est/this.rtt_est);
		if(rwnd > this.awnd_max)
			rwnd = this.awnd_max;
		RBUFFER_SIZE = (int) (rwnd + rcv_nxt - snd_nxt);
		System.out.println(elapsed);
		System.out.println(rwnd + " " + this.data_rcvd + " " + this.rtt_est);
		this.data_rcvd = 0;
	}

//	// expected_: the sequence # is expected to received?
//	private void SACK_process(long seq_, int size_, boolean expected_)
//	{
//		// the new block is either alone or triggers merging of several 
//		// previous blocks
//		LongInterval new_ = expected_? null:
//				new LongInterval(seq_, seq_ + size_);
//
//		synchronized(llSackBlock){
//			if (llSackBlock == null)
//				// create SACK block list
//				llSackBlock = new LinkedList<LongInterval>();
//			else{
//				for (ListIterator<LongInterval> li_ = llSackBlock.listIterator(0); li_.hasNext(); ) {
//					LongInterval interval_ = (LongInterval)li_.next();
//					if (receivedSeq.strictlyContains(interval_.start, interval_.end)) {
//						// this block is merged, so change new_ to the merged interval
//						if (new_ != null) {
//							if (interval_.start < new_.start)
//								new_.start = interval_.start;
//							else if (interval_.end > new_.end)
//								new_.end = interval_.end;
//						}
//						li_.remove();
//					}
//				}
//	
//			}
//			
//			if (new_ != null)
//				llSackBlock.addFirst(new_);
//
//			if (isDebugEnabled() && (isDebugEnabledAt(DEBUG_SAMPLE) 
//				&& llSackBlock != null && llSackBlock.size() > 0
//				|| isDebugEnabledAt(DEBUG_SACK))) {
//				String s_ = sackToString();
//				debug("SACK_BLOCK_CHANGE: Triggering seq=" + (seq_/MSS) + "/" 
//					+ seq_ + ", blocks="
//					+ (s_ == null || s_.length() == 0? "none": s_));
//			}
//		}
//		
//	}
	
	
	/** Makes a TCP header with SACK option. */
	protected TCPPacket SACKHdr(long aTS_)
	{
		TCPPacket pkt_ = null;
		long[] REblk_ = null;
		long[] LEblk_ = null;
		int sackLen_ = 0;
		synchronized(rbuffer){
			sackLen_ = receivedSeq.numOfLongIntervals() - 1;
			if(sackLen_ > 0){
				sackLen_ = sackLen_>3?3:sackLen_;
				REblk_ = new long[sackLen_];
				LEblk_ = new long[sackLen_];
				for(int i = 0; i < sackLen_; i++){
					LongInterval li = receivedSeq.getLongInterval(i+1);
					LEblk_[i] = li.start;
					REblk_[i] = li.end;
				}

			}
		}
		if(sackLen_ > 0 ){
			
			// header size = 20 bytes + 10 bytes of timestamp option + 2*8n 
			// bytes of sack option
			// n is # of blocks in sack option, n <= 3
			pkt_ = new TCPPacket(connection.getLocalPort(),
				connection.getRemotePort(),
				getSeqNo(), rcv_nxt/*ackno*/, getAvailableReceivingBuffers()>>wind_scal,
				true/*ack*/, false/*syn*/, false/*fin*/, -1000/*TS*/, aTS_,
				true, sackLen_, 32+4+8*sackLen_/*30+2+8*len_*/, 0, null);
			pkt_.setSACKBlocks(LEblk_, REblk_);
			
		}else{
			// header size = 20 bytes + 10 bytes of timestamp option
			pkt_ = new TCPPacket(connection.getLocalPort(), 
				connection.getRemotePort(),
				getSeqNo(), rcv_nxt/*ackno*/, getAvailableReceivingBuffers()>>wind_scal,
				true/*ack*/, false/*syn*/, false/*fin*/, -1000/*TS*/, aTS_,
				NS_COMPATIBLE? 20: 32, 0, null);
		}
		return pkt_;
		
	}
//	/** Makes a TCP header with SACK option. */
//	protected TCPPacket SACKHdr(long aTS_)
//	{
//		synchronized(llSackBlock){
//			TCPPacket pkt_;
//			int len_ = llSackBlock == null? 0: llSackBlock.size();
//
//			if (len_ > 3)
//				len_ = 3;
//				// TCP option field can hold up to 3 SACK blocks with timestamp
//				// option
//			if (len_ > 0) {
//				long[] REblk_ = new long[len_];
//				long[] LEblk_ = new long[len_];
//				int i = 0;
//				for (ListIterator<LongInterval> li_ = llSackBlock.listIterator(0);
//					li_.hasNext(); ) {
//					LongInterval interval_ = (LongInterval)li_.next();
//					LEblk_[i] = interval_.start;
//					REblk_[i] = interval_.end;
//					if (++i == len_) break;
//				}
//
//				// header size = 20 bytes + 10 bytes of timestamp option + 2*8n 
//				// bytes of sack option
//				// n is # of blocks in sack option, n <= 3
//				pkt_ = new TCPPacket(connection.getLocalPort(),
//					connection.getRemotePort(),
//					getSeqNo(), rcv_nxt/*ackno*/, getAvailableReceivingBuffers()>>wind_scal,
//					true/*ack*/, false/*syn*/, false/*fin*/, -1000/*TS*/, aTS_,
//					true, len_, 32+4+8*len_/*30+2+8*len_*/, 0, null);
//				pkt_.setSACKBlocks(LEblk_, REblk_);
//			}
//			else {
//				// header size = 20 bytes + 10 bytes of timestamp option
//				pkt_ = new TCPPacket(connection.getLocalPort(), 
//					connection.getRemotePort(),
//					getSeqNo(), rcv_nxt/*ackno*/, getAvailableReceivingBuffers()>>wind_scal,
//					true/*ack*/, false/*syn*/, false/*fin*/, -1000/*TS*/, aTS_,
//					NS_COMPATIBLE? 20: 32, 0, null);
//			}
//			return pkt_;
//		}
//		
//	}

	// the seqno of acks being sent out
	// in case of merging TCP and TCPSink, we will get seqno from TCP sending
	// side
	protected long getSeqNo()
	{ 
		
		return snd_nxt; 
	}

	/* fields and methods for connection */
	protected int remotePort, localPort;
	
	void setConnection(Connection conn_)
	{ connection = conn_; }

	public int getLocalPort()
	{ return localPort; }
	
	public int getRemotePort()
	{ return remotePort; }

	public long getLocalAddr()
	{ return drcl.net.Address.NULL_ADDR; }

	public long getPeer()
	{ return peer; }
	
	public String info()
	{
		String sb_ = null;
//		if (SACK && llSackBlock != null)
//			sb_ = sackToString();
		return "   State = " + STATES[state] + "\n"
		     + "    Peer = " + peer + "\n"
		     + "rcv buffer size = " + getAvailableReceivingBuffers() + "/"
			 	+ RBUFFER_SIZE + "\n"
			 + "    SACK = " + SACK + "\n"
			 + "DelayACK = " + DelayACK + (DelayACK? ", delay = "
			 	+ delayTimer + ", pending:"
				+ (ACKPending == null? "none": ""+ACKPending.timeout): "") 
			 	+ "\n"
			 + "receive_next = " + rcv_nxt/MSS + "---" + rcv_nxt + "\n"
			 + "   send_next = " + snd_nxt/MSS + "---" + snd_nxt + "\n"
			 + "  rcv buffer = " + printBuffer(true) + "---" 
			 	+ printBuffer(false) + "\n"
			 + " receivedSeq = " + receivedSeq + "\n"
			 + (SACK?  " SACK_blocks = " + (sb_ == null || sb_.length() == 0?
									 "none": sb_) + "\n": "");
	}

//	String sackToString()
//	{
//		StringBuffer sb_ = new StringBuffer();
//		if (llSackBlock != null) {
//			for (ListIterator<LongInterval> li_ = llSackBlock.listIterator(0);
//				li_.hasNext(); ) {
//				LongInterval interval_ = (LongInterval)li_.next();
//				sb_.append("[" + (interval_.start/MSS) + "/" + interval_.start
//					+ "," + (interval_.end/MSS) + "/" + interval_.end + ")");
//			}
//		}
//		return sb_.toString();
//	}

	String printBuffer(boolean mss_)
	{
		StringBuffer sb_ = new StringBuffer();
		long last_ = -1;
		for (int i=0; i<rbuffer.size(); i++) {
			TCPPacket tmp_ = (TCPPacket)rbuffer.elementAt(i);
			if (last_ < tmp_.getSeqNo()) {
				if (mss_) {
					if (last_ >= 0) sb_.append(last_/MSS + ")");
					sb_.append("(" + tmp_.getSeqNo()/MSS + ",");
				}
				else {
					if (last_ >= 0) sb_.append(last_ + ")");
					sb_.append("(" + tmp_.getSeqNo() + ",");
				}
			}
			last_ = tmp_.getSeqNo() + tmp_.size - tmp_.headerSize;
		}
		if (last_ >= 0) {
			if (mss_) sb_.append(last_/MSS + ")");
			else sb_.append(last_ + ")");
		}
		if (sb_.length() == 0) return "()";
		return sb_.toString();
	}

	class TM_EVT
	{
		int type;
		long timeout;
		long aTS;
		
		TM_EVT(int type_, long timeout_, long aTS_)
		{ type = type_; timeout = timeout_; aTS=aTS_; }

		public String toString()
		{ return TIMEOUT_TYPES[type]; }
	}

	@Override
	public void setLocalPort(int port) {
		this.localPort = port;
	}
}
