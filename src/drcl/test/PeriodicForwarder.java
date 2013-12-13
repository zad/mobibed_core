package drcl.test;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;

import drcl.comp.ActiveComponent;

public class PeriodicForwarder extends drcl.net.Module 
implements ActiveComponent{

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private int localPort = 0;
	private DatagramSocket datagramSocket;
	private byte[] buf = new byte[1024]; 
	

	@Override
	protected void _start() {
		// connect to server
		openSocket();
		this.setTimeout(0, 2000);
	}
	
	protected void openSocket(){
		try {
			if(localPort==0){
				datagramSocket = new DatagramSocket();
				localPort = datagramSocket.getLocalPort();
			}else
				datagramSocket = new DatagramSocket(localPort);
		} catch (SocketException e) {
			e.printStackTrace();
		}
	}

	@Override
	protected void timeout(Object data_) {
		DatagramPacket p;
		try {
			p = new DatagramPacket(buf, buf.length, InetAddress.getByName("localhost"), 5003);
			datagramSocket.send(p );
		} catch (UnknownHostException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		// forward a packet
		 catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		System.out.println("sent packet " + data_);
		setTimeout((Integer)data_ + 1, 200);
	}
	
	
}
