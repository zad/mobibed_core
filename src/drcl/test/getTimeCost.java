package drcl.test;

import drcl.comp.ActiveComponent;

import drcl.net.Module;

public class getTimeCost extends Module implements ActiveComponent{
	
	long duration = 2000;
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	protected int num = 1000;
	
	@Override
	protected void _start() {
		System.out.println("getTimeCost started " + getTime());
		this.setTimeout(num, duration);
	}
	
	@Override
	protected void timeout(Object data_) {
		test();
//		this.setTimeout(data_, duration);
	}

	protected void test(){
		long start = System.nanoTime();
		long t;
		for(int i=0;i<num;i++)
			t = System.currentTimeMillis();
		long end = System.nanoTime();
		System.out.println("ms cost " + (end - start)/num);
		
		start = System.nanoTime();
		for(int i=0;i<num;i++)
			t = System.nanoTime();
		end = System.nanoTime();
		System.out.println("ns cost " + (end - start)/num);
		
		double dt;
		
		for(int i=0;i<num;i++)
			dt = System.currentTimeMillis()/1000.0;
		end = System.nanoTime();
		System.out.println("ms to s cost " + (end - start)/num);
		
		start = System.nanoTime();
		for(int i=0;i<num;i++)
			dt = System.nanoTime()/1000000000.0;
		end = System.nanoTime();
		System.out.println("ns to s cost " + (end - start)/num);
	}
	
	
	
	

}
