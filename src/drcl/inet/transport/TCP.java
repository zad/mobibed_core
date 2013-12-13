// @(#)TCP.java   7/2006
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

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Hashtable;
import java.util.Enumeration;
import java.util.Iterator;
import drcl.data.DoubleObj;
import drcl.comp.*;
import drcl.comp.lib.bytestream.ByteStreamContract;
import drcl.util.CircularBuffer;
import drcl.util.TimeLog;
import drcl.util.scalar.LongSpace;
import drcl.util.scalar.LongInterval;
import drcl.inet.InetPacket;
import drcl.inet.host.MobibedUtils;
import drcl.net.Address;

// dataArriveAtUpPort() -> snd_maxpck() -> snd_packet()
//                      -> schedule timeout if necessary -> forward()
// dataArriveAtDownPort() -> recv() acks -> readjust window & timeout -> snd_maxpck()
//                        or -> dupack
// timeout -> timeout(), readjust window & schedule next timeout

/**
 * This component implements the single-session sender TCP.
 * 
 * Only one TCP session is handled in this component. Open and close of the
 * connection is not implemented, nor is the 3-way handshaking.
 * 
 * <p>
 * Several variants of TCP are implemented: Tahoe, Reno, New-Reno ([Fall96]) and
 * Vegas ([Brakmo95]). Each can be used with the SACK option. Currently only
 * New-Reno takes advantage of the SACK blocks in an ACK.
 * 
 * To specify the implementation, use {@link #setImplementation(String)} with
 * "Tahoe", "Reno", "New-Reno" or "Vegas" as argument. To enable SACK, use
 * {@link #setSackEnabled(boolean)}.
 * 
 * <p>
 * The initial sequence number is set as 0, instead of a random value suggestted
 * in [TCPILL2]
 * 
 * <p>
 * For Tahoe and Reno, the RTT and RXT timer base estimation is based on
 * [TCPILL2]. The sending time of a segment is recorded as <code>ts_</code> in
 * the TCP header (similar to the timestamp option in the real TCP header) and
 * returned by the corresponding acknowledgment packet. <code>ts_</code> is set
 * to -1 if the segment is a retransmitted one, so no RTT estimation is
 * performed for retransmitted segments. The algorithm is described in [RFC793]
 * and [TCPILL1].
 * 
 * <pre>
 * 	SampleRTT = current time - ts_;
 * 	SmoothedRTT[new] = 7/8*SmoothedRTT[old] + 1/8*SampleRTT;
 * 	Delta = |SampleRTT - SmoothedRTT[old]|;
 * 	RTTVariance[new] = 3/4*RTTVariance[old] + 1/4*Delta;
 * 	and the retransmission timer base is 
 * 	RTO[new] = SmoothedRTT[new] + 4*RTTVariance[new].
 * </pre>
 * 
 * The exception is that for the first acknowledgment received:
 * 
 * <pre>
 * 	SampleRTT = current time - ts_;
 * 	SmoothedRTT[new] = SampleRTT;
 * 	RTTVariance[new] = SampleRTT/2;
 * 	and the retransmission timer base is 
 * 	RTO[new] = SmoothedRTT[new] + 4*RTTVariance[new].
 * </pre>
 * 
 * <p>
 * Additional usage infomation:
 * <ol>
 * <li>The "maxburst" option, use {@link #setMaxburstEnabled(boolean)}. This
 * option restricts the maximum number of bytes that can be sent in response to
 * a single ACK to be one segment during Reno-style fast recovery, and two
 * segments otherwise. This is based on [RFC2018] and is different from similar
 * setting in [Fall96].
 * 
 * For Vegas, only two segments restriction applies since Vegas does not have
 * fast recovery.
 * <li>To change TTL value, use {@link #setTTL(int)}.
 * <li>To change MSS value, use {@link #setMSS(int)}.
 * <li>To change timer tick, use {@link #setTick(double)}.
 * </ol>
 * 
 * <p>
 * References:
 * <ul>
 * <li>[TCPILL1] W. Stevens, TCP/IP Illustrated vol.1: The Protocols,
 * Addison-Wesley,1994.
 * <li>[TCPILL2] W. Stevens, TCP/IP Illustrated vol.2: The Implementation,
 * Addison-Wesley,1994.
 * <li>[TCPILL2] G. Wright and W. Stevens, TCP/IP Illustrated vol.2: The
 * Implementation, Addison-Wesley,1995
 * <li>[RFC793] J. Postel, Transmission Control Protocol, September 1981.
 * <li>[RFC2018] M. Mathis, J. Mahdavi, S. Floyd and A. Romanow, TCP Selective
 * Acknowledgment Options, Octobor 1996.
 * <li>[RFC2581] M. Allman, V. Paxson and W. Stevens, TCP Congestion Control,
 * April 1999.
 * <li>[RFC2988] V. Paxson and M. Allman, Computing TCP's Retransmission Timer,
 * November 2000.
 * <li>[Fall96] K. Fall and S. Floyd, Simulation-based Comparisons of Tahoe,
 * Reno and SACK TCP, Computer Comm. Review, V. 26 N. 3, July 1996, pp. 5-21.
 * <li>[Brakmo95] L. S. Brakmo and L. L. Peterson, TCP Vegas: End to End
 * Congestion Avoidance on a Global Internet, IEEE Journal on Selected Areas in
 * Communication, Vol 13, No. 8 (October 1995) pages 1465-1480.
 * </ul>
 * 
 * @see TCPPacket
 * @see TCPSink
 * @author Yuan Gao, Yung-ching Hsiao, Hung-ying Tyan
 */
