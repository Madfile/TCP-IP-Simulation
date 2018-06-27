

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.SocketException;
import java.util.Arrays;
import java.util.PriorityQueue;
import java.util.Random;
import java.util.Scanner;

/**
 * The Sender
 */
public class Sender extends TransmitBaseOB {

	private String IP;
	private int portNum;
	private int mss;
	private int mws;
	private int above;
	private int offset = 0;
	private PriorityQueue<PacketBean> packetQueue;
	private SenderTimer senderTimer = null;
	private Random randomOB;
	private byte[] content;
	private int MAX_PACKET = 0;
	private double pdrop;
	private int ACK_retransmit = -1;
	private int Num_retransmit = 0;
	private int dataSegmentSent = 0;
	private int dropped = 0;
	private int delayed = 0;
	private int num_retransmitted = 0;
	private int ACK_duplicated = 0;
	private String filename;

	public Sender(final String address, final int port, String filename, int mws, int mss, int timeout, double pdrop,
			long seed) throws SocketException {
		super();
		randomOB = new Random(seed);
		this.IP = address;
		this.portNum = port;
		this.filename = filename;
		this.senderTimer = new SenderTimer(this, timeout);
		this.mss = mss;
		this.mws = mws;
		this.above = mss;
		if (mws < above) { // mss can not be over mws
			mss = above;
		}
		StringBuilder stringBuilder = new StringBuilder();
		Scanner filescanner_1;
		Scanner filescanner_2;
		try {
			filescanner_1 = new Scanner(new File(filename), "ASCII");
			//changed
			int lines = 0;
			while (filescanner_1.hasNextLine()) {
				lines++;
				filescanner_1.nextLine();
			}
			filescanner_2 = new Scanner(new File(filename), "ASCII");
			for (int i = 0; i < lines; i++) {
				stringBuilder.append(filescanner_2.nextLine());
				stringBuilder.append("\n");
			}
			byte[] data = stringBuilder.toString().getBytes();
			this.content = data;
		} catch (FileNotFoundException e1) {
			System.err.println("file not found !!!");
			e1.printStackTrace();
		}

		packetQueue = new PriorityQueue<PacketBean>();
		this.pdrop = pdrop;
		// LastSeq should be random but for clearance of the log file, we set it as 0 this time;
		// lastSeq = random.nextInt();
		// lastSeq = 0;
		// =============================== First SYN ==========================
		try {
			handshake();
		} catch (IOException e) {
			e.printStackTrace();
		}
		// =============================== Init SenderTimer ==========================
		senderTimer.set();
		// =============================== Listen ==========================
		if (mws / mss >= 0) {
			MAX_PACKET = mws / mss;
		} else {
			System.exit(-1);
		}
		listen(0);

	}

	public static void main(String[] args) throws FileNotFoundException {
		if (args.length != 8) {
			System.out.println("Wrong arguments");
			System.exit(0);
		}
		
		String ip = args[0];
		int port = Integer.parseInt(args[1]);
		String file = args[2];
		int mws = Integer.parseInt(args[3]);
		int mss = Integer.parseInt(args[4]);

		int timeout = Integer.parseInt(args[5]);
		double pdrop = Double.parseDouble(args[6]);
		long seed = Long.parseLong(args[7]);

		try {
			Sender sender = new Sender(ip, port, file, mws, mss, timeout, pdrop, seed);
		} catch (SocketException e) {
			e.printStackTrace();
			System.exit(-1);
		}
	}

	@Override
	public void sendPacket(PacketBean packet) throws IOException {
		Sequence_latest += packet.getDataLength();
		if (packet.getFlagString().equals("D")) {
			dataSegmentSent++;
			int trigger = randomOB.nextDouble() < pdrop ? 0 : 1;
			switch (trigger) {
			case 0:
				dropPacket(packet);
				dropped++;
				return;
			case 1:
				break;
			}
		}
		super.sendPacket(packet);
	}

	/**
	 * retransmit packets function
	 */
	public void retransmit() throws IOException {
		if (FINCheck())
			return;
		
		PacketBean current = packetQueue.peek();
		 if (isConnected()) {
			 int dropT = randomOB.nextFloat() < pdrop ? 0 : 1;
				switch (dropT) {
				case 0:
					dropPacket(current);
					dropped++;
				case 1:
					super.sendPacket(current);
				}
				num_retransmitted++;
		 } else {
		 headerPacketSender(current, IP, portNum);
		 }
		senderTimer.setCount();

	}

