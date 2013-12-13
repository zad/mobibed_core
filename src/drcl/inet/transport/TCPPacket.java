// @(#)TCPPacket.java   1/2004
// Copyright (c) 1998-2004, Distributed Real-time Computing Lab (DRCL) 
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

import com.sun.org.apache.regexp.internal.RE;

import drcl.inet.InetPacket;
import drcl.inet.host.MobibedUtils;
import drcl.net.Packet;
import drcl.util.CircularBuffer;
import drcl.util.StringUtil;

/**
This class defines the TCP packet header.
Fields implemented in this class are
SOURCE_port, DEST_port, SEQNo, ACKNo, AdvertisedWnd, ACK, SYN, FIN, SACK option,
timestamp option.

RFC requirements NOT implemented in this class are
URG, PSH, RST, TCP checksum, Urgent pointer, and other options

References:
<ul>
<li>[TCPILL1] W. Stevens, TCP/IP Illustrated vol.1: The Protocols, Addison-Wesley,1994.
<li>[TCPILL2] G. Wright and W. Stevens, TCP/IP Illustrated vol.2: The Implementation, Addison-Wesley,1995
<li>[RFC793] J. Postel, Transmission Control Protocol, September 1981.
<li>[RFC2018] M. Mathis, J. Mahdavi, S. Floyd and A. Romanow, TCP Selective Acknowledgment Options, Octobor 1996.
<li>[RFC2581] M. Allman, V. Paxson and W. Stevens, TCP Congestion Control, April 1999.
</ul>
 */
public class TCPPacket extends Packet
{
	/** Size of maximum transmission unit, used to set/calculate packet count from sequence number.*/
	public static long MSS = 512;
	public static final int FLAG_ACK = 1;
	public static final int FLAG_SYN = 2;
	public static final int FLAG_FIN = 4;
	public static final int FLAG_SACK = 8;
	public static final int FLAG_RST = 16;

	// standard fields
	private int sport;
	private int dport;
	private	long SeqNo;		/* sequence number */
	private	long AckNo;		/* ACK number for FullTcp */
	private	int AdvWin;	/* Advertised Window */
	
	// flags
	int flag;

	// tcp options
	private	long TS;		/* time packet generated (at source) (ms) */
	private long aTS;		/* time packet generated (at sink) (ms) */
	private int sackLen;
	private long[] LEblk, REblk; // Left and right edge of blocks
	public static int scale = 7; // window scale (send with SYN)
	public int mss;
	private InetPacket ipkt; 
	
	

	public void reset(){
		super.reset();
		sport = 0;
		dport = 0;
		SeqNo = 0;
		AckNo = 0;
		AdvWin = 0;
		flag = 0;
		TS = 0;
		aTS = 0;
		sackLen = 0;
		LEblk = null;
		REblk = null;
		ipkt = null;
		
	}
	
	public String getName()
	{ return "TCP"; }

	public TCPPacket()
	{ super(20); }
	
	public TCPPacket(InetPacket ipkt_)
	{ super(20); ipkt = ipkt_; }

	/**
	 * Constructor for TCPPacket without SACK option.
	 *
	 * @param Seqno_ Sequence number of data
	 * @param AckNo_ Acknowledge sequence number
	 * @param AdvWin_ Advertised window
	 * @param Ack_ flag ACK 
	 * @param SYN_ flag SYN
	 * @param FIN_ flag FIN
	 * @param ts_ Timestamp
	 * @param ats_ Ackknowledge Timestamp
	 * @param hsize_ header size, calculated by caller
	 * @param bsize_ body size
	 * @param body_ body 
	 */
	public TCPPacket (int sport_, int dport_, long Seqno_, long AckNo_,
			int AdvWin_, boolean Ack_, boolean SYN_, boolean FIN_,
			long ts_, long ats_, int hsize_, int bsize_, Object body_)
	{
		super(hsize_,bsize_,body_);
//		System.out.println("hs:" + hsize_ + " bs " + bsize_);
		sport = sport_;
		dport = dport_;
		SeqNo = Seqno_;
		AckNo = AckNo_;
		AdvWin = AdvWin_;
		flag = 0;
		if (Ack_) setACK(Ack_);
		if (SYN_) setSYN(SYN_);
		if (FIN_) setFIN(FIN_);
		setSACK(false);
		TS = ts_;
		aTS = ats_;
	}

