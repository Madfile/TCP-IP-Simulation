package stp;



import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.SocketException;
import java.util.PriorityQueue;


/**
 * The receiver
 */
public class Receiver extends TransmitBaseOB {

    private File file;
    private BufferedWriter bufferedWriter;
    private PriorityQueue<PacketBean> buffer = new PriorityQueue<>();

    //statistics
    private int receivedData = 0;
    private int receivedDataSegment = 0;
    private int segment_Duplicated = 0;


    public Receiver(int port, String fileName) throws SocketException {
        super(port);
        file = new File(fileName);
//		0-STOP,
//      1-WAITING_HANDSHAKE,
//      2-ESTABLISHED,
//      3-FIN,
//      4-FINISH;
        giveStatusPara(1);
        listen(0); //listen on a header value, must be changed after Handshake
    }

    public static void main(String[] args) throws SocketException {
		 if (args.length != 2) {
	         System.err.println("Wrong arguments");
	         System.exit(0);
	      }
        int port = Integer.parseInt(args[0]);
        String fileName = args[1];

        Receiver receiver = new Receiver(port, fileName);
    }

    /**
     * @param data the string to write to the file
     */
    //TODO:changed
    private void writeToTXT(String data) {
        try {
        int 	trigger = bufferedWriter == null?0:1;
        switch(trigger) {
        case 0:
        		int fileTrigger =  !file.exists()?0:1;
        		switch(fileTrigger) {
        		case 0:
        			file.createNewFile();
        			bufferedWriter = new BufferedWriter(new FileWriter(file));
        			break;
        		case 1:
        			bufferedWriter = new BufferedWriter(new FileWriter(file));
        			break;
        		}
        break;
        case 1:
        		break;
        }
//            if (bufferedWriter == null) {
//                if (!file.exists()) {
//                    file.createNewFile();
//                }
//                bufferedWriter = new BufferedWriter(new FileWriter(file));
//            }
            bufferedWriter.write(data);
            receivedData += data.length();


        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * try to get a cumulative the Ack number
     * selective ack
     */
    //changed
    private void bufferChecker() {
        PacketBean packetBean = buffer.peek();
//        if (packetBean == null) {
//            return;
//        }
        int trigger = packetBean == null?0:1;
        switch(trigger) {
        case 0:
        		return;
        case 1:
        		break;
        }
        int empty = 0;
        while (Ack_latest == packetBean.getSeq()) {
            packetBean = buffer.poll();
            Ack_latest += packetBean.getDataLength();
            writeToTXT(packetBean.getDataString());
            empty = buffer.isEmpty()?1:0;
//            if (buffer.isEmpty()) {
//                break;
//            }
            if(empty == 1) {
            		break;
            }
            packetBean = buffer.peek();
        }
    }


    @Override
    protected void onPacketReceived(PacketBean packet) {
//    	ACK = 0;
//    	SYN = 1;
//    	FIN = 2;
        if (packet.isFlagSet(2)) {
            //ACK
            setMss(0);
//    		0-STOP,
//          1-WAITING_HANDSHAKE,
//          2-ESTABLISHED,
//          3-FIN,
//          4-FINISH;
            giveStatusPara(3);
            PacketBean packet1 = new PacketBean(null);
//        	ACK = 0;
//        	SYN = 1;
//        	FIN = 2;
            packet1.setFlags(true, 0);
            Ack_latest = Ack_latest + 1;
            ByteFormer.setHeader(packet1, Sequence_latest, Ack_latest);
            try {
                sendPacket(packet1);
            } catch (IOException e) {
                e.printStackTrace();
            }

            //FIN
            PacketBean packet2 = new PacketBean(null);
            packet2.setFlags(true, PacketBean.FIN);
            Sequence_latest = Sequence_latest + 1;
            ByteFormer.setHeader(packet2, Sequence_latest, Ack_latest);
            try {
                sendPacket(packet2);
            } catch (IOException e) {
                e.printStackTrace();
            }

        } else {
            if (packet.getSeq() == Ack_latest) {
                //correct packet order
                receivedDataSegment++;
                Ack_latest += packet.getDataLength();
                writeToTXT(packet.getDataString());
            } else if (packet.getSeq() > Ack_latest) {
                boolean b = false;
                for (PacketBean temp : buffer) {
                    if (temp.getSeq() == packet.getSeq()) {
                        b = true;
                        break;
                    }
                }
                if (!b) {
                    buffer.add(packet);
                    receivedDataSegment++;
                } else {
                    segment_Duplicated++;
                }
            } else {
                segment_Duplicated++;
            }
            bufferChecker();
            PacketBean packet1 = new PacketBean(null);
            ByteFormer.setHeader(packet1, Sequence_latest, Ack_latest);
            packet1.setFlags(true, PacketBean.ACK);
            try {
                sendPacket(packet1);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    protected void onHandshakeReceived(PacketBean packet, String address, int port) {
        if (isConnected()) {
            return;
        }
        if (packet.isFlagSet(PacketBean.SYN)) {
            if (Ack_latest != -1)
                return;

            super.bufferPacketInfo(address, port);
            Ack_latest = packet.getSeq() + 1;
            PacketBean packet1 = new PacketBean(null);
            packet1.setFlags(true, PacketBean.ACK);
            packet1.setFlags(true, PacketBean.SYN);
            packet1.setData(null);
            ByteFormer.setHeader(packet1, Sequence_latest, Ack_latest);
            Sequence_latest++;
            try {
                headerPacketSender(packet1, address, port);
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else if (packet.isFlagSet(PacketBean.ACK)) {
            if ( compareAddress(address)&& comparePort(port) && Ack_latest == packet.getSeq()) {
            	//getFormerPort() == port
            	//getFormerAddress().equals(address)
                setMss(packet.getMss());
                connectionSetUp(address, port);
                //System.out.println("Handshake Done!");
            } else {
                System.out.println("drop ACK packet from sender, not reliable handshake, send SYN first!");
            }
        }

    }

    @Override
    protected void onFinPacketReceived(PacketBean packet) {
        if (packet.isFlagSet(PacketBean.ACK)) {
            if (packet.getAck() == Sequence_latest) { //check state FIN_WAIT_2
                try {
                    bufferedWriter.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                getLogger().receiverLog(receivedData, receivedDataSegment, segment_Duplicated);
//        		0-STOP,
//              1-WAITING_HANDSHAKE,
//              2-ESTABLISHED,
//              3-FIN,
//              4-FINISH;
                giveStatusPara(4);
                System.exit(1);
            } else {
                System.out.println("ERROR!");
            }
        }
    }
}
