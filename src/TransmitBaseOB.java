


import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;

/**
 * STP "socket"
 */
public abstract class TransmitBaseOB { 

    protected int Sequence_latest = 0;
    protected int Ack_latest = -1;
    private DatagramSocket datagramSocket;
    private State state = new State(0);
    private long time = System.nanoTime() / 1000000;
    private String formerAddress = "nohting";
    private int formerPort;
    private STPLogger logger;
    private DatagramPacket packet; //last packet received.
    private int mss = 0;

    //for sender constructor
    public TransmitBaseOB() throws SocketException {
        datagramSocket = new DatagramSocket();
        logger = new STPLogger("Sender_log.txt");
    }

    //for receiver constructor
    public TransmitBaseOB(int port) throws SocketException {
        datagramSocket = new DatagramSocket(port);
        logger = new STPLogger("Receiver_log.txt");
    }



    protected void listen(int mss) {
        this.mss = mss;
//		0-STOP,
//      1-WAITING_HANDSHAKE,
//      2-ESTABLISHED,
//      3-FIN,
//      4-FINISH;
        while (state.getStatus() != 4) {
            packet = new DatagramPacket(new byte[this.mss + PacketBean.FIRST_PART_HEADER_SIZE], this.mss + PacketBean.FIRST_PART_HEADER_SIZE);
            try {
                datagramSocket.receive(packet);
            } catch (IOException e) {
                e.printStackTrace();
                System.out.println("STPPacket Received IO Error!");
                System.exit(-1);
            }
            if (reliable(packet)) { //only allow either reliable packet or handshake stuff
                PacketBean stpPacket = new PacketBean(packet.getData());
                logger.recordLog(STPLogger.PacketType.RECEIVE, stpPacket, getTime(),0);
                switch(state.getStatus()) {
                case 3:
                		onFinPacketReceived(stpPacket);
                		break;
                	default:
                		onPacketReceived(stpPacket);
                		break;
                }
            } else if (state.getStatus() == 1) {
                PacketBean stpPacket = new PacketBean(packet.getData());
                logger.recordLog(STPLogger.PacketType.RECEIVE, stpPacket,getTime(), 0);
                onHandshakeReceived(new PacketBean(packet.getData()), packet.getAddress().getHostAddress(), packet.getPort());
            } else {
                System.err.println("Error with Listening!");

            }
        }
    }


    public void bufferPacketInfo(String connectionAddress, int port) {
        this.formerAddress = connectionAddress;
        this.formerPort = port;
    }

    
    public boolean compareAddress(String address) {
    		if(address.equals(formerAddress)) {
    			return true;
    		}
    		else {
    			return false;
    		}
    }


    public boolean comparePort(int port) {
    		if(port == formerPort) {
    			return true;
    		}
    		else {
    			return false;
    		}
    }


//	0-STOP,
//  1-WAITING_HANDSHAKE,
//  2-ESTABLISHED,
//  3-FIN,
//  4-FINISH;
    public boolean reliable(DatagramPacket packet) {
        return (state.getStatus() == 2 || state.getStatus() == 3) && packet.getPort() == this.formerPort && packet.getAddress().getHostAddress().equals(formerAddress);
    }


    public boolean isConnected() {
        return state.getStatus() == 2;
    }

    protected void giveStatusPara(int i) {
        this.state.setStatus(i);
        if (state.getStatus() == 4) {
            logger.shutdown();
        }
    }
    

    protected void setMss(int mss) {
        this.mss = mss;
    }

    //for header to send
    protected void headerPacketSender(PacketBean packet, String IP, int port) throws IOException {
        byte[] content = packet.changeFormToBytes();
        logger.recordLog(STPLogger.PacketType.SEND, packet,getTime(),0); 
        DatagramPacket p = new DatagramPacket(content, content.length, InetAddress.getByName(IP), port);
        datagramSocket.send(p);
    }

    //for packets after handshake
    protected void sendPacket(PacketBean packet) throws IOException {
        headerPacketSender(packet, formerAddress, formerPort);
    }


    protected void dropPacket(PacketBean packet) {
        logger.recordLog(STPLogger.PacketType.DROP, packet,getTime(),0);
    }


    protected STPLogger getLogger() {
        return logger;
    }


//	0-STOP,
//  1-WAITING_HANDSHAKE,
//  2-ESTABLISHED,
//  3-FIN,
//  4-FINISH;
    protected void connectionSetUp(String address, int port) {
        this.formerAddress = address;
        this.formerPort = port;
        state.setStatus(2);
    }



    private double getTime() {
		return (double) System.nanoTime() / 1000000 - time;
	}

    protected abstract void onPacketReceived(PacketBean packet);

    protected abstract void onHandshakeReceived(PacketBean packet, String address, int port);

    protected abstract void onFinPacketReceived(PacketBean packet);

    
	public static class State {
		private int status = 0;
//		0-STOP,
//      1-WAITING_HANDSHAKE,
//      2-ESTABLISHED,
//      3-FIN,
//      4-FINISH;

		public State(int status) {
			super();
			this.status = status;
		}

		public int getStatus() {
			return status;
		}

		public void setStatus(int status) {
			this.status = status;
		}
		
		
		
	}
}
