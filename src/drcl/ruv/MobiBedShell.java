package drcl.ruv;

import java.io.BufferedReader;

import tcl.lang.Interp;





public class MobiBedShell extends Shell {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	public static String name = "mobibed";
	Interp it;
	
	public String getName() { return name; }
	
	public MobiBedShell() throws Exception {
		super();
		
	}
	
	public MobiBedShell(String id) throws Exception {
		super(id);
		
	}

	@Override
	protected void init() throws ShellEvalException {
		
		it = new Interp((Shell)this);
	}
	
	public final Object evalFile(String script_, String[] args_)
			throws ShellEvalException, InterruptedException
	{
		Object o = super.evalFile(script_, args_);
		return o;
	}
	
	public synchronized Object eval(String cmd_)
			throws ShellEvalException, InterruptedException
	{
		try {
			it.eval(cmd_);
			
		}
		catch (Exception e_) {
			if (e_ instanceof InterruptedException)
				throw (InterruptedException)e_;
			else
//				throw new ShellEvalException(null, e_,
//								it.getResult().toString());
				throw new ShellEvalException("shell eval exception");
		}
		// XX: not precise
		return null;
//		return cmd_.endsWith(";")? null: it.getResult();
	}

	/**
	 * evaluate input script string here
	 */
	public Object eval(BufferedReader r_)
			throws ShellEvalException, InterruptedException
	{
		StringBuffer sb_ = new StringBuffer();
		Object result_ = null;
		String line_ = null;
		int counter_ = 0;
		try {
			while (true) {
				// interpret line by line
				line_ = r_.readLine();
				counter_++;
				if (line_ == null) break;
				line_.trim();
				if (line_.length() == 0 || line_.startsWith("#")) continue;
			
			
				if (line_.lastIndexOf('\\') == line_.length() -1) {
					// unfinished line
					sb_.append(line_.substring(0, line_.length() - 1));
					sb_.append(" ");
					continue;
				} else {
					sb_.append(line_);
					// NOTE: must add a trailing line,
					// otherwise the interpreter thinks of a complete command
					// with comment at the end as incomplete...
					synchronized (this) { 
						if (Interp.commandComplete(sb_ + "\n")) {
							String cmd_ = sb_.toString();
							it.eval(cmd_);
							// XX: not precise
							result_ = cmd_.endsWith(";")? 
									null: it.getResult();
							//result_ = eval(sb_.toString());
							sb_.setLength(0);
						}
						else {
							//java.lang.System.out.println(
							//	"command not complete: " + sb_);
							sb_.append("\n");
						}
					}
				}
			}
		}
		catch (Exception e_) {
			if (e_ instanceof InterruptedException)
				throw (InterruptedException)e_;
			else
				throw new ShellEvalException(sb_.toString(), e_, 
								it.getResult().toString(), counter_);
		}
		return result_;

	}

	@Override
	protected boolean isCommandComplete(String cmd_) {
		
		return Interp.commandComplete(cmd_);
	}

	@Override
	protected void setArguments(String[] args_) throws ShellEvalException {
		
		
	}

	@Override
	protected String _autocomplete(String cmd_, int pos_)
			throws ShellEvalException {
		
		return null;
	}
	
	public void print(String results){
		java.lang.System.out.print(results);
	}

}