	/**
	 * Constructor for TCPPacket with SACK option.
	 * @param Seqno_ Sequence number of data
	 * @param AckNo_ Acknowledge sequence number
	 * @param AdvWin_ Advertised window
	 * @param Ack_ flag ACK 
	 * @param SYN_ flag SYN
	 * @param FIN_ flag FIN
	 * @param ts_ Timestamp
	 * @param ats_ Ackknowledge Timestamp
	 * @param sack_ flag SACK 
	 * @param sackLen_ # of SACK blocks 
	 * @param hsize_ header size, calculated by caller
	 * @param bsize_ body size
	 * @param body_ body 
	 */
	public TCPPacket (int sport_, int dport_, long Seqno_, long AckNo_,
		int AdvWin_,
		boolean Ack_, boolean SYN_, boolean FIN_, long ts_, long ats_,
		boolean sack_, int sackLen_, int hsize_, int bsize_, Object body_)
	{
		super(hsize_,bsize_,body_);
		sport = sport_;
		dport = dport_;
		SeqNo = Seqno_;
		AckNo = AckNo_;
		AdvWin = AdvWin_;
		if (Ack_) setACK(Ack_);
		if (SYN_) setSYN(SYN_);
		if (FIN_) setFIN(FIN_);
		if (sack_) setSACK(sack_);
		TS = ts_;
		aTS = ats_;
		if (sack_) {
			sackLen = sackLen_;
			REblk = new long[sackLen_];
			LEblk = new long[sackLen_];
		}
	}

	private TCPPacket (int sport_, int dport_, long Seqno_, int AdvWin_,
		int flag_, long AckNo_, long ts_, long ats_,
		int sackLen_, long[] LEblk_, long[] REblk_,
		int hsize_, int bsize_, Object body_)
	{
		super(hsize_,bsize_,body_);
		sport = sport_;
		dport = dport_;
		SeqNo = Seqno_;
		AckNo = AckNo_;
		AdvWin = AdvWin_;
		flag = flag_;
		TS = ts_;
		aTS = ats_;
		sackLen = sackLen_;
		LEblk = LEblk_;
		REblk = REblk_;
	}

	public static TCPPacket createRST(int sport_, int dport_, int hsize_)
	{
		TCPPacket p = new TCPPacket(sport_, dport_, -1L, -1L, 0, false, false,
						false, 0, 0, hsize_, 0, null);
		p.setRST(true);
		return p;
	}

	public int getPacketCount()
	{
		if (body instanceof Packet)
			return ((Packet)body).getPacketCount();
		else
			return (int)(SeqNo/MSS);
	}
	
	public long getByteCount()
	{
		if (body instanceof Packet)
			return ((Packet)body).getByteCount();
		else
			return SeqNo;
	}

	public int getDPort()
	{ return dport; }

	public int getSPort()
	{ return sport; }
	
	public long getTS()
	{ return TS; }

	public void setTS(long ts_)
	{TS = ts_; }

	public long getaTS()
	{ return (aTS); }

	public void setaTS(long aTS_)
	{ aTS=aTS_; }

	public void setSeqNo(long seqno_)
	{ SeqNo = seqno_; }

	public long getSeqNo()
	{ return SeqNo; }

	public long getAckNo()
	{ return AckNo; }
	
	public void setAckNo(long ack)
	{ this.AckNo = ack;}
	
	public int getAdvWin()
	{ return AdvWin; }

	public void setACK(boolean enabled_)
	{
		if (enabled_) flag |= FLAG_ACK;
		else flag &= ~FLAG_ACK;
	}

	public boolean isACK()
	{ return (flag & FLAG_ACK) != 0;}

	public void setFlag(int flag_, boolean enabled_)
	{
		if (enabled_) flag |= flag_;
		else flag &= ~flag_;
	}

	public void setSYN(boolean enabled_)
	{
		if (enabled_) flag |= FLAG_SYN;
		else flag &= ~FLAG_SYN;
	}

	public boolean isSYN()
	{ return (flag & FLAG_SYN) != 0;}

	public void setFIN(boolean enabled_)
	{
		if (enabled_) flag |= FLAG_FIN;
		else flag &= ~FLAG_FIN;
	}

	public boolean isFIN()
	{ return (flag & FLAG_FIN) != 0;}

	public void setSACK(boolean enabled_)
	{
		if (enabled_) flag |= FLAG_SACK;
		else flag &= ~FLAG_SACK;
	}

	public boolean isSACK()
	{ return (flag & FLAG_SACK) != 0;}

	public void setRST(boolean enabled_)
	{
		if (enabled_) flag |= FLAG_RST;
		else flag &= ~FLAG_RST;
	}

	public boolean isRST()
	{ return (flag & FLAG_RST) != 0;}

	public int getSACKLen()
	{ return sackLen; }

	public void setSACKLen (int len_)
	{ sackLen = len_; }

	public long[] getLEblk ()
	{ return LEblk; }

	public void setLEblk (long[] LEblk_)
	{ LEblk = LEblk_; }

