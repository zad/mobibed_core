package drcl.inet.transport;

/**
 * extends bi-directional single-session TCP protocol 
 * add TCP connection establishment: three way handshaking
 * add TCP connection termination
 * @author andong
 *
 */
public class TCPClient extends TCPb{
	public String getName()
	{ return "tcpclient"; }
	
	public TCPClient()
	{ super(); state = CLOSED;}
	
	public TCPClient(String id_)
	{ super(id_); state = CLOSED;}
}
