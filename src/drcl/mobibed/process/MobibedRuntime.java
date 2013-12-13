package drcl.mobibed.process;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import drcl.comp.ACARuntime;
import drcl.comp.ACATimer;
import drcl.comp.Task;
import drcl.comp.WorkerThread;

public class MobibedRuntime extends ACARuntime{
	private static final int ScheduledPoolSize = 10;
	private final ExecutorService pool = Executors.newCachedThreadPool();
	private final ScheduledExecutorService delayedPool = Executors.newScheduledThreadPool(ScheduledPoolSize);
	private ScheduledFuture<?> lastScheduledFuture;
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	public MobibedRuntime(){
		this("default");
	}

	public MobibedRuntime(String name_){
		super(name_);
	}

	/**
	 * the only method to run runnable task in mobibed runtime
	 */
	@Override
	protected void newTask(Task task_) {
		long later_ = task_.getTime();
		if(later_ == 0)
		{
			// execute task immediately
			pool.execute(task_);
		}else{
			// execute task with delay
			lastScheduledFuture = delayedPool.schedule(task_, later_, TimeUnit.MILLISECONDS);
		}
	}
	
	/**
	 * the same as {@link #newTask(Task)}
	 * @see #newTask(Task)
	 */
	@Override
	protected void newTask(Task task_, WorkerThread current_) {
		newTask(task_);
	}
	

	@Override
	public String info() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String a_info(boolean listWaitingTasks_) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public long getNumberOfArrivalEvents() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public double getEventRate() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	protected long _getTime() {
		return System.currentTimeMillis();
	}

	@Override
	protected void _stop(boolean block_) {
		pool.shutdown();
		delayedPool.shutdown();
	}

	@Override
	public void resume() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void reset() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public long getWallTimeElapsed() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	protected String t_info(String prefix_) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	protected void off(ACATimer handle_) {
		if(lastScheduledFuture !=null) lastScheduledFuture.cancel(false);
	}

	@Override
	public Object getEventQueue() {
		// TODO Auto-generated method stub
		return null;
	}



}
