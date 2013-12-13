package drcl.inet.tool;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import drcl.comp.Extension;
import drcl.comp.Port;
import drcl.comp.contract.EventContract;
import drcl.inet.InetPacket;

public class PCapTrace extends Extension{
	/** The ID of the "up" port group. */
	public static final String PortGroup_IN   = "in";
	/** The default "down" port. */
	public Port inPort = addPort(PortGroup_IN, false/*not removable*/);
	
	OutputStream out = null;
	String output = "output";
	byte[] pcap_hrd;
	
	public byte[] pcap_buf = new byte[10240];
	
	public void open(String fname_)
	{
		try {
			output = fname_;
			if (out != null) try { out.close(); } catch (Exception e_) {}
//			if (drcl.ruv.System.Android){
//				File f = new File("sdcard/"+fname_);
//				f.createNewFile();
//				out = new FileOutputStream(f);
//			}
//			else
			out = new FileOutputStream(fname_);
			pcap_hrd = new byte[24];
			pcap_hrd = constructPCapHeader(pcap_hrd);
			out.write(pcap_hrd);
		}
		catch (Exception e_) {
			error("open()", e_);
		}
	}
	public void close()
	{
		try {
			if (out != null) out.close();
		}
		catch (Exception e_) {
			error("close()", e_);
		}
	}
	
	protected void finishing(){
		super.finishing();
		this.close();
	}
	
	public PCapTrace(){
		super();
	}
	
	public PCapTrace(String id_){
		super(id_);
	}
	
	protected synchronized void process(Object data_, Port inPort_) 
	{
		if(data_ instanceof EventContract.Message) {
			EventContract.Message s_ = (EventContract.Message)data_;
			if(isDebugEnabled())
				debug(s_.getTime() + "\t" + s_.getEvent());
			if(s_.getEvent() instanceof InetPacket){
				synchronized(pcap_buf){
					InetPacket pkt = (InetPacket)(s_.getEvent());
					int len = pkt.toPcapFormat(pcap_buf, s_.getTime());
					if(drcl.ruv.System.Android){
						byte[] pcap = new byte[len];
						System.arraycopy(pcap_buf, 0, pcap, 0, len);
						drcl.ruv.System.addPcapTrace(pcap);
						return;
					}
					String desc = (String) s_.getDescription();
					// if new data come in
					try {
						if(out==null)
							open("mobibed_client.pcap");
						out.write(pcap_buf, 0, len);
						// test one packet
							out.flush();
	//						out.close();
	//						out = null;
					} catch (IOException e) {
						e.printStackTrace();
					}	
				}
			}
		}
	}

	public static byte[] constructPCapHeader(byte[] pcap){
		// pcap header
		if(pcap.length<24){
			return null;
		}
		// magic
		pcap[3] = (byte) 0xa1;
		pcap[2] = (byte) 0xb2;
		pcap[1] = (byte) 0xc3;
		pcap[0] = (byte) 0xd4;
		// version_major
		pcap[4] = 2;
		// version minor
		pcap[6] = 4;
		// snaplen
		pcap[16]= (byte) 0xff;
		pcap[17]= (byte) 0xff;
		// link type : 101, raw ip no link [18~21]
		pcap[20]=101;
		return pcap;
	}
	
	private void test(){
		try {
			if (out != null) try { out.close(); } catch (Exception e_) {}
			out = new FileOutputStream("test1.pcap");
			
			byte[] data = new byte[24]; 
			data = constructPCapHeader(data);
			out.write(data);
		}
		catch (Exception e_) {
			error("open()", e_);
		}
	}
	
	public static void main(String[] args){
		PCapTrace pcap = new PCapTrace();
		pcap.open("test1.pcap");
		pcap.test();
		pcap.close();
	}
}