	/**
	 * sender First SYN handshake
	 */
	private void handshake() throws IOException {
		// 0-STOP,
		// 1-WAITING_HANDSHAKE,
		// 2-ESTABLISHED,
		// 3-FIN,
		// 4-FINISH;
		giveStatusPara(0); // stop current listening process
		byte[] temp_byte = null;
		PacketBean packetBean = new PacketBean(temp_byte);
		if (mss <= mws) {
			packetBean.setMss(mss);
		} else {
			mss = mws;
			packetBean.setMss(mss);
		}
		packetBean.setFlags(true, 1);// Flag set at SYN
		packetBean.setSeq(Sequence_latest);
		Sequence_latest = Sequence_latest + 1;
		packetQueue.add(packetBean);
		giveStatusPara(1);
		try {
			headerPacketSender(packetBean, IP, portNum);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * FIN
	 *
	 * @Precondition must be established state
	 */
	private void shutdown() throws IOException {
		// 0-STOP,
		// 1-WAITING_HANDSHAKE,
		// 2-ESTABLISHED,
		// 3-FIN,
		// 4-FINISH;
		giveStatusPara(3);
		PacketBean packet = new PacketBean(null);
		packet.setFlags(true, PacketBean.FIN);
		ByteFormer.setHeader(packet, Sequence_latest, Ack_latest);
		Sequence_latest++;
		packetQueue.add(packet);
		sendPacket(packet);
	}

	/**
	 * clean all acked packets in the sender window
	 *
	 * @param packet
	 *            the packet just received.
	 */
	private void cleanWindow(PacketBean packet) {
		PacketBean unACKPacket = packetQueue.peek();
		while (unACKPacket != null && unACKPacket.getSeq() + unACKPacket.getDataLength() <= packet.getAck()) {
			unACKPacket = packetQueue.poll();
			if (packetQueue.isEmpty()) {
				break;
			}
			unACKPacket = packetQueue.peek();
			senderTimer.setCount();
		}
	}

	/**
	 * the method to fill the sender window
	 */
	private void fillWindow() {
		while (packetQueue.size() < MAX_PACKET && offset < content.length) {
			int to = offset + mss;
			if (to > content.length) {
				to = content.length;
			}
			byte[] buffer = Arrays.copyOfRange(content, offset, to);

			PacketBean send = new PacketBean(null);
			send.setData(buffer);
			ByteFormer.setHeader(send, Sequence_latest, Ack_latest);
			send.setMss(to - offset);
			packetQueue.add(send);
			offset = to;
			try {
				sendPacket(send);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

	}

	private boolean FINCheck() {
		// FIN triggered
		if (offset == content.length && packetQueue.isEmpty()) {
			try {
				shutdown();
			} catch (IOException e) {
				e.printStackTrace();
			}
			return true;
		}
		return false;
	}

	@Override
	protected void onPacketReceived(PacketBean packet) {
		if (isConnected()) {
			if (packet.isFlagSet(PacketBean.ACK)) {
//				if (rttcalculator != null) {
//					rttcalculator.onPacketReceived(packet);
//				}
				if (packet.getAck() < Sequence_latest && packet.getAck() == ACK_retransmit && Num_retransmit == 3) {
					// System.err.println("Fast retransmit!");
					try {
						retransmit();
					} catch (IOException e) {
						e.printStackTrace();
					}
				} else {
					cleanWindow(packet);
					fillWindow();
				}
				if (ACK_retransmit != packet.getAck()) {
					ACK_retransmit = packet.getAck();
					Num_retransmit = 0;
				} else {
					ACK_duplicated++;
					Num_retransmit++;
				}
				if (FINCheck())
					return;
			} else {
				System.err.println("WTF PACKET RECEIVED, WHY IT'S NOT AN ACK?");

			}
		} else {
			System.out.println("Drop");
		}
	}

	@Override
	protected void onHandshakeReceived(PacketBean packet, String address, int port) {
		if (isConnected()) {
			return;
		}
		if (address.equals(this.IP) && port == this.portNum) {
			if (Ack_latest != -1)
				return;
			if (packet.isFlagSet(PacketBean.SYN) && packet.isFlagSet(PacketBean.ACK)
					&& packet.getAck() == Sequence_latest) {
				senderTimer.setCount();
				packetQueue.clear();
				PacketBean packet1 = new PacketBean(null);
				packet1.setMss(mss);
				packet1.setFlags(true, PacketBean.ACK);
				Ack_latest = packet.getSeq() + 1;
				ByteFormer.setHeader(packet1, Sequence_latest, Ack_latest);
				try {
					headerPacketSender(packet1, address, port);
				} catch (IOException e) {
					e.printStackTrace();
				}
				connectionSetUp(address, port);
				fillWindow();
				senderTimer.set();

			}

		}
	}

	@Override
	protected void onFinPacketReceived(PacketBean packet) {
		if (packet.isFlagSet(PacketBean.ACK)) {
			if (packet.getAck() == Sequence_latest) {
				packetQueue.clear();
				Ack_latest++;
				senderTimer.setCount();
			} else {
				System.out.println("ERROR! ACK");
				System.out.println(packet.getAck() + " " + Sequence_latest);
			}
		} else if (packet.isFlagSet(PacketBean.FIN)) {
			if (packet.getAck() == Sequence_latest) {
				PacketBean packet1 = new PacketBean(null);
				packet1.setFlags(true, PacketBean.ACK);
				ByteFormer.setHeader(packet1, Sequence_latest, Ack_latest);
				try {
					sendPacket(packet1);
				} catch (IOException e) {
					e.printStackTrace();
				}
				getLogger().senderLog(content.length, dataSegmentSent, dropped, delayed, num_retransmitted, ACK_duplicated);
				// 0-STOP,
				// 1-WAITING_HANDSHAKE,
				// 2-ESTABLISHED,
				// 3-FIN,
				// 4-FINISH;
				giveStatusPara(4);
//				if (rttcalculator != null) {
//					rttcalculator.finish();
//				}

				System.exit(1);
			} else {
				System.out.println("ERROR! FIN");
				System.out.println(packet.getAck() + " " + Sequence_latest);
			}
		}
	}
}
