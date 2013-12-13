// @(#)UDPPacket.java   1/2004
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

import drcl.net.Packet;

/** This class defines the UDP packet header used by {@link UDP}.  */
public class UDPPacket extends Packet
{ 
	public String getName()
	{ return "UDP"; }

	int sport, dport;

	public UDPPacket()
	{ super(8); }

	/**
	 * @param sport_ source port number
	 * @param dport_ destination port number
	 */
	public UDPPacket (int sport_, int dport_, int hsize_)
	{
		super(hsize_);
		sport = sport_;
		dport = dport_;
	}
	
	/**
	 * @param sport_ source port number
	 * @param dport_ destination port number
	 */
	public UDPPacket (int sport_, int dport_, int hsize_, int bsize_, Object body_)
	{
		super(hsize_, bsize_, body_);
		sport = sport_;
		dport = dport_;
	}
	
	/** Returns the source port number of the UDP packet. */
	public int getSPort()
	{ return sport; }
	
	/** Returns the destination port number of the UDP packet. */
	public int getDPort()
	{ return dport; }
	
	public void setSPort(int value_)
	{ sport = value_; }
	
	public void setDPort(int value_)
	{ dport = value_; }
	
	/*
	public void duplicate(Object source_)
	{
		super.duplicate(source_);
		UDPPacket that_ = (UDPPacket)source_;
		sport = that_.sport;
		dport = that_.dport;
		setHeaderSize(that_.headerSize);
	}
	*/

	public Object clone()
	{
		return new UDPPacket(sport, dport, headerSize, size-headerSize,
			body instanceof drcl.ObjectCloneable?
				((drcl.ObjectCloneable)body).clone(): body);
	}

	public String _toString(String separator_)
	{ return "s:" + sport + separator_ + "d:" + dport; }

	
	public byte[] toBytes() {
		// package into a udp packet
		byte[] body_ = (byte[]) this.body;
		int length = 8 + body_.length;
		byte[] bytes = new byte[length];
		// source port
		bytes[0] = (byte) (sport/0x100 & 0xff);
		bytes[1] = (byte) (sport & 0xff);
		// destination port
		bytes[2] = (byte) (dport/0x100 & 0xff);
		bytes[3] = (byte) (dport - bytes[2]*256);
		// length
		bytes[4] = (byte) (length/0x100 & 0xff);
		bytes[5] = (byte) (length & 0xff);
		// checksum
		bytes[6] = 0;
		bytes[7] = 0;
		// body
		System.arraycopy(body_, 0, bytes, 8, body_.length);
		return bytes;
	}
	
	/**
	 * convert bytes into udp packet
	 * @param bytes
	 * @param pos pointer to start
	 * @param len
	 * @return false if occurs errors
	 */
	public boolean toPacket(byte[] bytes, int pos, int port){
		// get source port
		sport = port;
		dport = (bytes[pos+2]+256)%256 * 256 + (bytes[pos+3]+256)%256;
		int len = bytes[pos+4]*0x100 + (bytes[pos+5]+256)%256;
		body = new byte[len-8];
		System.arraycopy(bytes, pos+8, body, 0, len-8);
		return true;
	}

	@Override
	public int encode(int pos) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public int encode(byte[] bytes, int pos) {
		// TODO Auto-generated method stub
		return 0;
	}
}
