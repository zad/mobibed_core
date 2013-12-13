package drcl.inet.transport;

import drcl.util.CircularBuffer;

public class TCPPacketPayload {

	CircularBuffer buffer;
	int start;
	int size;
	public TCPPacketPayload(CircularBuffer buffer_, int start_, int size_){
		buffer = buffer_;
		start = start_;
		size = size_;
	}
	public void copy(byte[] bytes, int i) {
		// TODO Auto-generated method stub
		buffer.copy(start, bytes, i, size);
	}

}
