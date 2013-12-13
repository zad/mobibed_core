package drcl.inet.transport;

public abstract class TCPCong {
	public String name;
	public static final int TCP_INIT_CWND=3;
	
	public abstract void init(TCP tcp);
	public abstract int ssthresh(TCP tp);
	public abstract void cong_avoid(TCP tp, long ackseq_, int in_flight);
	public abstract void set_state(TCP tp);
	public abstract int undo_cwnd(TCP tp);
	public abstract void pkts_acked(TCP tp, long snd_una, double rtt_us);
	public abstract void register();
	
	public boolean tcp_is_cwnd_limited(TCP tp, int in_flight){
		int left;
		int snd_cwnd = tp.CWND/tp.MSS;
		if(in_flight >= snd_cwnd)
			return true;
		left = snd_cwnd - in_flight;
		
		return left <= 3;
	}
	
	/* In theory this is tp->snd_cwnd += 1 / tp->snd_cwnd (or alternative w) */
	public void tcp_cong_avoid_ai(TCP tp, int w){
		if(tp.snd_cwnd_cnt >= w) {
			if(tp.CWND<tp.MAXCWND)
				tp.CWND+=tp.MSS;
			tp.snd_cwnd_cnt = 0;
		}else
			tp.snd_cwnd_cnt++;
//		System.out.println("cong avoid:"+TCP.PHASES[tp.phase]+" CWND:"+ tp.CWND/tp.MSS 
//				+" sthld:" + tp.sthld/tp.MSS 
//				+ " cnt:"+tp.snd_cwnd_cnt+":"+((TCPCubic)this).cnt);
	}
	
	public void tcp_slow_start(TCP tp){
		int cnt;	// increase in packets
		int delta = 0;
		// assume sysctl_tcp_abc = 0
		// assume sysctl_tcp_max_ssthresh = 0
		cnt = tp.CWND/tp.MSS;
		
		// do not need <bytes_acked>
		tp.snd_cwnd_cnt += cnt;
		int snd_cwnd = tp.CWND/tp.MSS;
		while(tp.snd_cwnd_cnt >= snd_cwnd){
			tp.snd_cwnd_cnt -= snd_cwnd;
			delta++;
		}
		tp.CWND = min(snd_cwnd+delta, tp.MAXCWND/tp.MSS)*tp.MSS;
		tp.CWND = max(tp.CWND, TCP_INIT_CWND*tp.MSS);
//		System.out.println("slow start:"+TCP.PHASES[tp.phase]+" CWND:"+ tp.CWND/tp.MSS 
//				+" sthld:" + tp.sthld/tp.MSS 
//				+ " cnt:"+tp.snd_cwnd_cnt+":"+((TCPCubic)this).cnt);
	}
	
	public int min(int a,int b){
		return a>b?b:a;
	}
	
	public int max(int a, int b){
		return a>b?a:b;
	}
}