	public long[] getREblk ()
	{ return REblk; }

	public void setREblk (long[] REblk_)
	{ REblk = REblk_; }

	public void setSACKBlocks (long[] LEblk_, long[] REblk_)
	{
		LEblk = LEblk_;
		REblk = REblk_;
	}

	public Object clone()
	{
		TCPPacket p = new TCPPacket(sport, dport, SeqNo, AdvWin, flag, AckNo, TS, aTS,
				sackLen, LEblk, REblk, headerSize, size-headerSize,
				(body instanceof drcl.ObjectCloneable?
				 	((drcl.ObjectCloneable)body).clone(): body));
		p.mss = mss;
		return p;
	}

	/*
	public void duplicate(Object source_)
	{
		super.duplicate(source_);
		TCPPacket that_ = (TCPPacket)source_;
		sport = that_.sport;
		dport = that_.dport;
		TS = that_.TS;
		aTS = that_.aTS;
		SeqNo = that_.SeqNo;
		AckNo = that_.AckNo;
		AdvWin = that_.AdvWin;
		flag = that_.flag;
		sackLen = that_.sackLen;
		LEblk = that_.LEblk;
		REblk = that_.REblk;
	}
	*/

	public String getPacketType()
	{
		if (flag == 0)
			return "TCP";
		else if (isRST())
			return "TCP-RST";
		else if (isSYN())
			return isACK()? "TCP-SYN-ACK": "TCP-SYN";
		else if (isFIN())
			return isACK()? "TCP-FIN-ACK": "TCP-FIN";
		else if (size == headerSize)
			return isSACK()? "TCP-SACK": "TCP-ACK";
		else
			return isSACK()? "TCP/SACK": "TCP/ACK";
	}

	public String _toString(String separator_)
	{
		if (isRST())
			return "s:" + sport + separator_ + "d:" + dport
					+ separator_ + "RST";
		else
			return "s:" + sport + separator_ + "d:" + dport
				+ separator_ + "seq" + SeqNo
				+ separator_ + "AWND:" + AdvWin
				+ separator_ + "TS" + TS
				+ (isSYN() || isFIN() || isACK()?
					separator_ + (isSYN()? "Syn": "") + (isFIN()? "Fin": "")
				+ (isACK()? "Ack:" + AckNo + "," + aTS: ""): "")
				+ (isSACK()? separator_ + "SACK" + sackLen + ","
					+ StringUtil.toString(LEblk) + "-"
					+ StringUtil.toString(REblk): "");
	}


	
	
	@Override
	public int encode(byte[] bytes, int pos) {
		int offset = this.headerSize >> 2;
		int len;
		bytes[pos+13] = 0;
		
		for(int i=0;i<this.headerSize;i++)
			bytes[pos+i] = 0;
			
		if(flag == 0)
		{
			// read payload to bytes from sending buffer
			TCPPacketPayload payload = (TCPPacketPayload) this.body;
			len = this.size;
			payload.copy(bytes, pos+headerSize);
		}
		else{
//			bytes = new byte[headerSize];
			len = this.headerSize;
			
			if(this.isACK())
				bytes[pos+13] |= 0x10;
			if(this.isSYN())
				bytes[pos+13] |= 0x2;
			if(this.isFIN())
				bytes[pos+13] |= 1;
		}
		
		// source port
		bytes[pos] = (byte) ((sport>>8) & 0xff);
		bytes[pos+1] = (byte) (sport & 0xff);
		// destination port
		bytes[pos+2] = (byte) ((dport>>8) & 0xff);
		bytes[pos+3] = (byte) (dport & 0xff);
		// sequence number
		long seq = this.getSeqNo();
		MobibedUtils.LongToByteArray(seq, bytes, pos+4);
		
		// acknowledgment number
		long ack = this.getAckNo();
		MobibedUtils.LongToByteArray(ack, bytes, pos+8);
		
		// Offset and Reserved
		bytes[pos+12] = (byte) ((byte) offset << 4);
		// Window 14 15
//		byte[] wind = JHUUtil.LongToByteArray(AdvWin);
//		System.out.println("ad:"+AdvWin);
		bytes[pos+14] = (byte) ((AdvWin>>8) & 0xff);
		bytes[pos+15] = (byte) (AdvWin & 0xff);
		
//		System.arraycopy(wind, 0, bytes, 14, 4);
		// checksum
		long checksum = MobibedUtils.calculateChecksum(bytes, pos+16);
		bytes[pos+16] = (byte) (checksum >> 8);
		bytes[pos+17] = (byte) (checksum & 0xff); 
		if(this.headerSize > 20 && !isSYN()){
			// tcp timestamp option
			bytes[pos+20] = 0x01;
			bytes[pos+21] = 0x01;
			bytes[pos+22] = 0x08;
			bytes[pos+23] = 10;
			if(this.TS>0){
				MobibedUtils.LongToByteArray((long) TS, bytes, pos+24);
			}
			if(this.aTS>0){
				MobibedUtils.LongToByteArray((long) aTS, bytes, pos+28);
			}
			if(this.sackLen>0){
				// tcp sack option
				bytes[pos+32] = 0x01;
				bytes[pos+33] = 0x01;
				bytes[pos+34] = 0x05;
				bytes[pos+35] = (byte) (2+this.sackLen*8);
				int p = 36;
				for(int i=0;i<this.sackLen;i++){
					MobibedUtils.LongToByteArray(this.LEblk[i], bytes, pos+p);
					p+=4;
					MobibedUtils.LongToByteArray(this.REblk[i], bytes, pos+p);
					p+=4;
				}
			}
		}else if(isSYN() && !isACK())
		{
			// tcp option XXX
			// mss
			bytes[pos+20] = 0x02;
			bytes[pos+21] = 0x04;

			bytes[pos+22] = (byte) (mss>>8);
			
			bytes[pos+23] = (byte) (mss & 0xff);
			// sack ture
			bytes[pos+24] = 04;
			bytes[pos+25] = 02;
			// timestamp
			bytes[pos+26] = 0x08;
			bytes[pos+27] = 10;
			if(this.TS>0){
				MobibedUtils.LongToByteArray((long) TS, bytes, pos+28);
			}
			if(this.aTS>0){
				MobibedUtils.LongToByteArray((long) aTS, bytes, pos+32);
			}
			// window scale
			bytes[pos+36] = 1;
			bytes[pos+37] = 3;
			bytes[pos+38] = 3;
			bytes[pos+39] = (byte) scale;
		}

		
		return len;
	}
	
	

