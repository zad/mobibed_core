package drcl.inet.host;


import java.util.concurrent.atomic.AtomicLong;

import drcl.util.TimeLog;


public class BufferArray {
	private byte[][] array;
	private boolean[] occupied;
	private int num;
	private AtomicLong head, tail;
//	private long idx;
	
	public BufferArray(int num, int size){
		array = new byte[num][size];
		occupied = new boolean[num];
		this.num = num;
		head = new AtomicLong();
		tail = new AtomicLong(-1);
	}
	
	public int alloc(){
		int t = (int) tail.get()%num;
		if((int) head.get()%num == t)
			while(t == (tail.get()%num));
		int i = (int) (head.getAndIncrement() % num);
//		long t = System.nanoTime();
//		int i = (int) (idx.getAndIncrement() % num);
//		int i = (int) (idx++ % num);
//		long t2 = System.nanoTime();
//		TimeLog.add("0.3. " + (t2-t));
		return i;
	}
	
//	public int selectBuffer(){
////		long t = System.nanoTime();
//		synchronized(occupied){
//			for(int i=0;i<num;i++)
//			{
//				if(this.occupied[i] == false)
//				{
//					this.occupied[i] = true;
////					TimeLog.add("0.1. " + (System.nanoTime()-t));
//					return i;
//				}
//			}
////			TimeLog.add("0.2. " + (System.nanoTime()-t));
//			return -1;
//		}
////		for(int i=0;i<num;i++)
////		{
////			if(this.occupied[i] == false)
////			{
////				synchronized(occupied){
////					if(this.occupied[i] == false)
////					{
////						this.occupied[i] = true;
//////						TimeLog.add("0.1. " + (System.nanoTime()-t));
////						return i;
////					}
////				}
////				
////			}
////		}
////		TimeLog.add("0.2. " + (System.nanoTime()-t));
////		return -1;
//	}
	
	public byte[] getBuffer(int i)
	{
		return array[i];
	}
	
	public static void main(String [ ] args){
		BufferArray buf = new BufferArray(50, 3000);
		long t = System.nanoTime();
		for (int i=0;i<10000;i++)
		{
			int j = buf.alloc();
			buf.releaseBuffer(j);
		}
		long t2 = System.nanoTime();
		System.out.println(t2-t);
	}
	
	public void releaseBuffer(int i)
	{
		tail.incrementAndGet();
	}
}