public class TCP extends drcl.inet.Protocol implements TCPConstants,
		Connection, ActiveComponent {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	// {setDebugEnabled(true);setDebugEnabledAt(true, DEBUG_SAMPLE);}
	/** Sets to true to make TCP ns compatible */
	public static boolean NS_COMPATIBLE = false;

	/** Default advertisement window size (unit of MSS bytes). */
	public static int AWND_DEFAULT = 128;
	/** Default maximum congestion window size (unit of MSS bytes). */
	public static int MAXCWND_DEFAULT = Integer.MAX_VALUE;
	/** Initial slow-start threshold (unit of MSS bytes). */
	public int INIT_SS_THRESHOLD = Integer.MAX_VALUE; // #define
														// TCP_INFINITE_SSTHRESH
														// 0x7fffffff
	
	public static int TCP_MAX_RMEM = 1024*1024;
	public static int TCP_MAX_WMEM = 1024*1024;
	public int wmem;
	public int rmem;
	@Override
	public void setMaxReceiveBufferSize(int size) {
		TCP.TCP_MAX_RMEM = size;
	}

	@Override
	public void setMaxSendBufferSize(int size) {
		TCP.TCP_MAX_WMEM = size;
		
	}
	/**
	 * Number of duplicate ACKs to trigger fast RXT as specified in [RFC2581]
	 * (for Reno, Tahoe).
	 */
	public static int NUMDUPACKS = 3;

	static {
		setContract(TCP.class, "*@" + drcl.net.Module.PortGroup_UP,
				new drcl.comp.lib.bytestream.ByteStreamContract(
						Contract.Role_REACTOR));
	}



	public String[] getDebugLevelNames() {
		return DEBUG_LEVELS;
	}

	public String getName() {
		return "tcp";
	}

	/** Port to export the congestion window changed event. */
	protected Port cwndPort = addEventPort(CWND_PORT_ID);
	/** Port to export the slow start threshold changed event. */
	protected Port sstPort = addEventPort(SST_PORT_ID);
	/** Port to export the SRTT event. */
	protected Port srttPort = addEventPort(SRTT_PORT_ID);
	/** Port to export the RTTVAR event. */
	protected Port rttvarPort = addEventPort(RTTVAR_PORT_ID);
	/** Port to export the [first] sequence number of each packet sent. */
	protected Port seqNoPort = addEventPort(SEQNO_PORT_ID);
	
	protected Port awndPort = addEventPort(AWND_PORT_ID);
	/**
	 * Port to export the acknowledged sequence number of each packet received.
	 */
	protected Port ackPort = addEventPort(ACK_PORT_ID);
	protected Port cwndCntPort = addEventPort(CWND_CNT_PORT_ID);
	protected long peer = Address.NULL_ADDR; // where this connection is
												// destined
	protected int localPort;

	protected int remotePort;

	/**
	 * window scale
	 */
	public static int wind_scal = 2;

	/*
	 * RTT esimation and Rxt timer backoff (in second)
	 */
	/** Initial value of smoothed RTT (shifted by {@link #srtt_bits}) */
	protected int srtt_init = 0;
	/** Initial value of RTT Variation (shifted by {@link #rttvar_bits}) */
	protected int rttvar_init = 12;
	/** Initial value of base RXT timer. (seconds) */
	protected long rtxcur_init = 3; // according to RFC2988
	// protected double rtxcur_init = 6.0;

	/**
	 * TCP tick for all the rtt variables. The tick value is set as 100 ms (0.1
	 * second) as the fast timer in [TCPILL2].
	 */
	protected double t_grain = 0.1;
	/** Smoothed RTT (shifted by {@link #srtt_bits}) */
	transient protected int t_srtt;
	/** RTT Variation (shifted by {@link #rttvar_bits}) */
	transient protected int t_rttvar;
	/** Current RXT timeout value. */
	transient protected long t_rtxcur;
	/** Current RXT timer backoff value. (second)*/
	transient protected int backoff = 1; 
	/** Exponent of weight for updating {@link #t_srtt}. */
	protected int srtt_bits = 3;
	/** Exponent of weight for updating {@link #t_rttvar}. */
	protected int rttvar_bits = 2;
	/** Exponent of multiple for {@link #t_rtxcur}. */
	protected int rttvar_exp = 2;
	/** Maximum value of a RXT timeout. */
	protected long maxrto = 64; // recommended in [TCPILL2]
	/**
	 * Maximum backoff value allowed. Retransmission stops when this maximum is
	 * reached. Specify negative value to retransmit forever until succeeded.
	 */
	protected int maxBackoff = -1;

	/** Most recent RTT value. */
	transient protected double cur_rtt = 0;

	static final int SLOW_START = 0;
	public static final int CONGESTION_AVOIDANCE = 1;
	public static final int FAST_RECOVERY = 2;
	public int phase = SLOW_START;
	public static final String[] PHASES = { "SLOW_START",
			"CONGESTION_AVOIDANCE", "FAST_RECOVERY" };

	private static final int VEGAS_INCREASE = 0;
	private static final int VEGAS_DECREASE = 1;
	private static final int VEGAS_NOCHANGE = 2;
	private static final int VEGAS_CWND_SLOW_START = 2;
	private static final String[] VEGAS_ADJUST = { "INCREASE", "DECREASE",
			"NOCHANGE" };

	private static final int TCP_INIT_CWND = 3;

	private static final long SYN_ACK_TIMEOUT = 5000; // ms

	// for VEGAS
	private static int alpha = 1;
	private static int beta = 3;

	/** Stores variables for implementing TCP-VEGAS. */
	public static class VegasVariables implements java.io.Serializable {
		public VegasVariables() {
		}

		int windowAdjust = VEGAS_INCREASE;
		double base_rtt = 0.0; // Minimum RTT valus seen
		int numack = -1; // # of non-duplicate acks after a retransmission
		int numRetx = 0; // # of retransmissions
		Hashtable htSentTime = new Hashtable();
		long seqMarked = 0; // seq# of a bit sent, marked as the beginning
							// of a (RTT) measure period;
							// the period ends when the seq# is acked
		double periodBegin = Double.NaN; // begin of the measure period
		long bytesSent = 0; // bytes sent during the measure period
	}

	// congestion avoidance algorithm
	private static TCPCong ca;

	// for cubic
	public int snd_cwnd_cnt = 0;

	transient protected VegasVariables vegas = null;

	/** TTL field to IP. */
	protected int TTL = 255;
	/** MSS Maximum segmentation size. */
	protected int MSS = 512;

	protected String implementation = RENO;

	/** Congestion window size. */
	transient protected int CWND;
	/** Maximum congestion window size. */
	protected int MAXCWND = MAXCWND_DEFAULT;
	/** Receiver window size. */
	transient protected int AWND;
	/** Congestion avoidance threshold. */
	transient protected int sthld;
	transient protected int last_cwnd;
	/** Smallest sequence number that has not yet been acknowledged. */
	transient protected long snd_una;
	/** Sequence number to send next. */
	transient protected long snd_nxt;
	/** during Reno-style fast recovery. */
	transient protected long snd_nxt_recorded;
	/** {@link #snd_nxt} marked for NEW-RENO and SACK. */
	transient protected long snd_nxt_marked;
	/** Maximum seqence number that has been sent. */
	transient protected long snd_max;
	/**
	 * Maximum seqence number of data in the buffer, = snd_una + sbuffer.size().
	 */
	transient protected long dt_max;
	/** Number of duplicated ACKs received. */
	transient protected int dup_ack;
	/** # of effective acks received. */
	transient protected long numack = 0;
	/** Sending buffer. */
	transient protected CircularBuffer sbuffer;
	/** # of bytes retransmitted. */
	transient protected long bytesRetx = 0;
	/** # of coarse-grained timeouts. */
	transient protected int numTimeouts = 0;
	/** # of bytes that have been sent in response to last ACK. */
	transient protected int burst = 0;
	// true if have notified application of available buffers
	transient boolean notifiedApplication = false;
	// last sequence # to increase window, should be multiples of MSS
	transient protected long last_seq;

	/** True if SACK option is enabled. Default is false. */
	protected boolean SACK = false; // SACK flag
	/** True if maxburst restriction is enabled. Default is true. */
	protected boolean maxburst = false;
	/** (Reconstructed) Sequence numbers that the receiver has received. */
	transient protected LongSpace recvBuffer;

	/** FSM State of this TCP component. */
	transient public int state = CLOSED;

	protected boolean isAppStopped = false;

	protected int retrans_out, packets_out, lost_out;
	
	private boolean initialized = false;


	public TCP() {
		super();
//		if(!initialized)_start();
	}

	public TCP(String id_) {
		super(id_);
//		if(!initialized)_start();
	}

	public void reset() {
		super.reset();
		state = CLOSED;
		notifiedApplication = false;
		tcp_init();
	}

	public void duplicate(Object source_) {
		super.duplicate(source_);
		TCP that_ = (TCP) source_;
		TTL = that_.TTL;
		MSS = that_.MSS;
		t_grain = that_.t_grain;
		implementation = that_.implementation;
	}

	/** Used to output initial states events. */
	protected void _start() {
		if (cwndPort._isEventExportEnabled())
			cwndPort.exportEvent(CWND_EVENT,
					new DoubleObj((double) CWND / MSS), null);
		if (cwndCntPort._isEventExportEnabled())
			cwndCntPort.exportEvent(CWND_CNT_EVENT, new DoubleObj(
					(double) this.snd_cwnd_cnt), null);
		if (sstPort._isEventExportEnabled())
			sstPort.exportEvent(SST_EVENT, new DoubleObj((double) sthld / MSS),
					null);
		if (srttPort._isEventExportEnabled())
			srttPort.exportEvent(SRTT_EVENT, new DoubleObj(
					(t_srtt >> srtt_bits) * t_grain), null);
		tcp_init();
		if(isDebugEnabled()) debug("tcp is started.");
	}

	public int getState() {
		return state;
	}

	public void setPeer(long peer_) {
		peer = peer_;
	}

	public long getPeer() {
		return peer;
	}

	public void setTTL(int ttl) {
		TTL = ttl;
	}

	public int getTTL() {
		return TTL;
	}

	/**
	 * Sets the maximum segment size. This method initializes this TCP
	 * component. Should not be called during simulation.
	 */
	public void setMSS(int mss) {
		if (MSS != mss) {
			MAXCWND = MAXCWND / MSS * mss;
			CWND = CWND / MSS * mss;
			sthld = sthld / MSS * mss;
			AWND = AWND / MSS * mss;
			MSS = mss;
		}
	}

	public int getMSS() {
		return MSS;
	}

	public void setMAXCWND(int max_) {
		MAXCWND = max_;
	}

	public int getMAXCWND() {
		return MAXCWND;
	}

	/** Sets the clock tick for RTT estimation. */
	public void setTick(double tick_) {
		srtt_init = (int) ((srtt_init >> srtt_bits) * t_grain / tick_) << srtt_bits;
		rttvar_init = (int) ((rttvar_init >> rttvar_bits) * t_grain / tick_) << rttvar_bits;
		t_grain = tick_;
	}

	public double getTick() {
		return t_grain;
	}

	/** Sets the initial value of srtt (in second). */
	public void setSRTT_INIT(double v_) {
		srtt_init = (int) (v_ / t_grain) << srtt_bits;
	}

	/** Returns the initial value of srtt (in second). */
	public double getSRTT_INIT() {
		return (srtt_init >> srtt_bits) * t_grain;
	}

	/** Sets the initial value of rttvar (in second). */
	public void setRTTVAR_INIT(double v_) {
		rttvar_init = (int) (v_ / t_grain) << rttvar_bits;
	}

	/** Returns the initial value of rttvar (in second). */
	public double getRTTVAR_INIT() {
		return (rttvar_init >> rttvar_bits) * t_grain;
	}

	/** Sets the initial value of baseRTT (in second). */
	public void setBASERTT_INIT(long v_) {
		rtxcur_init = v_;
	}

	/** Returns the initial value of baseRTT (in second). */
	public double getBASERTT_INIT() {
		return rtxcur_init;
	}

	/** Sets the maximum retr timeout (in second). */
	public void setMAXRTO(long v_) {
		maxrto = v_;
	}

	/** Returns the maximum retr timeout (in second). */
	public double getMAXRTO() {
		return maxrto;
	}

	/** Returns # of bytes retransmitted. */
	public long getNumBytesRetransmitted() {
		return bytesRetx;
	}

	/** Returns # of coarse-grained timeouts. */
	public int getNumTimeouts() {
		return numTimeouts;
	}

	/**
	 * Configures RTT parameters.
	 * 
	 * @param tick_
	 *            time unit for RTT variables.
	 * @param srtt_init_
	 *            initial value of srtt.
	 * @param rttvar_init_
	 *            initial value of rttvar.
	 * @param basertt_init_
	 *            initial value of baseRTT.
	 * @param maxrto_
	 *            maximum retrx timeout.
	 */
	public void configureRTT(double tick_, double srtt_init_,
			double rttvar_init_, long basertt_init_, long maxrto_) {
		if (tick_ < 0.0)
			tick_ = t_grain;

		if (srtt_init_ >= 0.0)
			srtt_init = (int) (srtt_init_ / tick_) << srtt_bits;
		else
			srtt_init = (int) ((srtt_init >> srtt_bits) * t_grain / tick_) << srtt_bits;

		if (rttvar_init_ >= 0.0)
			rttvar_init = (int) (rttvar_init_ / tick_) << rttvar_bits;
		else
			rttvar_init = (int) ((rttvar_init >> rttvar_bits) * t_grain / tick_) << rttvar_bits;

		if (basertt_init_ >= 0.0)
			rtxcur_init = basertt_init_;

		if (maxrto_ > 0)
			maxrto = maxrto_;

		t_grain = tick_;
	}

	public void setImplementation(String impl_) {
		String imp_ = impl_.toUpperCase();
		vegas = null;
		CWND = MSS;
		if (imp_.equals(RENO))
			implementation = RENO;
		else if (imp_.equals(NEW_RENO)){
			implementation = NEW_RENO;
		}
		else if (imp_.equals(TAHOE))
			implementation = TAHOE;
		else if (imp_.equals(VEGAS)) {
			implementation = VEGAS;
			
		} else if (imp_.equals(CUBIC)) {
			implementation = CUBIC;
			

		} else
			drcl.Debug.error("Unrecognized implementation: " + impl_);
		
	}
	
	public void initImplementation() {
		String imp_ = implementation;
		if (imp_.equals(NEW_RENO)){
			CWND = TCP_INIT_CWND * MSS;
		}
		else if (imp_.equals(VEGAS)) {
			vegas = new VegasVariables();
			CWND = VEGAS_CWND_SLOW_START * MSS;
		} else if (imp_.equals(CUBIC)) {
			NUMDUPACKS = 3;
			// initialize CUBIC
			ca = new TCPCubic();
			ca.register();
			CWND = TCP_INIT_CWND * MSS;

		} 
		if (cwndPort._isEventExportEnabled())
			cwndPort.exportEvent(CWND_EVENT,
					new DoubleObj((double) CWND / MSS), null);
		if (cwndCntPort._isEventExportEnabled())
			cwndCntPort.exportEvent(CWND_CNT_EVENT, new DoubleObj(
					(double) this.snd_cwnd_cnt), null);
		if (sstPort._isEventExportEnabled())
			sstPort.exportEvent(SST_EVENT, new DoubleObj((double) sthld / MSS),
					null);
	}

	public String getImplementation() {
		return implementation;
	}

	public void setSackEnabled(boolean sack_) {
		SACK = sack_;
	}

	public boolean isSackEnabled() {
		return SACK;
	}

	public void setMaxburstEnabled(boolean maxburst_) {
		maxburst = maxburst_;
	}

	public boolean isMaxburstEnabled() {
		return maxburst;
	}

	/** Initializes this TCP component. */
	protected void tcp_init() {
		rtt_init();
		win_init();
		initImplementation();
		this.wmem = TCP.TCP_MAX_WMEM;
		sbuffer = new CircularBuffer(wmem);
		recvBuffer = null;
		timeoutEvent = null;
		numack = 0;
		bytesRetx = 0;
		numTimeouts = 0;

		if (vegas != null) {
			vegas.htSentTime.clear();
			vegas.windowAdjust = VEGAS_INCREASE;
			vegas.numack = -1;
			vegas.numRetx = 0;
			vegas.seqMarked = vegas.bytesSent = 0L;
			vegas.periodBegin = Double.NaN;
		}

	}

	/** Initialize variables for the retransmit timer as in [TCPILL2]. */
	protected void rtt_init() {
		t_srtt = srtt_init;
		t_rttvar = rttvar_init;
		t_rtxcur = rtxcur_init;
		if (vegas != null)
			vegas.base_rtt = 0;
		backoff = 1;
	}

	/**
	 * Update RTT estimations and recalculate RXT timer base. The estimation
	 * method is same as in [TCPILL2]. The sending time of a packet is carried
	 * back by acknowledgment packet as ts_ in header (which doesn't exist in
	 * real world TCP/IP header). When ts_ is -1, the packet is a retransmitted
	 * one, so no RTT estimation updating is done for this packet. The algorithm
	 * is described in [RFC793] and [TCPILL1]. SampleRTT = current time - ts_;
	 * SmoothedRTT[new] = 7/8*SmoothedRTT[old] + 1/8*SampleRTT; Delta =
	 * |SampleRTT - SmoothedRTT[old]|; RTTVariance[new] = 3/4*RTTVariance[old] +
	 * 1/4*Delta; and the retransmission timer base is RTO[new] =
	 * SmoothedRTT[new] + 4*RTTVariance[new].
	 * 
	 * The exception is that for the first acknowledgment received: SampleRTT =
	 * current time - ts_; SmoothedRTT[new] = SampleRTT; RTTVariance[new] =
	 * SampleRTT/2; and the retransmission timer base is RTO[new] =
	 * SmoothedRTT[new] + 4*RTTVariance[new].
	 * 
	 * @param ts_
	 *            the sending time of the acknowledged packet.
	 * 
	 */
	protected void rtt_update(double now_, double ts_) {
		if (ts_ < 0)
			return;
		cur_rtt = now_ - ts_; // unit is millisecond

		if (implementation == VEGAS) {
			if (vegas.base_rtt == 0)
				vegas.base_rtt = cur_rtt;
			else if (vegas.base_rtt > cur_rtt)
				vegas.base_rtt = cur_rtt;
		} else if (implementation == CUBIC) {
			double rtt_us = cur_rtt * 1000.0; // in microseconds
			ca.pkts_acked(this, this.numack, rtt_us);
		}
		int t_rtt = (int) (cur_rtt / t_grain + .5);
		// Round the RTT to integer times of time grain
		if (t_rtt < 1)
			t_rtt = 1;

		int old_srtt_ = t_srtt;
		int old_rttvar_ = t_rttvar;

		if (t_srtt != 0) {
			int delta = t_rtt - (t_srtt >> srtt_bits); // delta = (m - a0)
			if ((t_srtt += delta) <= 0) // a1 = 7/8 a0 + 1/8 m
				t_srtt = 1;
			if (delta < 0)
				delta = -delta;
			delta -= (t_rttvar >> rttvar_bits);
			if ((t_rttvar += delta) <= 0) // var1 = 3/4 var0 + 1/4 |delta|
				t_rttvar = 1;
		} else {
			/*
			 * For first RTT estimation, set srtt as rtt and rttvar as half of
			 * rtt as in [TCPILL2]
			 */
			t_srtt = t_rtt << srtt_bits; // srtt = rtt
			t_rttvar = t_rtt << (rttvar_bits - 1);// rttvar = rtt / 2
		}

		if (srttPort._isEventExportEnabled()
				&& (t_srtt >> srtt_bits != old_srtt_ >> srtt_bits))
			srttPort.exportEvent(SRTT_EVENT, new DoubleObj(
					(t_srtt >> srtt_bits) * t_grain), null);
		if (rttvarPort._isEventExportEnabled()
				&& (t_rttvar >> rttvar_bits != old_rttvar_ >> rttvar_bits))
			rttvarPort.exportEvent(RTTVAR_EVENT, new DoubleObj(
					(t_rttvar >> rttvar_bits) * t_grain), null);

		/*
		 * Current retransmit value is (unscaled) smoothed round trip estimate
		 * plus 2^rttvar_exp times (unscaled) rttvar.
		 */
		t_rtxcur = (long) (((t_rttvar << (rttvar_exp - rttvar_bits)) + (t_srtt >> srtt_bits))
				* t_grain);

		if (isDebugEnabled() && isDebugEnabledAt(DEBUG_RTT)) {
			debug("RTT UPDATE: " + (cur_rtt) + " " + t_rtt + " " + t_srtt + " "
					+ t_rttvar + " " + t_rtxcur + ", backoff=" + backoff);
		}
	}

	/**
	 * Returns the RXT timeout value by multiplying base value and back off
	 * value.
	 * 
	 * @param t_backoff
	 *            RXT timer backoff value.
	 */
	protected long rxt_timer(int t_backoff_) {
		if (t_backoff_ > 64)
			t_backoff_ = 64;
		long rto_ = (long) (t_rtxcur * t_backoff_);
		// if (rto_ < 2*t_grain
		// return 2*t_grain;
		if (rto_ < 1) // according to RFC2988
			return 1000;
		else if (rto_ > maxrto)
			return maxrto*1000;
		else
			return rto_*1000;
	}

	/**
	 * Initializes sliding window variables. Congestion window and slow start
	 * threshold is set as in [TCPILL2].
	 */
	protected void win_init() {
		CWND = MSS;
		AWND = AWND_DEFAULT * MSS;
		MAXCWND = MAXCWND_DEFAULT / MSS * MSS;
		sthld = INIT_SS_THRESHOLD / MSS * MSS;
		if (sstPort._isEventExportEnabled())
			sstPort.exportEvent(SST_EVENT, new DoubleObj((double) sthld / MSS),
					null);
		snd_una = 0;
		snd_nxt = 0;
		snd_max = 0;
		dt_max = 0;
		dup_ack = 0;
		burst = 0;
		last_seq = 0;
		phase = SLOW_START;
	}

	/**
	 * Returns the size of usable window, which is the smaller value of
	 * congestion window and window size advertized by peer.
	 */
	protected int snd_wnd() {
		if (CWND < AWND)
			return CWND / MSS * MSS;
		else
			return AWND;
	}

	/** Returns the maximum sequence number that can be sent. */
	protected long seq_max() {
		return Math.min(snd_una + snd_wnd(), dt_max);
		// ((snd_max<dt_max)?snd_max:dt_max);
	}

	protected void dataArriveAtUpPort(Object data_, Port upPort_) {
		try {
			ByteStreamContract.Message msg_ = (ByteStreamContract.Message) data_;
			switch(msg_.getType()){
			case ByteStreamContract.SEND:
				recvMsgSend(msg_, upPort_);
				break;
			case ByteStreamContract.QUERY:
				recvMsgQuery(upPort_);
				break;
			case ByteStreamContract.STOP:
				recvMsgStop();
				break;
			}		
		} catch (Exception e_) {
			e_.printStackTrace();
			error(data_, "dataArriveAtUpPort()", upPort_, e_);
		}
	}

	private void recvMsgStop() {
		isAppStopped = true;
		notifiedApplication = true;
	}

	private void recvMsgSend(ByteStreamContract.Message msg_, Port upPort_) {
		int len_ = sbuffer.append(msg_.getByteArray(),
				msg_.getOffset(), msg_.getLength());
		if(isDebugEnabled())
			debug("recv msg send: len_ = " + len_);
		if (len_ > 0) {
			dt_max += len_;
			// Send the whole window of bytes
			snd_maxpck("recvMsgSend()");
			if (timeoutEvent == null)
				// || Double.isNaN(timeoutEvent.timeout))
				resetRXTTimer(getTime(), "recvMsgSend");
		}
		// return available buffer space
		if (len_ == msg_.getLength())
			upPort_.doSending(new ByteStreamContract.Message(
					ByteStreamContract.REPORT, sbuffer
							.getAvailableSpace(), id));
		else
			upPort_.doSending(new ByteStreamContract.Message(
					ByteStreamContract.REPORT, len_
							- msg_.getLength(), id));
		notifiedApplication = false;
		// need to notify app once buffer is cleared later
	}

	/**
	 * receive Query message from up port
	 * if connection is established, send the available buffer space back
	 * if connection is not established, send SYN packet to down port, 
	 * meanwhile, send the available buffer space back to up port
	 */
	private void recvMsgQuery(Port upPort_) {
		if (state == ESTABLISHED)
			upPort_.doLastSending(new ByteStreamContract.Message(
					ByteStreamContract.REPORT, sbuffer
							.getAvailableSpace(), id));
		else // try to connect with the server
		{
			// send SYN message to down port
			ack_syn_fin(false, true, false);
			state = SYN_SENT;
			// set SYN_ACK timeout
			if (timeoutEvent == null)
				resetRXTTimer(SYN_ACK_TIMEOUT, "SYN ACK");
		}
		notifiedApplication = false;
	}

	protected void dataArriveAtDownPort(Object data_, Port downPort_) {
		TCPPacket tcppkt_ = null;
		try {
			tcppkt_ = (TCPPacket) data_;
			if(tcppkt_.isACK()){
				switch(state){
				case SYN_SENT:
					if(tcppkt_.isSYN()){
						recvSYNACK(tcppkt_);
//						if (ca != null) {
//							ca.init(this);
//						}
					}
					break;
				case FIN_WAIT_1:
					// recv fin, close!
					if (tcppkt_.getSeqNo() == this.getAckNo()
							&& tcppkt_.getAckNo() == this.getSeqNo() + 1) {
						// close the connection
						state = CLOSED;
						if (isDebugEnabled())
							debug("connection is closed.");
					} else
						debug("drop: " + tcppkt_);
					break;
				default:
					recv(tcppkt_);
//					System.out.println("sbuffer size " + sbuffer.getSize());
					if (isAppStopped && sbuffer.getSize() == 0) {
						debug("try to terminate the connection");
						debug("send FinAck");
						ack_syn_fin(true, false, true);
						state = FIN_WAIT_1;
						resetRXTTimer(getTime(), "FIN_WAIN_1");
						TimeLog.print();
						TimeLog.clear();
					}	
				}
			}else{
				error(data_, "dataArriveAtDownPort()", downPort, "pkt is not an ack");
			}
		} catch (Exception e_) {
			e_.printStackTrace();
			error(data_, "dataArriveAtDownPort()", downPort_,
					"unrecognized data: " + e_);
		} 
	}

	protected void recvSYNACK(TCPPacket tcppkt_) {
		if (this.getSeqNo() == tcppkt_.getAckNo() - 1) {
			if (isDebugEnabled()) {
				debug("SYN ACK received.");
			}
		} else {
			if (isDebugEnabled()) {
				debug("SYN ACK received: ACK error.");
			}
		}
		// set seqno
		this.snd_nxt = tcppkt_.getAckNo();
		ack_syn_fin(true, false, false); // send ack to the server
		state = ESTABLISHED;

		// connection established
		// start the application
		ByteStreamContract.Message start = new ByteStreamContract.Message(
				ByteStreamContract.START, sbuffer.getAvailableSpace(), id);
		upPort.doSending(start);
		
//		resetRXTTimer(backoff*1000); // wait for sending packets
		if(timeoutEvent != null)
			cancelTimeout(timeoutEvent.timer);
	}

	/** Handles a packet arriving at the down port. */
	protected void recv(TCPPacket pkt_) {
		AWND = pkt_.getAdvWin() << wind_scal & 0xffffffff; // Get advertised
		if (awndPort._isEventExportEnabled())
			awndPort.exportEvent(AWND_EVENT,
					new DoubleObj((double) AWND / MSS), null);
		long ackseq_ = pkt_.getAckNo();
		// Get the acknowledgement sequence number
		if (ackPort._isEventExportEnabled())
			ackPort.exportEvent(ACK_EVENT, new DoubleObj((double) (ackseq_
					/ MSS - 1)), null);
		
		option_process(pkt_, ackseq_); // Processing options in packet
		if(isDebugEnabled() || isDebugEnabledAt(TCPConstants.DEBUG_ACK))
			debug("tcp:ackseq:" + ackseq_ + " snd_una:" + snd_una
				+ " snd_nxt:" + snd_nxt + " snd_nxt_marked:" + snd_nxt_marked + " dup_ack:" + dup_ack + " cwnd:"
				+ CWND / MSS + " awnd:" + AWND / MSS + " in_flight:"
				+ this.tcp_packets_in_flight()
				+ " last_seq:" + last_seq + " phase:" + phase);
		if (ackseq_ < snd_una) // old acknowledgement, ignored
		{
			if(isDebugEnabled())debug("old acknowledgement, ignored");
		}else if(ackseq_ > snd_una)
		{
			burst = 0;
			recvNewACK(pkt_);
		}
		else
		{
			burst = 0;
			recvDupACK(pkt_);
		}
		

		
	}

	private void recvDupACK(TCPPacket pkt_) {
		long ackseq_ = pkt_.getAckNo();
		// Duplicated ACK
		++dup_ack;
		if (implementation == VEGAS) {
			if (isDebugEnabled()
					&& (isDebugEnabledAt(DEBUG_SAMPLE)
							|| isDebugEnabledAt(DEBUG_ACK) || isDebugEnabledAt(DEBUG_DUPACK)))
				debug("DUPACK " + dup_ack + ": " + (ackseq_ / MSS));
			vegas_handleAck(ackseq_, true/* consider retx */, getTime());
		} else if (implementation == CUBIC) {
			if (dup_ack < NUMDUPACKS) {
				// on the first and second duplicate ACKs
				// snd_maxpck("DUP_ACK"+dup_ack); // Send packet
				// RFC 5681:
				// On the first and second duplicate ACKs received at a
				// sender, a
				// TCP SHOULD send a segment of previously unsent data
				// per [RFC3042]
				// provided that the receiver’s advertised window
				// allows, the total
				// FlightSize would remain less than or equal to cwnd
				// plus 2*SMSS,
				// and that new data is available for transmission.
				// Further, the
				// TCP sender MUST NOT change cwnd to reflect these two
				// segments
				// [RFC3042]. Note that a sender using SACK [RFC2018]
				// MUST NOT send
				// new data unless the incoming duplicate acknowledgment
				// contains
				// new SACK information.
				// send new data, i.e., previously unsent data
//				int size_ = Math.min(MSS, sbuffer.getSize());
//				snd_packet(snd_nxt, size_);
//				snd_nxt += size_;
//				if (cwndPort._isEventExportEnabled())
//					cwndPort.exportEvent(CWND_EVENT, new DoubleObj(
//							(double) CWND / MSS), null);
			} else if (dup_ack == NUMDUPACKS) {
				tcp_enter_recovery();
				snd_nxt_marked = snd_nxt;
//				tcp_noenter_recovery();
				// generate cwnd event
				if (cwndPort._isEventExportEnabled())
					cwndPort.exportEvent(CWND_EVENT, new DoubleObj(
							(double) CWND / MSS), null);
				if (sstPort._isEventExportEnabled())
					sstPort.exportEvent(SST_EVENT, new DoubleObj(
							(double) sthld / MSS), null);
				if (!pkt_.isSACK()) {
					if (isDebugEnabled()
							&& (isDebugEnabledAt(DEBUG_SAMPLE)
									|| isDebugEnabledAt(DEBUG_ACK) || isDebugEnabledAt(DEBUG_DUPACK)))
						debug("DUPACK" + dup_ack + ": " + (ackseq_ / MSS)
								+ ", window shrinked: " + win_info()
								+ ", retransmit " + (snd_una / MSS)
								+ ", snd_nxt_marked="
								+ (snd_nxt_marked / MSS)
								+ ", timeout adjusted to "
								+ (getTime() + rxt_timer(backoff)));
					snd_packet(snd_una, Math.min(MSS, sbuffer.getSize()));
					this.retrans_out++;
				} else {
					if (isDebugEnabled()
							&& (isDebugEnabledAt(DEBUG_SAMPLE)
									|| isDebugEnabledAt(DEBUG_ACK) || isDebugEnabledAt(DEBUG_DUPACK)))
						debug("DUPACK" + dup_ack + ": " + (ackseq_ / MSS)
								+ ", window shrinked: " + win_info()
								+ ", retransmit the whole window "
								+ "(selected), recvBuffer="
								+ _printRecvBuffer() + ", snd_nxt_marked="
								+ (snd_nxt_marked / MSS)
								+ ", timeout adjusted to "
								+ (getTime() + rxt_timer(backoff)));
					snd_nxt = snd_una;
					snd_maxpck("CUBIC_SACK_RESEND");
				}
				resetRXTTimer(0, "CUBIC_SACK_RESEND");					
			} else if (dup_ack > NUMDUPACKS) {
//				if (cwndPort._isEventExportEnabled())
//					cwndPort.exportEvent(CWND_EVENT, new DoubleObj(
//							(double) CWND / MSS), null);
				// fast recovery:
				// temporary inflating the window to fill up the pipe
//				if(CWND < this.last_cwnd)
					CWND += MSS;
				
				// generate cwnd event
				if (cwndPort._isEventExportEnabled())
					cwndPort.exportEvent(CWND_EVENT, new DoubleObj(
							(double) CWND / MSS), null);
//				System.out.println("stay fast_recv:" + CWND / MSS + ":"
//						+ sthld / MSS + " " + phase + " MORE_DUPACK");
				if (isDebugEnabled()
						&& (isDebugEnabledAt(DEBUG_SAMPLE)
								|| isDebugEnabledAt(DEBUG_ACK) || isDebugEnabledAt(DEBUG_DUPACK)))
					debug("DUPACK" + dup_ack + ": " + (ackseq_ / MSS)
							+ ", fast_recovery, window += MSS: "
							+ win_info());
				increase_buffer();
				snd_maxpck("MORE_DUPACK");					
			}

			// System.out.println(PHASES[phase]+" DUPACK: CWND:"+ CWND/MSS
			// +" sthld:" + sthld/MSS
			// + " cnt:"+this.snd_cwnd_cnt+":"+((TCPCubic)ca).cnt);
		} else // RENO, NEW-RENO, TAHOE
		{
			if (dup_ack == NUMDUPACKS) {
				win_decrease(false/* not due to timeout */);
				snd_nxt_marked = snd_nxt;

				// Fast retransmit
				if (implementation == RENO || implementation == NEW_RENO) {
					// Reno: retransmit but don't reset snd_nxt
					phase = FAST_RECOVERY;

					if (!SACK) {
						if (isDebugEnabled()
								&& (isDebugEnabledAt(DEBUG_SAMPLE)
										|| isDebugEnabledAt(DEBUG_ACK) || isDebugEnabledAt(DEBUG_DUPACK)))
							debug("DUPACK" + dup_ack + ": "
									+ (ackseq_ / MSS)
									+ ", window shrinked: " + win_info()
									+ ", retransmit " + (snd_una / MSS)
									+ ", snd_nxt_marked="
									+ (snd_nxt_marked / MSS)
									+ ", timeout adjusted to "
									+ (getTime() + rxt_timer(backoff)));
						snd_packet(snd_una,
								Math.min(MSS, sbuffer.getSize()));
					} else {
						if (isDebugEnabled()
								&& (isDebugEnabledAt(DEBUG_SAMPLE)
										|| isDebugEnabledAt(DEBUG_ACK) || isDebugEnabledAt(DEBUG_DUPACK)))
							debug("DUPACK" + dup_ack + ": "
									+ (ackseq_ / MSS)
									+ ", window shrinked: " + win_info()
									+ ", retransmit the whole window "
									+ "(selected), recvBuffer="
									+ _printRecvBuffer()
									+ ", snd_nxt_marked="
									+ (snd_nxt_marked / MSS)
									+ ", timeout adjusted to "
									+ (getTime() + rxt_timer(backoff)));
						snd_nxt = snd_una;
						snd_maxpck("RENO_SACK_RESEND");
					}
					resetRXTTimer(getTime(), "RENO_SACK_RESEND");
				} else {
					// Tahoe: reset snd_nxt to snd_una as well...
					snd_nxt = snd_una;
					if (isDebugEnabled()
							&& (isDebugEnabledAt(DEBUG_SAMPLE)
									|| isDebugEnabledAt(DEBUG_ACK) || isDebugEnabledAt(DEBUG_DUPACK)))
						debug("DUPACK" + dup_ack + ": " + (ackseq_ / MSS)
								+ ", window shrinked: " + win_info()
								+ ", retransmit whole window"
								+ (SACK ? " (selected)" : "")
								+ ", timeout adjusted to "
								+ (getTime() + rxt_timer(backoff)));
					snd_maxpck("Tahoe_RESEND");
					resetRXTTimer(getTime(), "Tahoe_RESEND");
				}
			} else if (dup_ack > NUMDUPACKS) {
				if (implementation == RENO || implementation == NEW_RENO) {
					// fast recovery:
					// temporary inflating the window to fill up the pipe
					CWND += MSS;

					if (isDebugEnabled()
							&& (isDebugEnabledAt(DEBUG_SAMPLE)
									|| isDebugEnabledAt(DEBUG_ACK) || isDebugEnabledAt(DEBUG_DUPACK)))
						debug("DUPACK" + dup_ack + ": " + (ackseq_ / MSS)
								+ ", fast_recovery, window += MSS: "
								+ win_info());

					if (cwndPort._isEventExportEnabled())
						cwndPort.exportEvent(CWND_EVENT, new DoubleObj(
								(double) CWND / MSS), null);
					increase_buffer();
				}
				snd_maxpck("MORE_DUPACK");
			}
		} // end RENO, TAHOE for processing dupack
	}

	private void recvNewACK(TCPPacket pkt_) {
		// Not a duplicated ACK
		// received advanced_ number of bytes
		long ackseq_ = pkt_.getAckNo();
		long advanced_ = ackseq_ - snd_una;
		sbuffer.remove(null, 0, (int) advanced_);
		snd_una = ackseq_;
		if (dup_ack >= NUMDUPACKS) {
			if(implementation == CUBIC){
				if(ackseq_ >= snd_nxt_marked)
					// quit fast recovery
					congestionAvoidanceCubic(ackseq_,advanced_, pkt_);
				else
				{
					stayInFastRecoveryCubic(ackseq_, advanced_, pkt_);
					return;
				}
			}
			else if(implementation == RENO)
			{
				congestionAvoidanceReno(ackseq_);
			}
			else if(implementation == NEW_RENO){
				if(ackseq_ >= snd_nxt_marked)
					congestionAvoidanceReno(ackseq_);
				else
				{
					stayInFastRecoveryNewReno(ackseq_, advanced_);
					return;
				}
			}
		}
		// 07-16-2002: refill buffer only when less than AWND/2 bytes
		// are in the buffer
		// if (!notifiedApplication && sbuffer.getSize() < (AWND >> 1)) {
		// 09-25-2002: refill buffer only when window outgrow buffer
		if (!notifiedApplication && snd_una + snd_wnd() + MSS >= dt_max) {
			notifiedApplication = true;
			upPort.doSending(new ByteStreamContract.Message(
					ByteStreamContract.REPORT, sbuffer.getAvailableSpace(),
					id));
		}		

		// since received new ACK, reset these parameters
		dup_ack = 0;
		if (NS_COMPATIBLE) // reset before update rtt...
			resetRXTTimer(0, "reset before update rtt...");
		backoff = 1;

		if (snd_una - last_seq >= MSS) {
			// It's time to update congestion window
			numack++;
			rtt_update(getTime(), pkt_.getaTS()); // Updating RTT estimations

			win_increase(ackseq_); // advance window
			if (cwndCntPort._isEventExportEnabled())
				cwndCntPort.exportEvent(CWND_CNT_EVENT, new DoubleObj(
						(double) this.snd_cwnd_cnt), null);
			if (cwndPort._isEventExportEnabled())
				cwndPort.exportEvent(CWND_EVENT, new DoubleObj(
						(double) CWND / MSS), null);
			if (sstPort._isEventExportEnabled())
				sstPort.exportEvent(SST_EVENT, new DoubleObj((double) sthld
						/ MSS), null);
			while (last_seq + MSS <= snd_una)
				last_seq += MSS;
		}

		// do VEGAS after rtt update and window increase
		if (implementation == VEGAS) {
			vegasUpdate(ackseq_);
		}

		if (isDebugEnabled()
				&& (isDebugEnabledAt(DEBUG_SAMPLE) || isDebugEnabledAt(DEBUG_ACK))) {
			if (timeoutEvent == null)
				debug("ACK: " + (ackseq_ / MSS) + "/" + ackseq_ + ", "
						+ win_info());
			else
				debug("ACK: " + (ackseq_ / MSS) + "/" + ackseq_ + ", "
						+ win_info() + ", timeout adjusted to "
						+ (getTime() + rxt_timer(backoff)));
		}

		// Send packet after receiving this new ACK
		snd_maxpck("NEW_ACK"); 
		if (!NS_COMPATIBLE)
			resetRXTTimer(0, "NEW_ACK");
	}

	private void vegasUpdate(long ackseq_) {
		if (ackseq_ >= vegas.seqMarked) {
			// end of a mearuse period: do not adjust when in
			// slow_start and exp. increase
			if (phase != SLOW_START
					|| vegas.windowAdjust != VEGAS_INCREASE) {
				double actual_ = vegas.bytesSent
						/ (getTime() - vegas.periodBegin) / MSS
						* vegas.base_rtt;
				double expected_ = (double) (seq_max() - snd_una) / MSS;
				double diff_ = expected_ - actual_;
				if (isDebugEnabled() && isDebugEnabledAt(DEBUG_VEGAS))
					debug("VEGAS measure: expect=" + expected_
							+ ", actual=" + actual_ + ", bytesSent="
							+ vegas.bytesSent);
				if (phase == SLOW_START) {
					if (diff_ > alpha) {
						phase = CONGESTION_AVOIDANCE;
						if (isDebugEnabled()
								&& (isDebugEnabledAt(DEBUG_SAMPLE) || isDebugEnabledAt(DEBUG_ACK)))
							debug("(*********) Left slow start and"
									+ " entered congestion avoidance, diff="
									+ diff_);
						if (diff_ > beta)
							vegas.windowAdjust = VEGAS_DECREASE;
						else
							vegas.windowAdjust = VEGAS_NOCHANGE;
					} else
						// still in slow start,
						// do exp increase in next RTT
						vegas.windowAdjust = VEGAS_INCREASE;
				} else {
					if (diff_ <= alpha)
						vegas.windowAdjust = VEGAS_INCREASE;
					else if (diff_ >= beta)
						vegas.windowAdjust = VEGAS_DECREASE;
					else
						vegas.windowAdjust = VEGAS_NOCHANGE;
				}
			} else
				// still in slow start, do measurement in next RTT
				vegas.windowAdjust = VEGAS_NOCHANGE;
			if (isDebugEnabled() && isDebugEnabledAt(DEBUG_VEGAS))
				debug("VEGAS phase=" + PHASES[phase] + ", adjust="
						+ VEGAS_ADJUST[vegas.windowAdjust]);
			vegas.periodBegin = Double.NaN;
			// to trigger start of a new period in snd_packet()
		}

		// retransmit?
		if (vegas.numack >= 0) {
			vegas.numack++;
			// if vegas.numack < 3, consider retransmission
			vegas_handleAck(ackseq_,
					vegas.numack < 3/* consider retx? */, getTime());
		} else
			vegas_handleAck(ackseq_, false/* consider retx? */, getTime());
	}

	private void stayInFastRecoveryCubic(long ackseq_, long advanced_, TCPPacket pkt_) {
		// stays in fast recovery, but window back to sthld
		// next round of fast retransmit
		this.retrans_out--;
		// CWND = sthld;
		// CWND -= (advanced_ - MSS);
		// stays in fast recovery, but window back to sthld
		CWND -= (advanced_ - MSS);

		if (cwndPort._isEventExportEnabled())
			cwndPort.exportEvent(CWND_EVENT, new DoubleObj(
					(double) CWND / MSS), null);
		if (!pkt_.isSACK()) {
			if (isDebugEnabled()
					&& (isDebugEnabledAt(DEBUG_SAMPLE) || isDebugEnabledAt(DEBUG_ACK)))
				debug("PARTIAL_ACK"
						+ (ackseq_ / MSS)
						+ "/"
						+ ackseq_
						+ ", retransmit the segment, window_back_to: "
						+ win_info() + ", timeout adjusted to "
						+ (getTime() + rxt_timer(backoff)));

			snd_packet(ackseq_, MSS);
		} else {
			if (isDebugEnabled()
					&& (isDebugEnabledAt(DEBUG_SAMPLE) || isDebugEnabledAt(DEBUG_ACK)))
				debug("PARTIAL_ACK" + (ackseq_ / MSS) + "/"
						+ ackseq_ + "window_back_to+1: "
						+ win_info() + ", timeout adjusted to "
						+ (getTime() + rxt_timer(backoff)));
		}
		snd_maxpck("PARTIAL_ACK");
		// Send new packets if window allows
		resetRXTTimer(0, "PARTIAL_ACK");
	}

	private void congestionAvoidanceCubic(long ackseq_, long advanced_, TCPPacket pkt_) {
		
		phase = CONGESTION_AVOIDANCE; // end of fast recovery
		CWND = sthld;
		sthld = ca.ssthresh(this) * MSS;
		lost_out = this.retrans_out = 0;
		
		if (isDebugEnabled()
				&& (isDebugEnabledAt(DEBUG_SAMPLE) || isDebugEnabledAt(DEBUG_ACK)))
			debug("ACK: " + (ackseq_ / MSS) + "/" + ackseq_
					+ ", end of fast recovery, window restored: "
					+ win_info());
		if (cwndPort._isEventExportEnabled())
			cwndPort.exportEvent(CWND_EVENT, new DoubleObj(
					(double) CWND / MSS), null);
		if (sstPort._isEventExportEnabled())
			sstPort.exportEvent(SST_EVENT, new DoubleObj(
					(double) sthld / MSS), null);
		
			
	}

	private void stayInFastRecoveryNewReno(long ackseq_, long advanced_) {
		// stays in fast recovery, but window back to sthld
		CWND -= (advanced_ - MSS);

		if (cwndPort._isEventExportEnabled())
			cwndPort.exportEvent(CWND_EVENT, new DoubleObj(
					(double) CWND / MSS), null);
		if (!SACK) {
			if (isDebugEnabled()
					&& (isDebugEnabledAt(DEBUG_SAMPLE) || isDebugEnabledAt(DEBUG_ACK)))
				debug("PARTIAL_ACK"
						+ (ackseq_ / MSS)
						+ "/"
						+ ackseq_
						+ ", retransmit the segment, window_back_to: "
						+ win_info() + ", timeout adjusted to "
						+ (getTime() + rxt_timer(backoff)));

			snd_packet(ackseq_, MSS);
		} else {
			if (isDebugEnabled()
					&& (isDebugEnabledAt(DEBUG_SAMPLE) || isDebugEnabledAt(DEBUG_ACK)))
				debug("PARTIAL_ACK" + (ackseq_ / MSS) + "/"
						+ ackseq_ + "window_back_to+1: "
						+ win_info() + ", timeout adjusted to "
						+ (getTime() + rxt_timer(backoff)));
		}
		snd_maxpck("PARTIAL_ACK");
		// Send new packets if window allows
		resetRXTTimer(0, "PARTIAL_ACK1");
	}

	private void congestionAvoidanceReno(long ackseq_) {
		phase = CONGESTION_AVOIDANCE; // end of fast recovery
		CWND = sthld;
		if (isDebugEnabled()
				&& (isDebugEnabledAt(DEBUG_SAMPLE) || isDebugEnabledAt(DEBUG_ACK)))
			debug("ACK: " + (ackseq_ / MSS) + "/" + ackseq_
					+ ", end of fast recovery, window restored: "
					+ win_info());
		// XX: this should be the ack of the fast retransmitted
		// packet so makes sense to adjust the snd_nxt, but is it
		// in the standard?
		// Turned out this setting causes a lot more dup acks...
		// snd_nxt = ackseq_;
		if (sstPort._isEventExportEnabled())
			sstPort.exportEvent(SST_EVENT, new DoubleObj(
					(double) sthld / MSS), null);
		if (cwndPort._isEventExportEnabled())
			cwndPort.exportEvent(CWND_EVENT, new DoubleObj(
					(double) CWND / MSS), null);
	}

	protected void tcp_enter_recovery() {
		phase = FAST_RECOVERY;
		this.numack = 0;
		this.lost_out++;
		this.snd_cwnd_cnt = 0;
		this.last_cwnd = CWND;

		sthld = ((int) snd_wnd() >> 1) / MSS * MSS;
		// Half of the current window size
		if (sthld < MSS << 1)
			sthld = MSS << 1; // at least two MSS

		if (sstPort._isEventExportEnabled())
			sstPort.exportEvent(SST_EVENT, new DoubleObj((double) sthld / MSS),
					null);
		CWND = sthld + 3 * MSS;

		if (cwndPort._isEventExportEnabled())
			cwndPort.exportEvent(CWND_EVENT,
					new DoubleObj((double) CWND / MSS), null);
	}
	
	protected void tcp_noenter_recovery() {
//		phase = FAST_RECOVERY;
		this.numack = 0;
		this.lost_out = 0;
		this.snd_cwnd_cnt = 0;
		this.last_cwnd = CWND;
		phase = CONGESTION_AVOIDANCE; // end of fast recovery
		lost_out = this.retrans_out = 0;
//		sthld = ((int) snd_wnd() >> 1) / MSS * MSS;
		sthld = ca.ssthresh(this)*MSS;
		// Half of the current window size
		if (sthld < MSS << 1)
			sthld = MSS << 1; // at least two MSS

		if (sstPort._isEventExportEnabled())
			sstPort.exportEvent(SST_EVENT, new DoubleObj((double) sthld / MSS),
					null);
		CWND = sthld;
		this.dup_ack = 0;
		if (cwndPort._isEventExportEnabled())
			cwndPort.exportEvent(CWND_EVENT,
					new DoubleObj((double) CWND / MSS), null);
	}

	/** Processing TCP header options. */
	protected void option_process(TCPPacket pkt_, long seq_) {
		if (SACK && pkt_.isSACK()) {
			if (recvBuffer != null)
				recvBuffer.checkout(0L, seq_);	
			int SACKLen_ = pkt_.getSACKLen();
			long[] LEblk_ = pkt_.getLEblk();
			long[] REblk_ = pkt_.getREblk();
			if (isDebugEnabled() && isDebugEnabledAt(DEBUG_SACK))
				debug("SACK: " + sackToString(SACKLen_, LEblk_, REblk_));
			if (recvBuffer == null) {
				recvBuffer = new LongSpace(0, 0);
			}
			for (int i = 0; i < SACKLen_; i++) {
				recvBuffer.checkin(LEblk_[i], REblk_[i]);
			}
			
		}
	}

	// print out SACK blocks nicely
	String sackToString(int SACKLen_, long[] LEblk_, long[] REblk_) {
		StringBuffer sb_ = new StringBuffer("SACKLen=" + SACKLen_);
		if (SACKLen_ > 0) {
			for (int i = 0; i < SACKLen_; i++)
				sb_.append(",[" + (LEblk_[i] / MSS) + "/" + LEblk_[i] + ","
						+ (REblk_[i] / MSS) + "/" + REblk_[i] + ")");
		}
		return sb_.toString();
	}

	// increase application buffer in response to window increase
	void increase_buffer() {
		if (snd_wnd() > sbuffer.getCapacity()) {
			if (isDebugEnabled() && isDebugEnabledAt(DEBUG_ACK))
				debug("sending buffer size increased: " + sbuffer.getCapacity()
						+ " ---> " + snd_wnd());
			sbuffer.resize((int) snd_wnd(), false/* don't shrink */);
		}
	}

	/**
	 * Updates congestion window and sending window variables. The congestion
	 * window updating is according to [RFC2581].
	 * 
	 * @param ackseq_
	 */
	protected void win_increase(long ackseq_) {
		// System.out.println(" numack "+ phase + " "+ this.numack + " " +
		// this.CWND);
		if (implementation == CUBIC && phase != FAST_RECOVERY) {
//			int cwnd = CWND;
			int in_flight = tcp_packets_in_flight();
			ca.cong_avoid(this, ackseq_, in_flight);
//			debug("cwnd increase " + cwnd + " " + CWND);
		}else if (implementation == VEGAS) {
			if (phase == SLOW_START) {
				if (vegas.windowAdjust == VEGAS_INCREASE) {
					CWND += MSS;
					// non-sense to stop VEGAS at sthld
					// if (CWND > sthld) CWND = sthld;
					if (cwndPort._isEventExportEnabled())
						cwndPort.exportEvent(CWND_EVENT, new DoubleObj(
								(double) CWND / MSS), null);
				}
			} else {
				if (vegas.windowAdjust != VEGAS_NOCHANGE) {
					if (vegas.windowAdjust == VEGAS_INCREASE) {
						CWND += MSS / (CWND / MSS); // linear increase
						if (CWND > MAXCWND)
							CWND = MAXCWND;
					} else {
						CWND -= MSS / (CWND / MSS); // linear decrease
						if (CWND < MSS)
							CWND = MSS;
					}

					if (cwndPort._isEventExportEnabled())
						cwndPort.exportEvent(CWND_EVENT, new DoubleObj(
								(double) CWND / MSS), null);
				}
			}
		} else { // RENO, NEW-RENO, TAHOE
			int in_flight = tcp_packets_in_flight();		
			double oldCWND_ = CWND;	
			if(tcp_is_cwnd_limited(in_flight))
			{
				if (CWND < sthld) // Slow_start
					CWND += MSS;
				else { // Congestion avoidance
					CWND += MSS * MSS / CWND;
				}
			}
			if (CWND > MAXCWND)
				CWND = MAXCWND;

			if (isDebugEnabled()
					&& (isDebugEnabledAt(DEBUG_SAMPLE) || isDebugEnabledAt(DEBUG_ACK)))
				if (oldCWND_ < sthld && CWND >= sthld)
					debug("(*********) Left slow start and entered"
							+ "congestion avoidance, sthld=" + sthld);

			if (cwndPort._isEventExportEnabled())
				cwndPort.exportEvent(CWND_EVENT, new DoubleObj((double) CWND
						/ MSS), null);
		}

		increase_buffer();
	}

	private int tcp_packets_in_flight() {
		return Math.max((int) ((snd_nxt - snd_una) / MSS - tcp_left_out() + retrans_out),0);
	}

	private int tcp_left_out() {
		int sacked_out = 0;
		// XXX
		if (recvBuffer != null && recvBuffer.numOfLongIntervals() != 0) {
			// iterates thru the gaps in recvBuffer
			long dtlen_ = seq_max();
			for (Iterator<?> it_ = recvBuffer.getGapIterator(snd_nxt); it_
					.hasNext();) {
				LongInterval gap_ = (LongInterval) it_.next();
				if (gap_.end > dtlen_)
					return sacked_out + lost_out;
				else
					sacked_out += (gap_.end - gap_.start) / MSS;
			}
		}

		return sacked_out + lost_out;
	}

	/**
	 * Updates congestion window and sending window variables when three
	 * duplicate acks are received.
	 */
	protected void win_decrease(boolean timeout_) {
		// only for RENO, NEW-RENO and TAHOE
		sthld = ((int) snd_wnd() >> 1) / MSS * MSS;
		// Half of the current window size
		if (sthld < MSS << 1)
			sthld = MSS << 1; // at least two MSS
		if (sstPort._isEventExportEnabled())
			sstPort.exportEvent(SST_EVENT, new DoubleObj((double) sthld / MSS),
					null);

		if (!timeout_ && (implementation == RENO || implementation == NEW_RENO))
			// For Reno, use fast recovery
			CWND = sthld + 3 * MSS;
		else
			// For Tahoe or timeout: enters slow start
			CWND = MSS;

		if (cwndPort._isEventExportEnabled())
			cwndPort.exportEvent(CWND_EVENT,
					new DoubleObj((double) CWND / MSS), null);
	}
	
	public boolean tcp_is_cwnd_limited(int in_flight){
		int left;
		int snd_cwnd = CWND/MSS;
		if(in_flight >= snd_cwnd)
			return true;
		left = snd_cwnd - in_flight;
		
		return left <= 3;
	}

	/**
	 * Backs off the RXT timer backoff. The largest backoff value is 64 as
	 * specified in [RFC2581].
	 */
	protected void timer_backoff() {
		if (backoff <64) {
			backoff <<= 1;
			if (isDebugEnabled() && isDebugEnabledAt(DEBUG_TIMEOUT)) {
				debug("TIMEOUT: timer backoff to " + backoff + " times");
			}
		}

		if (backoff > 8) {
			// If backed off this far, clobber the srtt
			// value, storing it in the mean deviation instead. (XX: why?)
			t_rttvar += (t_srtt >> srtt_bits);
			t_srtt = 0;
		}
	}

	protected synchronized void timeout(Object evt_) {
		if(isDebugEnabled() || isDebugEnabledAt(TCPConstants.DEBUG_TIMEOUT)) debug("enter timeout " + evt_);
		switch (((TM_EVT) evt_).type) {
		case RXT_EVT:
			// Check if timeout was being reset or cancelled
			if (timeoutEvent != evt_) {
				// debug("OOPS, evt_=" + evt_ + ", timeoutEvent="
				// + timeoutEvent);
				return;
			}

			// connection is reset
			if (state != ESTABLISHED && state != CLOSE_WAIT) {
				switch (state) {
				case SYN_SENT:
					// timeout when listening to syn ack
					if (!notifiedApplication) {
						upPort.doSending(new ByteStreamContract.Message(
								ByteStreamContract.ERROR, "SYN ACK timeout"));
						state = CLOSED;
						error(evt_, "timeout", null, "SYN ACK timeout");
					}
					 drcl.ruv.System.finish();
					break;
				case FIN_WAIT_1:
					state = CLOSED;
					if (isDebugEnabled())
						debug("connnection is closed without ack from the server.");
					// drcl.ruv.System.finish();
					break;
				}

				return;
			}

//			// rounding error if in real-time simulation?
//			long now_ = getTime();
//			if (timeoutEvent.timeout > now_) {
//				timeoutEvent.timer = setTimeoutAt(timeoutEvent,
//						timeoutEvent.timeout);
//				System.out.println("rounding error if in real-time simulation?");
//				return;
//			}
			
			

			burst = 0;
			numTimeouts++;
			// When timeout occurs, SACK info should be invalidated
			if (recvBuffer != null)
				recvBuffer = null;

			if (isDebugEnabled()
					&& (isDebugEnabledAt(DEBUG_SAMPLE) || isDebugEnabledAt(DEBUG_TIMEOUT)))
				debug("RXT TIMEOUT: rxtseq=" + (snd_una / MSS) + "/" + snd_una);

			// Slow_start
			phase = SLOW_START;
			if (implementation == VEGAS) {
				// Note: I don't think VEGAS needs this since VEGAS is
				// already not aggressive
				// but VEGAS cannot work with RED queue since VEGAS manages
				// the buffer occupancy itself!
				// CWND = VEGAS_CWND_SLOW_START * MSS;
				vegas.windowAdjust = VEGAS_INCREASE;
				vegas.periodBegin = Double.NaN;
				if (cwndPort._isEventExportEnabled())
					cwndPort.exportEvent(CWND_EVENT, new DoubleObj(
							(double) CWND / MSS), null);
			} else if (implementation == CUBIC) {
				// CUBIC TIMEOUT
				this.tcp_enter_loss();
				if (sstPort._isEventExportEnabled())
					sstPort.exportEvent(SST_EVENT, new DoubleObj((double) sthld
							/ MSS), null);
				if (cwndPort._isEventExportEnabled())
					cwndPort.exportEvent(CWND_EVENT, new DoubleObj(
							(double) CWND / MSS), null);

				if (isDebugEnabled()
						&& (isDebugEnabledAt(DEBUG_SAMPLE) || isDebugEnabledAt(DEBUG_CWND))) {
					debug("TIMOUT: cubic reset. CWND:" + CWND);
				}
			} else { // RENO, NEW_RENO, TAHOE
				sthld = (int) (snd_wnd() >> 1) / MSS * MSS;
				if (sthld < 2 * MSS)
					sthld = 2 * MSS;
				dup_ack = 0;
				CWND = MSS;

				if (sstPort._isEventExportEnabled())
					sstPort.exportEvent(SST_EVENT, new DoubleObj((double) sthld
							/ MSS), null);
				if (cwndPort._isEventExportEnabled())
					cwndPort.exportEvent(CWND_EVENT, new DoubleObj(1.0), null);
			}

			if (backoff == maxBackoff) {
				// E.g., According to "TCP/IP Illustrated vol.2: The
				// Implementation", // TCP should drop the connection after 12
				// retrans fail. In this case, backoff == maxBackoff == 4096
				// which means this is the 13th retransmission
				if (isDebugEnabled()
						&& (isDebugEnabledAt(DEBUG_SAMPLE) || isDebugEnabledAt(DEBUG_TIMEOUT)))
					debug("RXT TIMEOUT: done " + getMaxRetransmissionsAllowed()
							+ " retransmissions, no more retrans");
//				System.out.println("stop by maxBackoff");
				// stop app from sending more bytes
				upPort.doSending(new ByteStreamContract.Message(
						ByteStreamContract.STOP, id));
			} else {
				timer_backoff();
				snd_nxt = snd_una;
				snd_maxpck("TIMEOUT");
				timeoutEvent.timer = null;
				resetRXTTimer(0, "timeout");
				// timeoutEvent.timer = setTimeoutAt(timeoutEvent,
				// timeoutEvent.timeout);
			}
			break;
		}
	}

	protected void tcp_enter_loss() {
		this.sthld = ca.ssthresh(this)*MSS;
//		sthld = (int) (snd_wnd() >> 1) / MSS * MSS;
		if (sthld < 2 * MSS)
			sthld = 2 * MSS;
		dup_ack = 0;
		CWND = MSS;
		this.numack = 0;
		this.retrans_out = 0;
		this.lost_out = 0;
		this.snd_cwnd_cnt = 0;
		if (sstPort._isEventExportEnabled())
			sstPort.exportEvent(SST_EVENT, new DoubleObj((double) sthld
					/ MSS), null);
		if (cwndPort._isEventExportEnabled())
			cwndPort.exportEvent(CWND_EVENT, new DoubleObj(1.0), null);
		

	}

	/**
	 * Sets the maximum number of retransmissions allowed. If the maximum is
	 * reached, TCP stops retransmission attempt and notifies upper layer by a
	 * STOP command ({@link ByteStreamContract}). Specify 0 or negative value
	 * for unlimited retrans.
	 */
	public void setMaxRetransmissionsAllowed(int times_) {
		if (times_ <= 0)
			maxBackoff = times_;
		else {
			maxBackoff = 1;
			for (int i = 0; i < times_; i++)
				maxBackoff <<= 1;
		}
	}

	public int getMaxRetransmissionsAllowed() {
		if (maxBackoff <= 0)
			return maxBackoff;
		else {
			int i = 0;
			for (; maxBackoff > 1; i++) {
				maxBackoff >>= 1;
			}
			return i;
		}
	}
	
	private boolean SACKRecoveryNeeded(){
		if(
				// cases satisfy SACK recovery in action
				SACK &&
				phase == FAST_RECOVERY &&
				recvBuffer != null && recvBuffer.numOfLongIntervals() > 1 &&
				snd_nxt < snd_nxt_marked 
				)
			return true;
		else
			return false;
	}
	
	private long getDataLengthByMaxBurst(long dtlen_, String debugMsg_){
		// if has max burst limitation
		if (dup_ack >= 3 && implementation != VEGAS) {
			// one packet during fast recovery
			if (dtlen_ + burst > MSS) {
				dtlen_ = MSS - burst;
				if (isDebugEnabled()
						&& (isDebugEnabledAt(DEBUG_SEND) || isDebugEnabledAt(DEBUG_SAMPLE)))
					debug(debugMsg_
							+ ": maxburst_restrict, can only send "
							+ (dtlen_ / MSS) + "/" + dtlen_);
			}
		} else {
			// three packets otherwise
			if (dtlen_ + burst > (MSS * TCP_INIT_CWND)) {
				dtlen_ = (MSS * TCP_INIT_CWND) - burst;
				if (isDebugEnabled()
						&& (isDebugEnabledAt(DEBUG_SEND) || isDebugEnabledAt(DEBUG_SAMPLE)))
					debug(debugMsg_
							+ ": maxburst_restrict, can only send "
							+ (dtlen_ / MSS) + "/" + dtlen_);
			}
		}
		return dtlen_;
	}

	/** Sends as many packets as allowed by the current window. */
	protected void snd_maxpck(String debugMsg_) {
		if(isDebugEnabled())debug("snd_maxpck " + debugMsg_);
		if (snd_nxt < snd_una)
			snd_nxt = snd_una;
		if (!SACKRecoveryNeeded()) 
		{
			// the length of sending data based on current window
			long dtlen_ = seq_max() - snd_nxt;
//			debug("cwnd = " + CWND);

			if (maxburst) 
				dtlen_ = getDataLengthByMaxBurst(dtlen_, debugMsg_);

			if (dtlen_ <= 0)	// no data is allowed to be sent, just return
			{
				if(debugMsg_.equals("TIMEOUT")) debug("no data is allowed to be sent, just return");
				return;
			}

			if (isDebugEnabled() && isDebugEnabledAt(DEBUG_SEND))
				debug(debugMsg_ + ":Gonna send " + (snd_nxt / MSS) + " ---> "
						+ (seq_max() / MSS) + "; " + win_info()
						+ ", sending_buffer:" + sbuffer);
			// segement sending data into sequential sending processes
			while (dtlen_ > 0) {
				if (dtlen_ >= MSS) {
					snd_packet(snd_nxt, MSS);
					snd_nxt += MSS;
					dtlen_ -= MSS;
					// packets_out ++;
				} else {
					// send fragment only when no new data buffered
					if (dt_max == snd_nxt + dtlen_) {
						snd_packet(snd_nxt, (int) dtlen_);
						snd_nxt += dtlen_;
						// packets_out ++;
					}
					return;
				}
			}
		} else {
			// SACK enabled (TODO double check)

			int restriction_ = 0;
			if (maxburst) {
				if (dup_ack >= 3 && implementation != VEGAS)
					// one packet during fast recovery
					restriction_ = MSS;
				else
					// two packets otherwise
					restriction_ = MSS << 1;
			}

			// iterates thru the gaps in recvBuffer
			long dtlen_ = seq_max();

			if (recvBuffer.numOfLongIntervals() == 0) {
				System.out.println("recvBuffer.numOfLongIntervals()==0");
				return;
			}

			for (Iterator<?> it_ = recvBuffer.getGapIterator(snd_nxt); it_
					.hasNext();) {
				LongInterval gap_ = (LongInterval) it_.next();
				if (gap_.end > dtlen_) {
					// have sent all missing segments
					if (snd_nxt < snd_nxt_marked)
						snd_nxt = snd_nxt_marked;
					snd_maxpck("recursive");
					return;
				}
				while (gap_.start < gap_.end) {
					int len_ = (int) (gap_.end - gap_.start);
					if (len_ > MSS)
						len_ = MSS;
					if (maxburst && burst >= restriction_) {
						if (isDebugEnabled()
								&& (isDebugEnabledAt(DEBUG_SAMPLE)
										|| isDebugEnabledAt(DEBUG_SEND) || isDebugEnabledAt(DEBUG_SACK)))
							debug(debugMsg_
									+ ": maxburst_restrict, can only send upto "
									+ (gap_.start / MSS) + "/" + gap_.start);
						return;
					}
					snd_packet(gap_.start, len_);
					this.retrans_out++;
					gap_.start += len_;
					snd_nxt = gap_.start;
				}
			}
		}
	}

	/**
	 * Sends one packet with specified starting sequence number and packet size.
	 */
	protected void snd_packet(long seqno_, int size_) {
		long now_ = getTime();
		TCPPacketPayload payload = new TCPPacketPayload(sbuffer,
				(int) (seqno_ - snd_una), size_);
		boolean rxt_ = seqno_ < snd_max; // retransmit?
		if (snd_max < seqno_ + size_)
			snd_max = seqno_ + size_;

		// Construct a packet to send
		// header size = 20 bytes + 10 bytes of timestamp option
		// don't care port numbers
		TCPPacket pkt_ = new TCPPacket(getLocalPort(), getRemotePort(), seqno_,
				getAckNo()/* ackno */,
				getAvailableRcvBuffer() >> wind_scal/* advwin */,
				false/* ack */, false/* syn */, false/* fin */, now_, 
				-1000/* aTS */, NS_COMPATIBLE ? 20 : 32, size_, payload);

		if(drcl.ruv.System.SYNC)
			syncForward(pkt_, getLocalAddr(), peer, DEFAULT_PID, false, TTL, 0);
		else
			forward(pkt_, getLocalAddr(), peer, DEFAULT_PID, false, TTL, 0);
		burst += size_;	

		
		if (implementation == VEGAS) {
			// For vegas measuring sending rate:
			// dont do measurement when SLOW_START and VEGAS_NOCHANGE
			// Vegas measures every other RTT when in slow start
			if (Double.isNaN(vegas.periodBegin)) {
				// start of a period
				vegas.periodBegin = now_;
				vegas.seqMarked = seqno_ + size_;

				if (phase != SLOW_START || vegas.windowAdjust != VEGAS_INCREASE) {
					vegas.bytesSent = size_;
					if (isDebugEnabled() && isDebugEnabledAt(DEBUG_VEGAS))
						debug("VEGAS: measure period begins, seq#_marked="
								+ vegas.seqMarked
								+ "----------------------------");
				} else {
					if (isDebugEnabled() && isDebugEnabledAt(DEBUG_VEGAS))
						debug("VEGAS: non-measurement period begins, "
								+ "seq#_marked=" + vegas.seqMarked
								+ "--------------------");
					vegas.bytesSent = 0;
				}
			} else if (phase != SLOW_START
					|| vegas.windowAdjust != VEGAS_INCREASE)
				vegas.bytesSent += size_;

			// For vegas retransmission mechanism:
			// 1. record sending time
			vegas.htSentTime.put(new Long(seqno_), new Double(now_));
			// 2. start to count effective acks after a retx
			if (rxt_)
				vegas.numack = 0;
		}

		if (seqNoPort._isEventExportEnabled())
			seqNoPort.exportEvent(SEQNO_EVENT, new DoubleObj((double) seqno_
					/ MSS), null);
//		System.out.println("tcp:send------------" + seqno_);
		if (rxt_)
			bytesRetx += size_;	
		if (isDebugEnabled()
				&& (isDebugEnabledAt(DEBUG_SAMPLE) || isDebugEnabledAt(DEBUG_SEND)))
			debug((rxt_ ? "RESending" : "Sending") + ": SeqNo="
					+ (seqno_ / MSS) + "/" + seqno_ + ", Size=" + size_ + " pkt=" +  pkt_);

	}

	/** Resets the retransmission timer. */
	protected void resetRXTTimer(long delay, String debugMsg) {
		if(isDebugEnabled() || isDebugEnabledAt(TCPConstants.DEBUG_TIMEOUT))
			debug("enter resetRXTTimer from " + debugMsg);
		if (timeoutEvent == null) { // schedule a timeout
			if (snd_una < dt_max) { // having outstanding packets
				long time_ = rxt_timer(backoff);
				timeoutEvent = new TM_EVT(RXT_EVT, time_);
				timeoutEvent.timer = setTimeout(timeoutEvent, time_);
				if (isDebugEnabled() && isDebugEnabledAt(DEBUG_TIMEOUT))
					debug("RESET_RXT_TIMER: create one at " + (time_ + getTime()));
			}
			if (state == SYN_SENT) {
//				long time_ = delay + rxt_timer(backoff);
				timeoutEvent = new TM_EVT(RXT_EVT, delay);
				timeoutEvent.timer = setTimeout(timeoutEvent, delay);
				if (isDebugEnabled() && isDebugEnabledAt(DEBUG_TIMEOUT))
					debug("RESET_RXT_TIMER for SYN: create one after " + delay);
			}
		} else {
			if (snd_una >= dt_max) { // all sent packets are ack'ed
				if (timeoutEvent.timer != null)
					cancelTimeout(timeoutEvent.timer);
				if(snd_una == 0){
					// set timer for 1st data packet.
					timeoutEvent.timeout = delay;
					timeoutEvent.timer = setTimeout(timeoutEvent, delay);
					if (isDebugEnabled() && isDebugEnabledAt(DEBUG_TIMEOUT))
						debug("RESET_RXT_TIMER: create one for 1st data packet at " + (getTime() + delay));
				}else{
					timeoutEvent = null; // cancel the timer
					if (isDebugEnabled() && isDebugEnabledAt(DEBUG_TIMEOUT))
						debug("RESET_RXT_TIMER: cancel timeout");
				}
			} else { // reset timeout
				long time_ = rxt_timer(backoff);
				cancelTimeout(timeoutEvent.timer);
				timeoutEvent = new TM_EVT(RXT_EVT, time_);
				setTimeout(timeoutEvent, timeoutEvent.timeout);
				if (isDebugEnabled() && isDebugEnabledAt(DEBUG_TIMEOUT))
					debug("RESET_RXT_TIMER: adjust timeout to " + (getTime() + time_));
//				if (timeoutEvent.timer != null
//						&& time_ >= timeoutEvent.timer.getTime()) {
//					cancelTimeout(timeoutEvent.timer);
//					timeoutEvent = new TM_EVT(RXT_EVT, time_);
//					setTimeout(timeoutEvent, timeoutEvent.timeout);
//					if (isDebugEnabled() && isDebugEnabledAt(DEBUG_TIMEOUT))
//						debug("RESET_RXT_TIMER: adjust timeout to " + time_);
//					
//				} else {
//					// re-create a timer
//					if (isDebugEnabled() && isDebugEnabledAt(DEBUG_TIMEOUT))
//						if (timeoutEvent.timer != null)
//							debug("RESET_RXT_TIMER: recreate one with delay " + time_
//									+ ", old one was with delay "
//									+ timeoutEvent.timer.getTime());
//						else
//							debug("RESET_RXT_TIMER: recreate one with delay " + time_);
//					if (timeoutEvent.timer != null)
//						cancelTimeout(timeoutEvent.timer);
//					// timeoutEvent = new TM_EVT(RXT_EVT, time_);
//					timeoutEvent.timeout = time_;
//					timeoutEvent.timer = setTimeout(timeoutEvent, time_);
//				}
			}
		}
	}

	protected void ack_syn_fin(boolean ack_, boolean syn_, boolean fin_) {
//		if(isDebugEnabled()) debug("enter ack_syn_fin " + getTime());
		int size_ = 20;
		if (syn_ && !ack_ && !fin_)
			size_ = 40;
		TCPPacket p = new TCPPacket(getLocalPort(), getRemotePort(),
				getSeqNo(), getAckNo(), getAvailableRcvBuffer() >> wind_scal,
				ack_, syn_, fin_, -1000, -1000, size_, 0, null);
		if (syn_ && !ack_ && !fin_)
			p.mss = MSS;
		if (!syn_ && !fin_) // last handshake
			p.setAckNo(1);
		if(drcl.ruv.System.SYNC)
			syncForward(p, getLocalAddr(), peer, DEFAULT_PID, false, TTL, 0);
		else
			forward(p, getLocalAddr(), peer, DEFAULT_PID, false, TTL, 0);
		if (isDebugEnabled())
			debug("forward " + p);
//		if(isDebugEnabled()) debug("exit ack_syn_fin " + getTime());
	}

	public int getLocalPort() {
		return localPort;
	}

	public int getRemotePort() {
		return remotePort;
	}

	public long getLocalAddr() {
		return drcl.net.Address.NULL_ADDR;
	}

	protected long getSeqNo() {
		return snd_nxt;
	}

	protected long getAckNo() {
		double rtt = (long) ((t_srtt >> srtt_bits) * t_grain * 1000);
//		System.out.println(rtt);
		return (long)rtt;
	}

	protected int getAvailableRcvBuffer() {
		return 0;
	}

	protected int getSendingBuffer() {
		synchronized (sbuffer) {
			return sbuffer.getSize();
		}
	}

	transient TM_EVT timeoutEvent = null;

	String win_info() {
		long max_ = seq_max();
		return "window=(" + (snd_una / MSS) + "," + (snd_nxt / MSS) + ","
				+ (max_ / MSS) + ",progress:" + ((snd_nxt - snd_una) / MSS)
				+ "/" + ((max_ - snd_una) / MSS) + ",CWMD:" + (CWND / MSS)
				+ ")" + "(" + snd_una + "," + snd_nxt + "," + max_
				+ ",progress:" + (snd_nxt - snd_una) + "/" + (max_ - snd_una)
				+ ",CWND:" + CWND + ")";
	}

	// "phase" is only informational for TAHOE, RENO and NEW_RENO
	void _updatePhase() {
		if (implementation == RENO || implementation == NEW_RENO) {
			if (phase == SLOW_START && CWND >= sthld)
				phase = CONGESTION_AVOIDANCE;
		}
	}

	String _printRecvBuffer() {
		StringBuffer sb_ = null;
		if (recvBuffer != null) {
			sb_ = new StringBuffer();
			LongInterval[] ll_ = recvBuffer.getLongIntervals();
			for (int i = 0; i < ll_.length; i++)
				sb_.append("[" + (ll_[i].start / MSS) + "/" + ll_[i].start
						+ "," + (ll_[i].end / MSS) + "/" + ll_[i].end + ")");
		}
		return sb_ == null || sb_.length() == 0 ? "empty" : sb_.toString();
	}

	public String info() {
		String sb_ = _printRecvBuffer();

		_updatePhase();
		return configInfo()
				+ "Current states:\n"
				+ "                     State = "
				+ (state >= STATES.length ? state + "(unknown)" : STATES[state])
				+ ", "
				+ PHASES[phase]
				+ "\n"
				+ "            Total progress = "
				+ (snd_una / MSS)
				+ "/"
				+ (dt_max / MSS)
				+ "---"
				+ snd_una
				+ "/"
				+ dt_max
				+ "(ack_expected/#byte_received)\n"
				+ "            Current window = ("
				+ (snd_una / MSS)
				+ ","
				+ (snd_nxt / MSS)
				+ ","
				+ (seq_max() / MSS)
				+ ")---("
				+ snd_una
				+ ","
				+ snd_nxt
				+ ","
				+ seq_max()
				+ ") (ack_expected, next_seq_to_be_sent, max_seq_can_be_sent\n"
				+ "                             note: max_seq_can_be_sent = "
				+ "min(ack_expected + min(CWND,AWND), #byte_received)\n"
				+ "Progress in current window = "
				+ ((snd_nxt - snd_una) / MSS)
				+ "/"
				+ ((seq_max() - snd_una) / MSS)
				+ "---"
				+ (snd_nxt - snd_una)
				+ "/"
				+ (seq_max() - snd_una)
				+ "\n"
				+ "        Max. sequence sent = "
				+ (snd_max / MSS - 1)
				+ "---"
				+ (snd_max - 1)
				+ "\n"
				+ "   last effective seq recv = "
				+ (last_seq / MSS)
				+ "---"
				+ last_seq
				+ "\n"
				+ " # of effective acks recvd = "
				+ numack
				+ "\n"
				+ "    Congestion window size = "
				+ (CWND / MSS)
				+ "---"
				+ CWND
				+ "\n"
				+ "    Advertized window size = "
				+ (AWND / MSS)
				+ "---"
				+ AWND
				+ "\n"
				+ "   # of Dup. Acks received = "
				+ dup_ack
				+ "\n"
				+ "# of packets/bytes sent since last ack = "
				+ (burst / MSS)
				+ "---"
				+ burst
				+ "\n"
				+ ((implementation == RENO || implementation == NEW_RENO)
						&& dup_ack >= 3 ? "            snd_nxt_marked = "
						+ (snd_nxt_marked / MSS) + "---" + snd_nxt_marked
						+ "\n" : "")
				+ "Congestion avoidance thrhd = "
				+ (sthld / MSS)
				+ "---"
				+ sthld
				+ "\n"
				+ "  # of bytes retransmitted = "
				+ bytesRetx
				+ "\n"
				+ " # coarse-grained timeouts = "
				+ numTimeouts
				+ "\n"
				+ "            Sending buffer = "
				+ sbuffer
				+ "\n"
				+ (SACK ? " Reconstructed recv buffer = " + sb_ + "\n" : "")
				+ "\nRTT and Timout:\n"
				+ "        Clock tick = "
				+ t_grain
				+ "\n"
				+ "       Current RTT = "
				+ cur_rtt
				+ "\n"
				+ "      Smoothed RTT = "
				+ ((t_srtt >> srtt_bits) * t_grain)
				+ "\n"
				+ "     RTT variation = "
				+ ((t_rttvar >> rttvar_bits) * t_grain)
				+ "\n"
				+ "Current retx. base = "
				+ t_rtxcur
				+ "\n"
				+ "max. retx. allowed = "
				+ getMaxRetransmissionsAllowed()
				+ "\n"
				+ "     Retx. backoff = "
				+ backoff
				+ "\n"
				+ "     Retx. Timeout = "
				+ (timeoutEvent == null ? "no timeout event\n" : timeoutEvent
						+ "\n")
				+ " Init Smoothed RTT = "
				+ ((srtt_init >> srtt_bits) * t_grain)
				+ "\n"
				+ "Init RTT variation = "
				+ ((rttvar_init >> rttvar_bits) * t_grain)
				+ "\n"
				+ "   Init retx. base = "
				+ rtxcur_init
				+ "\n"
				+ (implementation == VEGAS ? "\nVEGAS variables:\n"
						+ "        # retransmissions = "
						+ vegas.numRetx
						+ "\n"
						+ "#effective ack after retx = "
						+ vegas.numack
						+ "\n"
						+ "             windowAdjust = "
						+ VEGAS_ADJUST[vegas.windowAdjust]
						+ "\n"
						+ "                 base_rtt = "
						+ vegas.base_rtt
						+ "\n"
						+ "  begin of measure period = "
						+ vegas.periodBegin
						+ ", seq# = "
						+ vegas.seqMarked
						+ "\n"
						+ "               bytes sent = "
						+ vegas.bytesSent
						+ "\n"
						+ "       recorded sent_time = "
						+ vegas.htSentTime
						+ "\n" : "");
	}

	public String configInfo() {
		return "Implementation = " + implementation + "\n"
				+ "           MSS = " + MSS + "\n" + "       MAXCWND = "
				+ MAXCWND + "\n" + "          AWND = " + AWND + "\n"
				+ "          SACK = " + SACK + "\n" + "      maxburst = "
				+ maxburst + "\n" + "          Peer = " + peer + "\n";
	}

	void vegas_handleAck(long ackseq_, boolean considerRetx_, long now_) {
		double sentTime_ = Double.NaN; // sent time of "ackseq_"
		// purge the records with seq# < ackseq_
		for (Enumeration e_ = vegas.htSentTime.keys(); e_.hasMoreElements();) {
			Long seq_ = (Long) e_.nextElement();
			if (seq_.longValue() < ackseq_)
				vegas.htSentTime.remove(seq_);
			else if (considerRetx_ && seq_.longValue() == ackseq_)
				sentTime_ = ((Double) vegas.htSentTime.get(seq_)).doubleValue();
		}

		// FIXME: use srtt?
		if (now_ - sentTime_ > (t_srtt >> srtt_bits) * t_grain) {
			// retransmit the segment, the sent time is recorded in snd_packet()
			snd_packet(ackseq_, Math.min(MSS, sbuffer.getSize()));
			resetRXTTimer(now_, "handleAck");
			vegas.numRetx++;
			if (isDebugEnabled() && isDebugEnabledAt(DEBUG_VEGAS))
				debug("VEGAS: in advance retransmit: " + (ackseq_ / MSS) + "/"
						+ ackseq_ + ", timeout adjusted to "
						+ (getTime() + rxt_timer(backoff)));
		}
	}

	// Timeout event class. It is used to hold the necessary information
	// for a scheduled timeout.
	class TM_EVT {
		int type; // Timeout type
		long timeout; // time of timeout
		ACATimer timer;

		public TM_EVT(int type_, long timeout_) {
			type = type_;
			timeout = timeout_;
		}

		public String toString() {
			if (timer == null)
				return TIMEOUT_TYPES[type] + ":" + timeout;
			else
				return TIMEOUT_TYPES[type] + ":" + timeout + "("
						+ timer.getTime() + ")";
		}
	}

	// fields and methods for real Internet transmission

	/**
	 * set remote peer on real Internet transmission
	 * 
	 * @param ip
	 * @param port
	 */
	public void setPeer(String ip, int port) {
		try {
			this.peer = MobibedUtils.byteArrayToLong(InetAddress.getByName(ip)
					.getAddress());
			this.remotePort = port;

		} catch (UnknownHostException e) {
			error(ip, "dataArriveAtUpPort()", null, e);
			e.printStackTrace();
		}

	}

	@Override
	public void setLocalPort(int port) {
		this.localPort = port;
	}

}