	public boolean decode(byte[] bytes, int pos, int len, int port){
		// source port
		sport = port;
		// destination port
		dport = ((bytes[pos+2] & 0xff)<<8)+(bytes[pos+3] & 0xff);
		// sequence number
		this.setSeqNo(MobibedUtils.byteArrayToLong(bytes, pos+4));
		// acknowledgment number
		this.AckNo = MobibedUtils.byteArrayToLong(bytes, pos+8);
		this.headerSize = (bytes[pos+12] & 0xff) >> 2;
		this.size = len;
		if(bytes[pos+13] == 0){
			// XXX data
			
//			this.body = new byte[size-headerSize];
//			System.arraycopy(bytes, pos+headerSize, body, 0, size-headerSize);
		}
		if((bytes[pos+13] & 1) > 0){
			// fin
			this.setFIN(true);
		}
		if((bytes[pos+13] & 2) > 0){
			// syn
			this.setSYN(true);
		}
		if((bytes[pos+13] & 0x10) > 0){
			this.setACK(true);
			
		}
		// AdvWin
		this.AdvWin = ((bytes[pos+14] & 0xff)<<8)+(bytes[pos+15] & 0xff);
		
//		System.out.println(AdvWin);
		// XXX
		if(headerSize > 20 && !isSYN()){
			// has tcp option
			int kind = bytes[pos+22] & 0xff;
			int len_ = bytes[pos+23] & 0xff;
			if(kind==8 && len_==10)
			{
				// has tcp timestamp option
				TS = MobibedUtils.byteArrayToLong(bytes, pos+24);
//				System.out.println(TS);
				aTS = MobibedUtils.byteArrayToLong(bytes, pos+28);
				if(TS < 100)
					TS = -1000;
				if(aTS < 100)
					aTS = -1000;
			}
			if(headerSize > 32){
				this.setSACK(true);
				kind = bytes[pos+34] & 0xff;
				len_ = bytes[pos+35] & 0xff;
				// has tcp sack option
				this.sackLen = (len_-2)/8;
				this.LEblk = new long[sackLen];
				this.REblk = new long[sackLen];
				int p = pos + 36;
				for(int i=0;i<sackLen;i++){
					this.LEblk[i] = MobibedUtils.byteArrayToLong(bytes, p);
					p+=4;
					this.REblk[i] = MobibedUtils.byteArrayToLong(bytes, p);
					p+=4;
				}
			}
		} else if(headerSize > 20 && isSYN() && !isACK()){
			scale = bytes[pos+39];
			System.out.println("-------------------scale = " + scale);
		}
		return true;
	}
	
	public void setSPort(int port_){
		sport = port_;
	}

	@Override
	public int encode(int pos) {
		// TODO Auto-generated method stub
		return 0;
	}

	public void free() {
		ipkt.free();
	}


}
