package drcl.inet.transport;

public class TCPCubic extends TCPCong{
	// constants
	// two methods of hybrid slow start
	public static final int HYSTART_ACK_TRAIN = 0x1;
	public static final int HYSTART_DELAY = 0x2;
	
	// number of delay samples for detecting the increase of delay
	public static final int HYSTART_MIN_SAMPLES = 8;
	private static final int HYSTART_DELAY_MAX = 16<<3;
	private static final int HYSTART_DELAY_MIN = 4<<3;
	public static int HYSTART_DELAY_THRESH(int x){
		return clamp(x, HYSTART_DELAY_MIN, HYSTART_DELAY_MAX);
	}
	private static int clamp(int x, int min, int max) {
		x = x<min?min:x;
		x = x>max?max:x;
		return x;
	}
	
	public static short hystart = 1;
	public static final int hystart_low_window = 16;
	public static final int hystart_detect = HYSTART_ACK_TRAIN|HYSTART_DELAY;
	public static final int hystart_ack_delta = 2;
	private static final int BICTCP_HZ = 10;
	public static int initial_ssthresh;
	public static double cube_factor;
	private int bic_scale = 41;
	
	public final int ACK_RATIO_SHIFT = 4;
	public final int BICTCP_BETA_SCALE = 1024;
	public final int beta = 717;
	public final int ACK_RATIO_LIMIT = 32*16; 
//			32<<ACK_RATIO_SHIFT;
	// variables
	public int tcp_friendliness = 1;
	public boolean fast_convergence = true;
	public double C = 0.4;

	public int cnt;  /* increase cwnd by 1 after ACKs */
	public int last_max_cwnd = 0; /* last maximum snd_cwnd */
	public int loss_cwnd = 0;      /* congestion window at last loss */
	public int last_cwnd = 0;      /* the last snd_cwnd */
	public double last_time = 0;      /* time when updated last_cwnd */
	public int bic_origin_point;/* origin point of bic function */
	public double bic_K;          /* time to origin point from 
								the beginning of the current epoch */
	public int delay_min = 0;	 /* min delay (msec << 3) */
	public double epoch_start = 0;/* beginning of an epoch */
	public int ack_cnt = 0; // number of acks
	public int tcp_cwnd = 0; // estimated tcp cwnd
	
	public int delayed_ack;    /* estimate the ratio of Packets/ACKs << 4 */
	public short sample_cnt;     /* number of samples to decide curr_rtt */
	public short found; // the exit point is found ?
	public long round_start;    /* beginning of each round */
	public long end_seq;        /* end_seq of the round */
	public long last_ack;       /* last time when the ACK spacing is close */
	public int curr_rtt;       /* the minimum rtt of current round */
	private long cube_rtt_scale;
	private int beta_scale;
	
	private boolean DEBUG = false;
	
	@Override
	public void init(TCP tcp) {
		reset();
		this.loss_cwnd = 0;
		
		if(hystart!=0)
			hystart_reset(tcp);
		if(hystart==0 && initial_ssthresh>0)
			tcp.INIT_SS_THRESHOLD = initial_ssthresh;
	}

	private void hystart_reset(TCP tcp) {
		round_start = last_ack = tcp.getTime();
		end_seq = tcp.snd_nxt;
		curr_rtt = 0;
		sample_cnt = 0;
	}

	private void reset() {
		cnt = 0;
		last_max_cwnd = 0;
		last_cwnd  = 0;
		last_time = 0;
		bic_origin_point = 0;
		bic_K = 0;
		delay_min = 0;
		epoch_start = 0;
		delayed_ack = 2 << ACK_RATIO_SHIFT;
		ack_cnt = 0;
		tcp_cwnd = 0;
		found = 0;
	}
	
	private void debug(String msg){
		if(DEBUG)
			System.out.println("CUBIC DEBUG | " + msg);
	}

