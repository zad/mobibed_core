package drcl.inet.host;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.concurrent.ConcurrentLinkedQueue;

import drcl.comp.ACARuntime;
import drcl.comp.ARuntime;
import drcl.comp.AWorkerThread;
import drcl.comp.ActiveComponent;
import drcl.comp.Component;
import drcl.comp.Port;
import drcl.comp.Task;
import drcl.comp.TaskSpecial;
import drcl.inet.InetPacket;
import drcl.inet.host.BufferArray;
import drcl.inet.host.MobibedUtils;
import drcl.inet.transport.TCPPacket;
import drcl.inet.transport.UDPPacket;
import drcl.inet.transport.Connection;
import drcl.mobibed.process.MobibedRuntime;
import drcl.util.TimeLog;
import drcl.util.queue.NonBlockingQueue;

/**
 * Socket Component for MobiBed Tasks
 * @author andong
 *
 */
public class SyncMobibedSocket extends drcl.net.Module 
	implements ActiveComponent{

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	public static boolean SOCKET_ON = false;
	public static final String PCAP_EVENT = "PCAP";
	/** The pcap trace "pcap" port. */
	public Port pcapPort = addEventPort("pcap");
	private int ntest = 0;
	
	
	protected long localAddr;
	
	private boolean server = false;
	private InetAddress remote_ip;
	private int remote_port;
	
	public SyncMobibedSocket(){
		super();
	
	}
	
	public void setServer(int port)
	{
		server = true;
		localPort = port;
	}
	
	public void connectServer(String remote_ip, int remote_port){
		try {
			this.remote_ip = InetAddress.getByName(remote_ip);
		} catch (UnknownHostException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		this.remote_port = remote_port;
	}
	
	public void setGlobalAddr(String addr)
	{
		try {
			localAddr = MobibedUtils.byteArrayToLong(
					InetAddress.getByName(addr).getAddress());
		} catch (UnknownHostException e) {
			e.printStackTrace();
		}
	}
	
//	{
//		if(localAddr==0)
//			localAddr = getGlobalAddr();
//	}
	
	protected long getGlobalAddr() {
		
		try{
			URL url = new URL("http://testip.edpsciences.org");
			Object content = url.getContent();
			String contentStr; 
			if (content instanceof InputStream) {
				/* we got a Stream from which to read the remote content, so read it */
				BufferedReader br;
				br = new BufferedReader(new InputStreamReader((InputStream)content));
				contentStr = br.readLine();
				while(contentStr.indexOf("Your ip address") == -1)
				{	
					contentStr = br.readLine();
				}
				// do a little substringing, and we'll have our answer 
				// (the ip address is inbetween "<B> " and " </B>"
				String ipAddr = contentStr.substring(contentStr.indexOf("ip_value\">")+10, 
						contentStr.indexOf("</span>"));
				if(isDebugEnabled()) debug(ipAddr);
				br.close();
				return MobibedUtils.byteArrayToLong(
						InetAddress.getByName(ipAddr).getAddress());
			}			
		}catch (Exception e){
			e.printStackTrace();
		}
		return 0;
	}

	public SyncMobibedSocket(String id_){
		super(id_);
	}
	
	protected DatagramSocket datagramSocket = null; 
	protected int localPort;
	protected InetAddress remoteIPAddress;
	
	/**
	 * start connection on port
	 */
	public void _start(){
		setLocalSocket();
		openSocket();
		receiving();
	}
	
	protected void setLocalSocket() {
		if(!upPort.anyConnection())
			return;
		Component transport = upPort.getInPeers()[0].host;
		localPort = ((Connection)transport).getLocalPort();
		if(localPort ==0)
		{
			server = false;
			remote_port = ((Connection)transport).getRemotePort();
			try {
				remote_ip = InetAddress.getByAddress(
						MobibedUtils.LongToByteArray(
								((Connection)transport).getPeer()));
			} catch (UnknownHostException e) {
				error("get remote ip.", e);
			}
		}
		else
			server = true;
	}


	protected void setInetPort() {
		if(!upPort.anyConnection())
			return;
		Component transport = upPort.getInPeers()[0].host;
		((Connection)transport).setLocalPort(localPort);
	}
	
	/**
	 * incoming packet to send
	 */
	protected void dataArriveAtUpPort(Object data_, drcl.comp.Port upPort_)
	{
		InetPacket ipkt_ = (InetPacket) data_;
//		if(data_ instanceof InetPacket)
//		{
//			ipkt_ = (InetPacket)  data_;
//			// forward data_ to the Internet
//			if(SOCKET_ON)
//				forward(ipkt_, upPort_);
//			else
//			{
//				try {
//					Thread.sleep(1000);
//					if(SOCKET_ON)
//						forward(ipkt_, upPort_);
//					else
//						error(this.name + ": datagramSocket is not ready!!!", SOCKET_ON);
//				} catch (InterruptedException e) {
//					error(this.name + ": datagramSocket is not ready!!!", e);
//				}
//				
//			}
//		}else{
//			error(data_, "dataArriveAtUpPort", upPort_, "data_ is not an InetPacket");
//			return;
//		}	
//		forward((InetPacket) data_, upPort_);
//	}
//	
//	protected void forward(InetPacket ipkt_, drcl.comp.Port upPort_){
		// forward data_ to the Internet
		try {
			
//			if(ipkt_.getSource()==drcl.net.Address.NULL_ADDR)
//				ipkt_.setSource(localAddr);
//			// get destination port and set sourc port
//
//			Object pkt_ = ipkt_.getBody();
//			
//			
//			int remotePort_ = 0;
//			if(pkt_ instanceof UDPPacket){
//				remotePort_ = ((UDPPacket) pkt_).getDPort();
//				((UDPPacket) pkt_).setSPort(datagramSocket.getLocalPort());
//			}else if(pkt_ instanceof TCPPacket){
//				remotePort_ = ((TCPPacket) pkt_).getDPort();
//				((TCPPacket) pkt_).setSPort(datagramSocket.getLocalPort());
//				if(((TCPPacket) pkt_).isSYN())
//				{	// get destination ip address
//					remoteIPAddress = InetAddress.getByAddress
//											(MobibedUtils.LongToByteArray(ipkt_.getDestination()));
////					System.out.println("remote ip:" + remoteIPAddress.getHostAddress());
//				}
//			}else{
//				error(ipkt_, "dataArriveAtUpPort", upPort_, "pkt_ type error");
//				return;
//			}
//			
//			if(remotePort_ == 0){
//				error(ipkt_, "dataArriveAtUpPort", upPort_, 
//						"pkt_ port error: remotePort_ == 0");
//				return;
//			}			
//		
//			
////			long delay = System.nanoTime() - t1;
////			System.out.println("delay1--------------------"+delay);
			int len_ = ipkt_.encode(0);
			
//			if(len_ > 1400)
//				len_ = 1400;
			
			
			DatagramPacket sendPacket = 
	                new DatagramPacket(ipkt_.getBytes(), len_, remote_ip, remote_port);//, InetAddress.getByName("localhost"), ipkt_.getPort());
//			System.out.println("send " + System.nanoTime());
			datagramSocket.send(sendPacket);
			
			
//				System.out.println(len_);
//			if(isDebugEnabled())
//				debug("send "+ipkt_);
			// export sending packet to pcap port
//			if(pcapPort._isEventExportEnabled())
//				pcapPort.exportEvent(PCAP_EVENT, ipkt_.clone(), remoteIPAddress.getHostAddress()+":"+remotePort_+".pcap");
			
//			delay = System.nanoTime() - t1;
//			System.out.println("delay2--------------------"+delay);
			
		} catch (UnknownHostException e) {
			error(ipkt_, "dataArriveAtUpPort", upPort_, "ipkt_ destination error");
			e.printStackTrace();
		} catch (SocketException e) {
			error(ipkt_, "dataArriveAtUpPort", upPort_, "clientSocket error" + e);
			e.printStackTrace();
		} catch (IOException e) {
			error(ipkt_, "dataArriveAtUpPort", upPort_, "clientSocket send error" + e);
			e.printStackTrace();
		} finally{
			ipkt_.free();
		}
	}

	protected void openSocket(){
		try {
			if(!server){
				datagramSocket = new DatagramSocket();
				localPort = datagramSocket.getLocalPort();
				datagramSocket.connect(remote_ip, remote_port);
				
			}else
				datagramSocket = new DatagramSocket(localPort);
			SOCKET_ON = true;
		} catch (SocketException e) {
			e.printStackTrace();
		}
		debug("open socket " + localPort);
	}
	
	/**
	 * start listening on the port
	 */
	protected void receiving(){
		if(isDebugEnabled())
			debug("MobibedSocket is listening on Port " + localPort);
		try{
			InetPacket pkt;	
			while(SOCKET_ON)
			{
				pkt = InetPacket.poll();	
				byte[] bytes = pkt.getBytes();
				
				
				DatagramPacket receivePacket = 
						new DatagramPacket(bytes, bytes.length);
				datagramSocket.receive(receivePacket);
				if(remote_ip == null || remote_port == 0)
				{
					remote_ip = receivePacket.getAddress();
					remote_port = receivePacket.getPort();
				}
				
//				System.out.println("recv "+System.nanoTime());
				pkt.setSource(remote_ip);
				pkt.setPort(remote_port);
				pkt.setPacketSize(receivePacket.getLength());
				packetArriveAtDownPort(pkt);
			}
			if(isDebugEnabled())
				debug("MobibedSocket is closed on Port " + localPort);
			
		}catch(IOException ex){
			error(ex.getMessage(), this);
			if(isDebugEnabled())
				debug("UDP Port "+ localPort +" is occupied.");
		}finally{
			datagramSocket.close();
			datagramSocket = null;
		}
	}
	
	/**
	 * decode received datagram packet into {@link #InetPacket} 
	 */
	protected void packetArriveAtDownPort(InetPacket pkt) {
		if(pkt.decode())
		{
			if(isDebugEnabled())
				debug("recv "+ pkt);
			upPort.doSyncSending(pkt.getBody());
			
		}
		else
			error(this.name + ": " + "packetArriveAtDownPort InetPacket decode error.", pkt);
	}
	
	/**
	 * decode received datagram packet into {@link #InetPacket} 
	 */
	@Override
	protected void dataArriveAtDownPort(Object data_, Port downPort_) {
//		if(data_ instanceof InetPacket){
//			System.out.println("processing datagramPacket");
			InetPacket pkt = (InetPacket)data_;
			if(pkt.decode())
			{
				if(isDebugEnabled())
					debug("recv "+ pkt);
				upPort.doSyncSending(pkt.getBody());
				
			}
			else
				error(this.name + ": " + "dataArriveAtDownPort InetPacket decode error.", pkt);
//			System.out.println("array size " + array.length);
			
//			System.out.println("buffer size " + buffer.size());
//		}
	}
	
	

	@Override
	public synchronized void reset() {
		closeSocket();
		super.reset();
	}

	private void closeSocket() {
		// stop listening 
		System.out.println("closing socket");
		
		SOCKET_ON = false;
		if(isDebugEnabled())
			debug(this.getRuntime().getTime()+": stop listening");
	}
	
	
}
