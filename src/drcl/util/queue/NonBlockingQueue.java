package drcl.util.queue;

import java.util.concurrent.ConcurrentLinkedQueue;

import drcl.inet.InetPacket;

public class NonBlockingQueue {
	private ConcurrentLinkedQueue<InetPacket> queue;

	public NonBlockingQueue(int num) {
		super();
		queue = new ConcurrentLinkedQueue<InetPacket>();
		for(int i=0;i<num;i++){
			queue.add(new InetPacket());
		}
	}
	
	public void add(InetPacket pkt){
		queue.add(pkt);
	}
	
	public int size()
	{
		return queue.size();
	}
	
	public InetPacket poll(){
		if(queue.isEmpty()){
			queue.add(new InetPacket());
//			System.out.println("queue is empty");
		}
		return queue.poll();
	}
}
