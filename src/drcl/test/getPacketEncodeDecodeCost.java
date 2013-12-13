package drcl.test;

import java.util.concurrent.ConcurrentLinkedQueue;

import drcl.inet.InetPacket;
import drcl.util.queue.NonBlockingQueue;

public class getPacketEncodeDecodeCost extends getTimeCost{
	private byte[] raw = new byte[2048];
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	protected void _start(){
		System.out.println();
		num = 10000;
		testEncode();
		testEncode2();
		testEncode3();
		testDecode();
		testDecode2();
//		testDecode3();
	}
	
	private InetPacket pkt;
	
	private void testEncode(){
		long start = System.nanoTime();
		long ip = 0;
		int port = 0;
		for(int i=0;i<num;i++)
		{
			pkt = new InetPacket();
			pkt.toPacket(raw, ip, port);
		}
		long end = System.nanoTime();
		System.out.println("pkt encoding time cost " + (end - start)/num);
	}
	
	private void testEncode2(){
		
		long ip = 0;
		int port = 0;
		pkt = new InetPacket();
		long start = System.nanoTime();
		for(int i=0;i<num;i++)
		{
			
			pkt.toPacket(raw, ip, port);
		}
		long end = System.nanoTime();
		System.out.println("pkt encoding time cost " + (end - start)/num);
	}
	
	private void testEncode3(){
		ConcurrentLinkedQueue<InetPacket> buf = new ConcurrentLinkedQueue<InetPacket>();
		
		long ip = 0;
		int port = 0;
		pkt = new InetPacket();
		buf.add(pkt);
		long start = System.nanoTime();
		for(int i=0;i<num;i++)
		{
			pkt = buf.poll();
			pkt.toPacket(raw, ip, port);
			buf.add(pkt);
		}
		long end = System.nanoTime();
		System.out.println("pkt encoding time cost " + (end - start)/num);
	}

	private void testDecode(){
		pkt.setProtocol(6);
		
		byte[] bytes = new byte[2048];
		long start = System.nanoTime();
		for(int i=0;i<num;i++)
		{
			
//			pkt.toBytes(bytes, 0);
			
		}
		long end = System.nanoTime();
		System.out.println("pkt decoding time cost " + (end - start)/num);
	}
	
	private void testDecode2(){
		pkt.setProtocol(6);
		long start = System.nanoTime();
		
		for(int i=0;i<num;i++)
		{
			byte[] bytes = new byte[2048];
//			pkt.toBytes(bytes, 0);
			
		}
		long end = System.nanoTime();
		System.out.println("pkt decoding time cost " + (end - start)/num);
	}
	
//	private void testDecode3(){
//		pkt.setProtocol(6);
//		NonBlockingQueue buffer = new NonBlockingQueue(2048, 1);
//		long start = System.nanoTime();
//		
//		for(int i=0;i<num;i++)
//		{
//			byte[] bytes = buffer.poll();
//			pkt.toBytes(bytes, 0);
//			buffer.add(bytes);
//			
//		}
//		long end = System.nanoTime();
//		System.out.println("pkt decoding time cost " + (end - start)/num);
//	}
}
