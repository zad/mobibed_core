package drcl.inet.application;

import drcl.inet.InetPacket;

import drcl.net.Packet;

public class InetPacketBuilder {
	private InetPacket ipkt;
	public InetPacketBuilder(){
		ipkt = InetPacket.poll();
	}
	
	public InetPacketBuilder setHeader(long src_, long des_, int ulp_, int ttl_, int hops_,
			boolean ra_, long tos_, int id_, int flag_, int fragment_, Packet pkt_,
			int pktsize_){
		ipkt.setSource(src_);
		ipkt.setDestination(des_);
		ipkt.setProtocol(ulp_);
		ipkt.setTTL(ttl_);
		ipkt.setHops(hops_);
		ipkt.setRouterAlertEnabled(ra_);
		ipkt.setTOS(tos_);
		ipkt.setID(id_);
		ipkt.setFlag(flag_);
		ipkt.setFragmentOffset(fragment_);
		ipkt.setBody(pkt_);
		ipkt.setSize(pktsize_+20, 20);
		return this;
	}
	
	
	public InetPacket build() {
		return ipkt;
	}
}
