package drcl.inet.transport;

import drcl.comp.Port;
import drcl.inet.InetPacket;
import drcl.inet.Protocol;

public class EchoCP extends Protocol{
	
	

	@Override
	protected void dataArriveAtDownPort(Object data_, Port downPort_) {
		((InetPacket)data_).free();
		InetPacket re = InetPacket.poll();
		forward(re, 0, 0, false, 0, 0);
	}

	
}
