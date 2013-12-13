// @(#)BulkSource.java   11/2005
// Copyright (c) 1998-2005, Distributed Real-time Computing Lab (DRCL) 
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


import java.util.concurrent.ConcurrentHashMap;

import drcl.comp.*;
import drcl.comp.lib.bytestream.ByteStreamConstants;
import drcl.comp.lib.bytestream.ByteStreamContract;

/** A byte stream source which always sends bytes as long as the receiver
has sufficient buffers.  This component does not receive bytes. 
@see drcl.comp.lib.bytestream.ByteStreamContract
{@link MBulkSource} supports multiple clients at the same time
*/
public class MBulkSource extends Component 
	implements ActiveComponent, ByteStreamConstants
{
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	Port downPort = addPort("down", false);
	int SIZE = 5120*1000;
	int dataUnit = 512;
	byte[] data = new byte[SIZE];
	
	ConcurrentHashMap<String, Integer> progressMap = new ConcurrentHashMap<String, Integer>();

	public MBulkSource ()
	{ super(); }

	public MBulkSource (String id_)
	{ super(id_); }
	
	public void setDataSize(int max)
	{ 
		SIZE = max;
		data = new byte[SIZE];
	}
	
	public void reset()
	{
		super.reset();
		progressMap = new ConcurrentHashMap<String, Integer>();
	}
	
	public void duplicate(Object source_)
	{
		super.duplicate(source_);
		dataUnit = ((MBulkSource)source_).dataUnit;
	}
	
	/**
	 * DataUnit is a reference value to display the sending progress
	 * in the form of (number of bytes sent) / DataUnit.
	 * @param dataUnit_ in bytes; default is 512.
	 */
	public void setDataUnit(int dataUnit_)
	{ dataUnit = dataUnit_; }

	public int getDataUnit()
	{ return dataUnit; }

	protected void _resume()
	{
		downPort.doLastSending(new ByteStreamContract.Message(QUERY));
	}
	
//	public String info()
//	{
//		return "Progress: " + (progress/dataUnit) + "/" + progress + "\n";
//	}

	protected void process(Object data_, Port inPort_)
	{
		if (isStopped()) return;
		if (!(data_ instanceof ByteStreamContract.Message))
		{	error("process", data_); return; }
		
		ByteStreamContract.Message msg_ = (ByteStreamContract.Message) data_;

		String key_ = msg_.getKey();
		int space = msg_.getLength();
		int length;
		if(space==0)
			return;
		int progress = 0;
		switch(msg_.getType()){
		case ByteStreamContract.START:
			// TODO make it thread safe
			if(progressMap.contains(key_))
				progress = progressMap.get(key_);
			else
				progress = 0;
			length = SIZE - progress;
			if(length > space) length = space;
			downPort.doSyncSending(new ByteStreamContract.Message(SEND, key_, data, progress, length));
			if(isDebugEnabled())
			{
				debug("SIZE = " + SIZE);
				debug("send bulk data to " + key_ + " start at " + progress +" len: "+ length);
			}
			progressMap.put(key_, progress + length);
			break;
		case ByteStreamContract.REPORT:
			if(progressMap.containsKey(key_)){
				if(isDebugEnabled())
					debug("report " + space);
				progress = progressMap.get(key_);
				length = SIZE - progress;
				if(length > space) length = space;
				if(length == 0){
					downPort.doSending(new ByteStreamContract.Message(STOP, key_));
					progressMap.remove(key_);
					if(isDebugEnabled())
						debug(key_ + " send done: "+ progress);
				}else{
					downPort.doSending(new ByteStreamContract.Message(SEND, key_, data, progress, length));
					if(isDebugEnabled())
						debug("send bulk data to " + key_ + " start at " + progress +" len: "+ length);
					progressMap.put(key_, progress + length);
				}
			}else{
				drop(msg_);
				if(isDebugEnabled()){
					debug("drop " + msg_);
				}
			}
			break;
		case ByteStreamContract.STOP:
			if(isDebugEnabled())
				debug(key_ + " receive stop msg");
			progressMap.remove(key_);
			downPort.doSending(new ByteStreamContract.Message(STOP, key_));
		}
	}
}
