package drcl.inet.transport;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Random;

import drcl.comp.ACATimer;
import drcl.comp.Port;
import drcl.comp.lib.bytestream.ByteStreamContract;
import drcl.inet.InetPacket;
import drcl.inet.host.MobibedUtils;
import drcl.inet.transport.TCP;
import drcl.inet.transport.TCPConstants;
import drcl.inet.transport.TCPPacket;


import drcl.net.Address;
import drcl.util.CircularBuffer;

public class STCP extends drcl.inet.Protocol
	implements TCPConstants, Connection{
	
	/** Default advertisement window size (unit of MSS bytes). */
	public static int AWND_DEFAULT = 128;
//	/** Default maximum congestion window size (unit of MSS bytes). */
//	public static int MAXCWND_DEFAULT = 128;
	
	public static final String[] DEBUG_LEVELS =
		{"ack", "dupack", "send", "timeout", "rtt", "sack", "sample", "vegas"};
	public static final int DEBUG_ACK= 0;
	public static final int DEBUG_DUPACK= 1;
	public static final int DEBUG_SEND = 2;
	public static final int DEBUG_TIMEOUT = 3;
	public static final int DEBUG_RTT = 4;
	public static final int DEBUG_SACK = 5;
	public static final int DEBUG_SAMPLE = 6;
	public static final int DEBUG_VEGAS = 7;
	public String[] getDebugLevelNames()
	{ return DEBUG_LEVELS; }

	public String getName()
	{ return "tcp"; }

	private CircularBuffer sbuffer;
	
	@Override
	public void setMaxReceiveBufferSize(int size) {
		TCP.TCP_MAX_RMEM = size;
	}

	@Override
	public void setMaxSendBufferSize(int size) {
		TCP.TCP_MAX_WMEM = size;
		
	}
	
	/** Maximum seqence number of data in the buffer,
	 * = snd_una + sbuffer.size(). */
	transient protected long dt_max;
	/** MSS Maximum segmentation size. */
	protected int MSS = 512;
	/** Smallest sequence number that has not yet been acknowledged. */
	transient protected long snd_una;
	/** Sequence number to send next. */
	transient protected long snd_nxt;
	/** Maximum seqence number that has been sent. */
	transient protected long snd_max;
	transient protected int AWND = 512;
	private long peer = Address.NULL_ADDR; // where this connection is destined
	/** # of bytes that have been sent in response to last ACK. */
	transient protected int burst = 0;
	private int localPort, remotePort;
	transient TM_EVT timeoutEvent = null;
	/** Number of duplicated ACKs received. */
	transient protected int dup_ack;
	/** Congestion window size. */
	transient protected int  CWND;
	// true if have notified application of available buffers
	transient boolean notifiedApplication = false;
	/** FSM State of this TCP component. */
	transient public int state = CLOSED;
	/** APP sent done */
	boolean app_closed = false;
	public STCP()
	{super();}
	
	public STCP(String id_)
	{
		super(id_);
	}
	
	public void reset()
	{
		super.reset();
		state = CLOSED;
		notifiedApplication = false;
		app_closed = false;
		stcp_init();
	}
	
	{stcp_init();}
	
	protected void stcp_init()
	{
		win_init();
		sbuffer = new CircularBuffer(AWND + MSS);
	}
	
	/**
	 * Initializes sliding window variables. 
	 * Congestion window and slow start threshold is set as in [TCPILL2].
	 */
	protected void win_init() {
		CWND = MSS;
		AWND = AWND_DEFAULT*MSS;
//		MAXCWND = MAXCWND_DEFAULT*MSS;
//		sthld = INIT_SS_THRESHOLD*MSS; 
		snd_una = 0;
		snd_nxt = 0;
		snd_max = 0;
		dt_max = 0;
		dup_ack = 0;
		burst = 0;
//		last_seq = 0;
//		phase = SLOW_START;
	}
	
	protected void dataArriveAtUpPort(Object data_, Port upPort_)
	{
		try{
			synchronized(sbuffer) {
				ByteStreamContract.Message msg_ =
						(ByteStreamContract.Message) data_;
				if (msg_.isSend()) {
					int len_ = sbuffer.append(msg_.getByteArray(),
							msg_.getOffset(), msg_.getLength());
					if(len_ > 0) {
						dt_max += len_;
						if(state == SEND){
							// Send the whole window of bytes
							snd_maxpck("dataArriveAtUpPort()");
							if (timeoutEvent == null)
//								//|| Double.isNaN(timeoutEvent.timeout))
								resetRXTTimer(getTime());
						}else{
							// try to SYN with the server
							snd_packet(0,0,false/*ack*/, true/*syn*/, false/*fin*/);
							state = SYN_SENT;
							if (timeoutEvent == null)
//								//|| Double.isNaN(timeoutEvent.timeout))
								resetRXTTimer(getTime());
						}
					}
					// return available buffer space
					if (len_ == msg_.getLength())
						upPort_.doLastSending(
									new Integer(sbuffer.getAvailableSpace()));
					else
						upPort_.doLastSending(
									new Integer(len_ - msg_.getLength()));
				}
				else if (msg_.isQuery()) {
					upPort_.doLastSending(
								new Integer(sbuffer.getAvailableSpace()));
				}
				else if (msg_.isStop()){
					app_closed = true;
					
				}
				notifiedApplication = false;
			}
		}
		catch (Exception e_) {
			e_.printStackTrace();
			error(data_, "dataArriveAtUpPort()", upPort_, e_); 
		}
	}
	

	/** Resets the retransmission timer. */
	protected void resetRXTTimer(long now_)
	{
//		if(timeoutEvent == null){
			if(snd_una < dt_max){
				long time_ = now_ + 200; //ms
				timeoutEvent = new TM_EVT(RXT_EVT, time_);
				System.out.println(time_);
				timeoutEvent.timer = setTimeoutAt(timeoutEvent, time_);
			}
//		}
	}
	
	protected synchronized void timeout(Object evt_)
	{
		switch(((TM_EVT)evt_).type) {
		case RXT_EVT:
			// Check if timeout was being reset or cancelled
			if (timeoutEvent != evt_) {
//				debug("OOPS, evt_=" + evt_ + ", timeoutEvent="
//					+ timeoutEvent);
				return;
			}
			// XX: rounding error if in real-time simulation?
			long now_ = getTime();
			if (timeoutEvent.timeout > now_) {
				timeoutEvent.timer = setTimeoutAt(timeoutEvent,
								timeoutEvent.timeout);
				return;
			}
			switch(state){
			case SEND:
				snd_nxt = snd_una;
				snd_maxpck("TIMEOUT");
				timeoutEvent.timer = null;
				resetRXTTimer(now_);
				break;
			case SYN_SENT:
				// timeout when listening to syn ack
				upPort.doSending(new ByteStreamContract.Message(
						ByteStreamContract.ERROR));
				state = CLOSED;
				error(evt_, "timeout", null, "SYN ACK timeout");
				break;
			case ESTABLISHED:
				state = SEND;
				snd_maxpck("first sending packet");
				break;
			case FIN_WAIT_1:
				// timeout when listening to ack
				// close the connection anyway
				state = CLOSED;
				if(!notifiedApplication){
					// TODO notify the app the connection is closed.
				}
			}
		}
	}
	
	/** Sends as many packets as allowed by the current window. */
	protected void snd_maxpck(String debugMsg_)
	{
		
		if (snd_nxt < snd_una) snd_nxt = snd_una;
		long dtlen_ = seq_max() - snd_nxt;

		if (dtlen_ <= 0) return;
		
		if (isDebugEnabled()
				&& isDebugEnabledAt(DEBUG_SEND))
				debug(debugMsg_ + ":Gonna send " + (snd_nxt/MSS) + " ---> "
					+ (seq_max()/MSS) + "; " + win_info()
					+ ", sending_buffer:" + sbuffer);
		
		while(dtlen_ > 0){
			if(dtlen_ >= MSS){
				snd_packet(snd_nxt, MSS,false/*ack*/, false/*syn*/, false/*fin*/);
				snd_nxt += MSS;
				dtlen_ -= MSS;
			}
			else{
				// send fragment only when no new data buffered
				if (dt_max == snd_nxt + dtlen_) {
					snd_packet(snd_nxt, (int)dtlen_,false/*ack*/, false/*syn*/, false/*fin*/);
					snd_nxt += dtlen_;
				}
				return;
			}
		}
	}
	public int getLocalPort()
	{ 
		return localPort; 
	}
	
	public int getRemotePort()
	{ return remotePort; }
	
	protected long getAckNo()
	{ return 0; }
	
	protected int getAvailableRcvBuffer()
	{ return 0; }
	
	public long getLocalAddr()
	{ 
		try {
			return MobibedUtils.byteArrayToLong(InetAddress.getLocalHost().getAddress());
		} catch (UnknownHostException e) {
			e.printStackTrace();
		}
		return drcl.net.Address.NULL_ADDR; 	
	}
	
	/** Sends one packet with specified starting sequence number and
	 * packet size. */	
	protected void snd_packet(long seqno_, int size_, boolean ack,
			boolean syn, boolean fin)
	{
		long now_ = getTime();
		
		// the payload_ is only "read" out of the buffer, not removed.
		// will be removed when acked
		byte[] payload_ = sbuffer.read((int)(seqno_ - snd_una), size_);
		boolean rxt_ = seqno_ < snd_max;
		if (snd_max < seqno_ + size_) snd_max = seqno_ + size_;
		// Construct a packet to send
		// header size = 20 bytes + 10 bytes of timestamp option
		// don't care port numbers
		TCPPacket pkt_ = new TCPPacket(getLocalPort(), getRemotePort(),
			seqno_, getAckNo()/*ackno*/, getAvailableRcvBuffer()/*advwin*/,
			ack/*ack*/, syn/*syn*/, fin/*fin*/,
			now_, //rxt_? -1.0: now_/*TS*/, // FIXME: why -1 for rxt pkt?
			-1000/*aTS*/, 20, size_, payload_);
		forward(pkt_, getLocalAddr(), peer, TCP.DEFAULT_PID, false, 128/*ttl*/, 0);
		burst += size_;
	}
	
	/**
	 * Returns the size of usable window, which is the smaller 
	 * value of congestion window and window size advertized by peer.
	 */
	protected int snd_wnd()
	{
		if (CWND<AWND)
			return CWND / MSS * MSS;
		else
			return AWND;
	}

	/** Returns the maximum sequence number that can be sent. */
	protected long seq_max()
	{
		return Math.min(snd_una + snd_wnd(), dt_max);
			//((snd_max<dt_max)?snd_max:dt_max);
	}
	
	protected void dataArriveAtDownPort(Object data_, Port downPort_)
	{
		try{
			synchronized(sbuffer){
				TCPPacket tcppkt_ = (TCPPacket)((InetPacket)data_).getBody();
				if (!tcppkt_.isACK()) {
					error(data_, "recv()", downPort, "pkt is not an ack");
					return;
				}
				if(tcppkt_.isSYN() && state == SYN_SENT)
					recvSYNACK(tcppkt_);
				else
				{
					if(state == FIN_WAIT_1)
					{
						// close the connection anyway
						state = CLOSED;
						if(isDebugEnabled())
							debug("connection is closed.");
						if(!notifiedApplication){
							// TODO notify the app the connection is closed.
						}
					}
					else
					{
						recv(tcppkt_);
						if(app_closed && sbuffer.getSize()==0)
						{
							// terminate the connection
							// send fin
							this.snd_packet(snd_nxt, 0, true, false, true);
							state = FIN_WAIT_1;
							resetRXTTimer(getTime());
						}
					}
				}
			}
		}
		catch (Exception e_) {
			e_.printStackTrace();
			error(data_, "dataArriveAtDownPort()", downPort_,
							"unrecognized data: " + e_);
		}
	}
	
	protected void recvSYNACK(TCPPacket tcppkt_) {
		// TODO Auto-generated method stub
		AWND = tcppkt_.getAdvWin();
//		if (!notifiedApplication && snd_una + snd_wnd() + MSS >= dt_max) {
//			notifiedApplication = true;
//			upPort.doSending(new ByteStreamContract.Message(
//							ByteStreamContract.REPORT,
//							sbuffer.getAvailableSpace()));
//		}
		if (isDebugEnabled()){
			debug("SYN ACK received.");
		}
		snd_maxpck("NEW_ACK"); // Send packet
		state = ESTABLISHED;
		resetRXTTimer(getTime()); // wait for sending packets
	}

	/** Handles a packet arriving at the down port. */
	protected void recv(TCPPacket pkt_)
	{
		AWND = pkt_.getAdvWin(); // Get advertised window
		long ackseq_ = pkt_.getAckNo();
		if (ackseq_ < snd_una) // old acknowledgement, ignored
			return;
		long now_ = getTime();
		if (ackseq_ > snd_una) { // Not a duplicated ACK
			long advanced_ =  ackseq_ - snd_una;
			sbuffer.remove(null, 0, (int)advanced_);
			snd_una = ackseq_;	
			if (!notifiedApplication && snd_una + snd_wnd() + MSS >= dt_max) {
				notifiedApplication = true;
				upPort.doSending(new ByteStreamContract.Message(
								ByteStreamContract.REPORT,
								sbuffer.getAvailableSpace()));
			}
			if (isDebugEnabled()){
				debug("ACK: " + (ackseq_/MSS) + "/" + ackseq_ + ", "
						+ win_info());
			}
			snd_maxpck("NEW_ACK"); // Send packet
			this.resetRXTTimer(now_);
		}else{ // Duplicated ACK
			++dup_ack;
			if (isDebugEnabled()){
				debug("DUPACK " + dup_ack + ": " + (ackseq_/MSS));		
			}
		}
	}
	
	String win_info()
	{
		long max_ = seq_max();
		return "window=(" + (snd_una/MSS) + "," + (snd_nxt/MSS) + ","
				+ (max_/MSS) + ",progress:"
				+ ((snd_nxt - snd_una)/MSS) + "/" + ((max_ - snd_una)/MSS)
				+ ",CWMD:" + (CWND/MSS) + ")"
				+ "(" + snd_una + "," + snd_nxt  + "," + max_ + ",progress:"
				+ (snd_nxt - snd_una) + "/" + (max_ - snd_una)
				+ ",CWND:" + CWND + ")";
	}
	
	// Timeout event class. It is used to hold the necessary information
	// for a scheduled timeout. 
	class TM_EVT
	{
		int type;   // Timeout type
		long timeout; // time of timeout
		ACATimer timer;
		
		public TM_EVT(int type_, long timeout_)
		{
			type = type_;
			timeout = timeout_;
		}
	}
	
	// fields and methods for real Internet transmission
	
	
	/**
	 * set remote peer on real Internet transmission
	 * @param ip
	 * @param port
	 */
	public void setPeer(String ip, int port)
	{
		try {
			this.peer = MobibedUtils.byteArrayToLong(
					InetAddress.getByName(ip).getAddress());
			this.remotePort = port;
			
		} catch (UnknownHostException e) {
			error(ip, "dataArriveAtUpPort()", null, e); 
			e.printStackTrace();
		}
		
	}

	@Override
	public long getPeer() {
		// TODO Auto-generated method stub
		return this.peer;
	}

	@Override
	public void setLocalPort(int port) {
		this.localPort = port;
	}
}
