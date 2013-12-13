// @(#)FileComponent.java   2/2004
// Copyright (c) 1998-2004, Distributed Real-time Computing Lab (DRCL) 
// All rights reserved.
// 
// Redistribution and use in source and binary forms, with or without
// modification, are permitted provided that the following conditions are met:
// 
// 1. Redistributions of source code must retain the above copyright notice,
//    this list of conditions and the following disclaimer. 
// 2. Redistributions in binary form must reproduce the above copyright notice,
//    this list of conditions and the following disclaimer in the documentation
//    and/or other materials provided with the distribution. 
// 3. Neither the name of "DRCL" nor the names of its contributors may be used
//    to endorse or promote products derived from this software without specific
//    prior written permission. 
// 
// THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
// AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
// IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
// ARE DISCLAIMED. IN NO EVENT SHALL THE REGENTS OR CONTRIBUTORS BE LIABLE FOR
// ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
// DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
// SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
// CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
// OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
// OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
// 

package drcl.comp.io;

import java.io.*;

import drcl.comp.*;
import drcl.data.*;
import drcl.comp.lib.bytestream.*;
import drcl.comp.contract.DoubleEventContract;
import drcl.comp.contract.EventContract;
import drcl.util.StringUtil;

/**
 * Writes incoming data to a file in text format. 
 */
public class FileWriter2 extends Extension
{
	static final long FLAG_APPEND_NEW_LINE_TO_OBJECT_ENABLED  = 1L << FLAG_UNDEFINED_START;
	static final long FLAG_AUTO_FLUSH_ENABLED  = 1L << FLAG_UNDEFINED_START << 1;
	static final long FLAG_TIMESTAMP_ENABLED  = 1L << FLAG_UNDEFINED_START << 2;
	static final long FLAG_EVENT_FILTERING_ENABLED  = 1L << FLAG_UNDEFINED_START << 3;
	static final long FLAG_WRITE_BINARY_ENABLED  = 1L << FLAG_UNDEFINED_START << 4;
	{ setComponentFlag(FLAG_APPEND_NEW_LINE_TO_OBJECT_ENABLED | FLAG_AUTO_FLUSH_ENABLED, true); }
	
	public FileWriter2()
	{ super(); }
	
	public FileWriter2(String id_)
	{ super(id_); }
	
	PrintWriter out = null;
//	String file = "cwnd";
	
	
	public void open(String fname_)
	{
		try {
			if (out != null) try { out.close(); } catch (Exception e_) {}
			out = new PrintWriter(fname_);
		}
		catch (Exception e_) {
			error("open()", e_);
		}
	}

	public void close()
	{
		try {
			if (out != null) out.close();
		}
		catch (Exception e_) {
			error("close()", e_);
		}
	}
	
	protected void finishing(){
		super.finishing();
		this.close();
	}

	private int figID=0, setID=0;
	private String eventName, portPath;
	
	protected synchronized void process(Object data_, Port inPort_) 
	{
		try {
			
			if (data_ instanceof EventContract.Message) {
				
				int figID_=0, setID_=0;
				boolean isChanged = false;
				figID_ = Integer.parseInt(inPort_.groupID);
				setID_ = Integer.parseInt(inPort_.id);
				
				EventContract.Message s_ = (EventContract.Message)data_;
				Object evt_ = s_.getEvent();
				if (out == null) {
					open(s_.getEventName()+".txt");
				}
				double x_ = 0.0, y_ = 0.0;
				if (evt_ instanceof Double) {
					x_ = s_.getTime();
					y_ = ((Double)evt_).doubleValue();
				}
				else if (evt_ instanceof DoubleObj) {
					x_ = s_.getTime();
					y_ = ((DoubleObj)evt_).value;
				}
				else if (evt_ instanceof double[]) {
					double[] xy_ = (double[])evt_;
					if (xy_.length >= 2) {
						x_ = xy_[0];  y_ = xy_[1];
					}
					else if (xy_.length == 1) {
						x_ = s_.getTime();  y_ = xy_[0];
					}
					else {
						error(data_, "process()", inPort_, "zero-length double array");
						return;
					}
				}
				else if (evt_ instanceof String)
				{
					String cmd = (String)evt_;
					if(cmd.compareTo("CLOSE")==0){
						close();
						if(isDebugEnabled())
							debug("close writer.");
					}
				}
				else {
					error(data_, "process()", inPort_, "unrecognized event object: " + evt_);
					return;
				}
				if(figID_ != figID || setID_ != setID 
						|| eventName == null || portPath == null)
				{
					isChanged = true;
					figID = figID_;
					setID = setID_;
					eventName = s_.getEventName();
					portPath = s_.getPortPath();
				}
				write(figID, setID, x_, y_, eventName, portPath,isChanged);
			}
		}
		catch (Exception e_) {
			e_.printStackTrace();
			error("close()", e_);
		}
	}

//	public class Cwnd implements java.io.Serializable{
//		/**
//		 * 
//		 */
//		private static final long serialVersionUID = 1L;
//		public int figID, setID;
//		public double x,y;
//		public String eventName, portPath;
//		public Cwnd(int figID_, int setID_, double x_, double y_,
//				String eventName, String portPath){
//			figID = figID_;
//			setID = setID_;
//			x = x_;
//			y = y_;
//			this.eventName = eventName;
//			this.portPath = portPath;
//		}
//		
//		public String toString(){
//			return figID + " " + setID + " " + x + " " + y
//					+ " " + eventName + " " + portPath;
//		}
//	}
	
	private void write(int figID_, int setID_, double x_, double y_,
			String eventName, String portPath, boolean isChanged) {
		// TODO Auto-generated method stub
//		Cwnd cwnd = new Cwnd( figID_,  setID_,  x_,  y_,
//				 eventName,  portPath);
//		if(isChanged)
//		{	
//			out.println( figID + " " + setID + " " + eventName + " " + portPath);
//		}
		out.println(x_ + " " + y_);
		
	}

	public void setAppendNewLineToObjectEnabled(boolean enabled_)
	{ setComponentFlag(FLAG_APPEND_NEW_LINE_TO_OBJECT_ENABLED, enabled_); }
	
	public boolean isAppendNewLineToObjectEnabled()
	{ return getComponentFlag(FLAG_APPEND_NEW_LINE_TO_OBJECT_ENABLED) != 0; }
	
	public void setAutoFlushEnabled(boolean enabled_)
	{ setComponentFlag(FLAG_AUTO_FLUSH_ENABLED, enabled_); }
	
	public boolean isAutoFlushEnabled()
	{ return getComponentFlag(FLAG_AUTO_FLUSH_ENABLED) != 0; }
	
	public void setTimestampEnabled(boolean enabled_)
	{ setComponentFlag(FLAG_TIMESTAMP_ENABLED, enabled_); }
	
	public boolean isTimestampEnabled()
	{ return getComponentFlag(FLAG_TIMESTAMP_ENABLED) != 0; }
	
	public void setEventFilteringEnabled(boolean enabled_)
	{ setComponentFlag(FLAG_EVENT_FILTERING_ENABLED, enabled_); }
	
	public boolean isEventFilteringEnabled()
	{ return getComponentFlag(FLAG_EVENT_FILTERING_ENABLED) != 0; }
}
