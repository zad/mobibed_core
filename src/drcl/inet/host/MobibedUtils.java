package drcl.inet.host;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

public class MobibedUtils {
	public static byte[] toByteArray(Object obj){
		byte[] bytes = null;
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		try{
			ObjectOutputStream oos = new ObjectOutputStream(bos);
			oos.writeObject(obj);
			oos.flush();
			oos.close();
			bytes = bos.toByteArray();
		}catch(IOException ioe){
			System.out.println("JHUUnit.toByteArray IO error");
			ioe.printStackTrace();
		}
		return bytes;
	}
	
	public static Object toObject(byte[] bytes){
		Object obj = null;
		try{
			ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
			ObjectInputStream ois = new ObjectInputStream(bis);
			obj = ois.readObject();
		}catch(IOException ioe){
			System.out.println("JHUUnit.toObject IO error");
		}catch(ClassNotFoundException cnfe){
			System.out.println("JHUUnit.toObject classNotFound error");
		}
		return obj;
	}
	
	public static long byteArrayToLong(byte[] pumpeIPAddressRaw)
	{
		long pumpeIPAddress =
			      ((pumpeIPAddressRaw [0] & 0xFFl) << (24)) + 
			      ((pumpeIPAddressRaw [1] & 0xFFl) << (16)) +
			      ((pumpeIPAddressRaw [2] & 0xFFl) << (8)) +
			      (pumpeIPAddressRaw [3] &  0xFFl); 
		return pumpeIPAddress;
	}
	
	public static long byteArrayToLong(byte[] pumpeIPAddressRaw, int pos)
	{
		long pumpeIPAddress =
			      ((pumpeIPAddressRaw [pos+0] & 0xFFl) << (24)) + 
			      ((pumpeIPAddressRaw [pos+1] & 0xFFl) << (16)) +
			      ((pumpeIPAddressRaw [pos+2] & 0xFFl) << (8)) +
			      (pumpeIPAddressRaw [pos+3] &  0xFFl); 
		return pumpeIPAddress;
	}
	
	public static byte[] LongToByteArray(long ip){
		byte[] addr = new byte[4];
		for(int i=0, shift = 24; i<4;i++,shift-=8)
		{
			long value = (ip>>shift) & 0xff;
			addr[i] = (byte)value;
		}
		return addr;
	}
	
	public static void LongToByteArray(long ip, byte[] buf, int offset){
		for(int i=0, shift = 24; i<4;i++,shift-=8)
		{
			long value = (ip>>shift) & 0xff;
			buf[i+offset] = (byte)value;
		}
	}
	
	public static String LongToIPString(long ip){
		StringBuilder addr = new StringBuilder();
		for(int i=0, shift = 24; i<4;i++,shift-=8)
		{
			long value = (ip>>shift) & 0xff;
			addr.append(value);
			if(i<3)
				addr.append(".");
		}
		return addr.toString();
	}
	
	/**
	   * Calculate the Internet Checksum of a buffer (RFC 1071 - http://www.faqs.org/rfcs/rfc1071.html)
	   * Algorithm is
	   * 1) apply a 16-bit 1's complement sum over all octets (adjacent 8-bit pairs [A,B], final odd length is [A,0])
	   * 2) apply 1's complement to this final sum
	   *
	   * Notes:
	   * 1's complement is bitwise NOT of positive value.
	   * Ensure that any carry bits are added back to avoid off-by-one errors
	   *
	   *
	   * @param buf The message
	   * @return The checksum
	   */
	public static long calculateChecksum(byte[] buf, int length) {
	    int i = 0;

	    long sum = 0;
	    long data;

	    // Handle all pairs
	    while (length > 1) {
	    	// Corrected to include @Andy's edits and various comments on Stack Overflow
	    	data = (((buf[i] << 8) & 0xFF00) | ((buf[i + 1]) & 0xFF));
	    	sum += data;
	    	// 1's complement carry bit correction in 16-bits (detecting sign extension)
	    	if ((sum & 0xFFFF0000) > 0) {
	    		sum = sum & 0xFFFF;
	    		sum += 1;
	    	}

	    	i += 2;
	    	length -= 2;
	    }

	    // Handle remaining byte in odd length buffers
	    if (length > 0) {
	    	// Corrected to include @Andy's edits and various comments on Stack Overflow
	    	sum += (buf[i] << 8 & 0xFF00);
	    	// 1's complement carry bit correction in 16-bits (detecting sign extension)
	    	if ((sum & 0xFFFF0000) > 0) {
	    		sum = sum & 0xFFFF;
	    		sum += 1;
	    	}
	    }

	    // Final 1's complement value correction to 16-bits
	    sum = ~sum;
	    sum = sum & 0xFFFF;
	    return sum;

	 }

	public static byte[] DoubleToByteArray(double aTS) {
		// TODO Auto-generated method stub
		return null;
	}

	public static long byteArrayToDouble(byte[] bytes, int i) {
		// TODO Auto-generated method stub
		return 0;
	}
}