	@Override
	public int ssthresh(TCP tp) {
		this.epoch_start = 0; // end of epoch
		// Wmax and fast convergence
		int snd_cwnd = tp.last_cwnd/tp.MSS;
		if(snd_cwnd < this.last_max_cwnd && fast_convergence)
		{	
			this.last_max_cwnd = (snd_cwnd*(BICTCP_BETA_SCALE + beta))
				/ (2*BICTCP_BETA_SCALE);
//			System.out.println("================================fast_convergence");
		}
		else
			this.last_max_cwnd = snd_cwnd;
		this.loss_cwnd = snd_cwnd;
		return max((snd_cwnd*beta)/BICTCP_BETA_SCALE, 2);
	}


	
	@Override
	public void cong_avoid(TCP tp, long ack, int in_flight) {
		if(!tcp_is_cwnd_limited(tp, in_flight))
		{
			debug("tcp is not cwnd limited ... :"+in_flight);
			return;
		}
		if(tp.CWND <= tp.sthld){
			if(hystart!=0 && after(ack, this.end_seq))
				hystart_reset(tp);
			tcp_slow_start(tp);
			debug("tcp_slow_start");
		}else{	
			bictcp_update(tp);
			tcp_cong_avoid_ai(tp,this.cnt);
			debug("tcp_cong_avoid_ai");
		}
	}

	/*
	 * compute congestion window to use.
	 */
	private void bictcp_update(TCP tp) {
		
		double offs, t, bic_target;
		double delta, max_cnt;
		int cwnd = tp.CWND/tp.MSS;
		double timestamp = tp.getTime()*250.0;
		this.ack_cnt++; // count the number of ACKs.
		
		if(this.last_cwnd == cwnd &&
				(timestamp-this.last_time)<= 1000/32)
			return;
		
		this.last_cwnd = cwnd;
		this.last_time = timestamp;
		
		if(this.epoch_start <= 0.1){
			this.epoch_start = timestamp; // record the beginning of an epoch
			this.ack_cnt = 1;	// start counting.
			this.tcp_cwnd = cwnd;	// syn with cubic
			
			if(this.last_max_cwnd <= cwnd){
				this.bic_K = 0;
				this.bic_origin_point = cwnd;
//				System.out.println("================================convex");
			}else{
				/*
				 * compute new K bsed on
				 * (wmax-cwnd)*(srrt>>3/HZ)/c*2^(3*bictcp_HZ)
				 */
//				this.bic_K = (int) Math.cbrt(cube_factor
//						* (this.last_max_cwnd-cwnd));
				this.bic_K = Math.cbrt((this.last_max_cwnd-cwnd)/0.4);
				this.bic_origin_point = this.last_max_cwnd;
//				System.out.println("================================concave");
			}
		}

		t = (timestamp + this.delay_min/8 - this.epoch_start)/250;
		offs = t - bic_K;
		bic_target = this.bic_origin_point + 0.4*offs*offs*offs;
//		bic_target = (long) (this.bic_origin_point + 0.4*offs*offs*offs);
		
		// cubic function -- calc bictcp_cnt
		if(bic_target > cwnd)
			this.cnt = (int) (cwnd/(bic_target - cwnd));
		else
			cnt = 100 * cwnd;	// very small increment
		
		/*
		 * the initial growth of cubic function may be too conservative
		 * when the available bandwidth is still unknown
		 */
		if(this.last_max_cwnd == 9 && cnt > 20){
			cnt = 20;	// increase cwnd 5% per RTT
		}
		
		// TCP friendly
		if(this.tcp_friendliness==1){
//			int scale = beta_scale;
//			delta = (cwnd * scale)/16;
//			while(this.ack_cnt > delta){ // update tcp cwnd
//				this.ack_cnt -= delta;
//				this.tcp_cwnd++;
//			}
			double tmp = 3*0.2/(2-0.2)*this.ack_cnt/cwnd;
			this.ack_cnt = 0;
			this.tcp_cwnd += tmp; 
			
			if(this.tcp_cwnd > cwnd){ // if bic is slower than tcp
				delta = this.tcp_cwnd - cwnd;
				max_cnt = cwnd/delta;
				if(this.cnt>max_cnt)
					cnt = (int)max_cnt;
			}
		}
//		int tmp = cnt;
// 		cnt = (cnt * 16)/this.delayed_ack;
		
//		System.out.println(cnt +"------------------------------" + tmp);
		if(cnt ==0) // cannot be zero
			cnt = 1;
//		System.out.println("bitupdate return: "+cwnd + ":" + this.last_max_cwnd 
//				+" t:"+t + " target:"+ bic_target + " K:" + bic_K + " origin:"+this.bic_origin_point 
//				+" cnt:"+cnt + " delay min:"+delay_min);
	}
	

	
	@Override
	public void set_state(TCP tp) {
		
		
		reset();
		hystart_reset(tp);
		
	}

