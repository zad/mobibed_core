package drcl.test;

import drcl.comp.ActiveComponent;

import drcl.net.Module;

public class ComponentExample extends Module implements ActiveComponent{
	
	long duration = 10;
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	@Override
	protected void _start() {
		System.out.println("ComponentExample started " + getTime());
		int timer_cnt = 0;
		this.setTimeout(timer_cnt, duration);
	}

	@Override
	protected void timeout(Object data_) {
		int cnt = (Integer)data_;
		System.out.println("timeout at " + getTime() + " count " + cnt);
		cnt++;
		this.setTimeout(cnt, duration);
	}
	
	
	
	

}
