package drcl.test;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;

import drcl.util.CircularBuffer;

public class TestBuffers extends getTimeCost{
	
	private int capacity = 1024*1024*10;
	private byte[] data = new byte[1024];
	private int addNum = 10000;

	@Override
	public void test(){
		long start = System.nanoTime();
		testJavaNioBuf();
		long end = System.nanoTime();
		System.out.println("java nio buf " + (end - start));
		start = System.nanoTime();
		testJsimCircularBuf();
		end = System.nanoTime();
		System.out.println("jsim cir buf " + (end - start));
		start = System.nanoTime();
		testJsimCircularBuf2();
		end = System.nanoTime();
		System.out.println("jsim cir buf2 " + (end - start));
	}

	private void testJsimCircularBuf() {
		CircularBuffer buf = new CircularBuffer(1024);
		for(int i=0;i<addNum;i++)
		{
//			synchronized(buf)
//			{
				buf.append(data);
//			}
		}
		System.out.println(buf.getAvailableSpace());
	}
	
	private void testJsimCircularBuf2() {
		CircularBuffer buf = new CircularBuffer(capacity);
		for(int i=0;i<addNum;i++)
		{
			buf.append(data);
		}
//		System.out.println(buf.getAvailableSpace());
	}

	private void testJavaNioBuf() {
//		BlockingQueue<Byte> buf = new ArrayBlockingQueue<Byte>(capacity);
//		for(int i=0;i<addNum;i++)
//			for(byte b : data)
//				buf.add(b);
//		System.out.println(buf.size());
		ConcurrentLinkedQueue<Byte> queue = new ConcurrentLinkedQueue<Byte>();
		for(int i=0;i<addNum;i++)
		{
			Collection collection = Arrays.asList(data);
			queue.addAll(collection);
		}
		System.out.println(queue.size());
	}
}