	@Override
	public int undo_cwnd(TCP tp) {
		int snd_cwnd = tp.CWND/tp.MSS;
		return max(snd_cwnd, this.loss_cwnd);
	}

	@Override
	public void pkts_acked(TCP tp, long cnt, double rtt_us) {
		int delay;
		if(tp.phase == TCP.SLOW_START || tp.phase == TCP.CONGESTION_AVOIDANCE){
			int ratio = this.delayed_ack;
			ratio -= this.delayed_ack / 16;
			ratio += cnt;
			this.delayed_ack = min(ratio, ACK_RATIO_LIMIT);
		}
		// some calls are for duplicates without timestamps
		if(rtt_us<0)
			return;
		
		
		
		if(tp.getTime()*250.0-this.epoch_start < 1000)
			return;
		
		delay = (int) (rtt_us / 4000 / 8);
		if(delay == 0)
			delay = 1;
		
		// first time call or link delay decreases
		if(this.delay_min == 0 || this.delay_min > delay)
			this.delay_min = delay;
		
		// hystart triggers when cwnd is larger than some threshold
		int snd_cwnd = tp.CWND/tp.MSS;
		if(hystart>0 && snd_cwnd <= tp.sthld && 
				snd_cwnd >= hystart_low_window){
			hystart_update(tp,delay);
		}
		
//		System.out.println("delay_min:"+delay_min + " delayed_ack:"+delayed_ack);
	}
	
	//	private int cubic_root(long l) {
	//		
	//		return (int) Math.cbrt(l);
	//	}
		
		private boolean after(long seq1, long seq2) {
			return seq1-seq2>0;
		}
	private void hystart_update(TCP tp, int delay) {
//		System.out.println("hystart update");
		if((this.found & hystart_detect) == 0){
			// first detection parameter -- ack-train detection
			long now = (long) (tp.getTime()*1000.0);
			if((now - this.last_ack)<= hystart_ack_delta){
				this.last_ack = now;
				if((now - this.round_start)>(this.delay_min>>4))
					this.found |= HYSTART_ACK_TRAIN;
			}
			
			// obtain the minimum delay of more than sampling packets.
			if(this.sample_cnt < HYSTART_MIN_SAMPLES){
				if(this.curr_rtt == 0 || this.curr_rtt > delay)
					this.curr_rtt = delay;
				this.sample_cnt++;
			}else{
				if(this.curr_rtt > this.delay_min + 
						HYSTART_DELAY_THRESH(this.delay_min>>4))
					this.found |= HYSTART_DELAY;
			}
			// either one of two conditions are met,
			// we exit from slow start immediately.
			if((this.found & hystart_detect) !=0)
			{	
				tp.sthld = tp.CWND;
//				System.out.println("----------------hystart update----------------");
			}
		}
		
		
	}
	@Override
	public void register() {
		
		this.beta_scale = 8*(BICTCP_BETA_SCALE+beta)/3/(BICTCP_BETA_SCALE-beta);
		cube_rtt_scale = (bic_scale * 10);
//		cube_factor = 1 << (10+3*BICTCP_HZ); // 2^40
		cube_factor = Math.pow(2, 40);
		cube_factor /= bic_scale*10;
	}

	



}
