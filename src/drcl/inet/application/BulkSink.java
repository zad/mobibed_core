// @(#)BulkSink.java   9/2002
// Copyright (c) 1998-2002, Distributed Real-time Computing Lab (DRCL) 
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

package drcl.inet.application;

import java.util.concurrent.atomic.AtomicLong;

import drcl.comp.*;
import drcl.comp.lib.bytestream.ByteStreamConstants;
import drcl.comp.lib.bytestream.ByteStreamContract;

/** A byte stream sink which always has sufficient buffers for incoming data,
and it always reports available buffers of <code>dataUnit</code> bytes.
This component does not send bytes.
@see ByteStreamContract
*/
public class BulkSink extends Component
	implements ActiveComponent, ByteStreamConstants
{
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	Port downPort = addPort("down", false);
	int dataUnit = 1388;
	AtomicLong progress;
	long cnt=1, start;

	public BulkSink ()
	{ super(); }

	public BulkSink (String id_)
	{ super(id_); }

	public void reset()
	{
		super.reset();
		progress = new AtomicLong();
	}

	protected void _start()
	{
		try {
			Thread.sleep(1000);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		progress = new AtomicLong();
//		downPort.doLastSending(new ByteStreamContract.Message(QUERY));
		downPort.doSending(new ByteStreamContract.Message(QUERY));
		start = (long) getTime();
		if(isDebugEnabled())debug("start at " + start);
	}
	
	public String info()
	{
		long value = progress.get();
	    return "Progress: " + (value/dataUnit) + "/" + value + "\n";
	}
	
	public void duplicate(Object source_)
	{
		super.duplicate(source_);
		dataUnit = ((BulkSink)source_).dataUnit;
	}

	protected void process(Object data_, Port inPort_)
	{
		if(!(data_ instanceof ByteStreamContract.Message))
			return;
		ByteStreamContract.Message msg_ = (ByteStreamContract.Message)data_;
		switch(msg_.getType()){
		case ByteStreamContract.QUERY:
			inPort_.doSending(new Integer(dataUnit));
			break;
		case ByteStreamContract.RECV:
			if(isDebugEnabled() && progress.get() == 0)
				debug("receiving data " + msg_.getLength());
			progress.addAndGet(msg_.getLength());
//			inPort_.doSending(msg_.getLength());
//			if(isDebugEnabled() && cnt--==0){
//				debug("received (bytes): "+ msg_.getLength());
//				cnt = 1;
//			}
			break;
		case ByteStreamContract.ERROR:
			error("process", msg_.getKey());
		case ByteStreamContract.STOP:
			if(isDebugEnabled())
				debug("connection closed. received (bytes): "+ progress+
						" time:" + (long)(getTime()-start) + " " + (long)getTime());
			
		}
	}

	public void setDataUnit(int dataUnit_)
	{ dataUnit = dataUnit_; }

	public int getDataUnit()
	{ return dataUnit; }
}
