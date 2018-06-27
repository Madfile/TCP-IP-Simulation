

import java.util.Arrays;
import java.util.Comparator;

/**
 * The STP packet
 */
public class PacketBean implements Comparator<PacketBean>, Comparable<PacketBean> {
	// packet header
	public static int ACK = 0;
	public static int SYN = 1;
	public static int FIN = 2;
	public static int FIRST_PART_HEADER_SIZE = 14;
	private int seq = 0;
	private int ack = 0;
	private int mss = 0;
	// 0 -- ACK
	// 1 -- SYN
	// 2 -- FIN
	// 3-15 reserved.
	// there.
	private boolean[] flags = new boolean[8];
	// data
	private byte[] data = null;


	public PacketBean(byte[] packet) {
		if (packet == null) {
			return;
		}
		seq = ByteFormer.headerBytesGetter(packet, 0); // 3
		ack = ByteFormer.headerBytesGetter(packet, 4); // 7
		mss = ByteFormer.headerBytesGetter(packet, 8); // 11
		// bits to boolean
		int largestBytes = 12;
		int temp_var = 0;
		while (temp_var < largestBytes - 4) {
			flags[temp_var] = (temp_var < 8) ? ((packet[largestBytes] & (0b00000001 << temp_var)) != 0)
					: ((packet[largestBytes + 1] & (0b00000001 << temp_var)) != 0);
			temp_var++;
		}
		//13
		if (FIRST_PART_HEADER_SIZE != packet.length) {
			data = Arrays.copyOfRange(packet, FIRST_PART_HEADER_SIZE, FIRST_PART_HEADER_SIZE + mss); // packet.length
		}

	}

	public PacketBean() {
	}

	public void setFlags(boolean b, int flag) {
		flags[flag] = b;
	}

	public String getDataString() {
		return new String(data);
	}

	public int getSeq() {
		return seq;
	}

	public void setSeq(int seq) {
		if (seq >= 0) {
			this.seq = seq;
		} else {
			System.err.println("error with seq");
		}
	}

	public int getAck() {
		return ack;
	}

	public void setAck(int ack) {
		if (ack >= 0) {
			this.ack = ack;
		} else {
			System.err.println("error with Ack");
		}
	}

	public int getMss() {
		return mss;
	}

	public void setMss(int mss) {
		if (mss > 0) {
			this.mss = mss;
		} else {
			System.err.println("error with mss");
		}
	}

	public int getDataLength() {
		int returnLength = 0;
		returnLength = data == null ? 0 : data.length;
		return returnLength;
	}

	public void setData(byte[] buffer) {
		this.data = buffer;
	}

	public boolean isFlagSet(int position) {
		return flags[position];
	}

	public byte[] changeFormToBytes() {
		int size = FIRST_PART_HEADER_SIZE;
		try {
			size += data.length;
		} catch (Exception e) {

		}
		byte[] sentBack = new byte[size];
		ByteFormer.headerBytesFill(seq, sentBack, 0);
		ByteFormer.headerBytesFill(ack, sentBack, 4);
		ByteFormer.headerBytesFill(mss, sentBack, 8);
		int temp_byte = 12;
		for (int i = 0; i < temp_byte - 4; i++) {
			if (!flags[i]) {
				continue;
			}
			switch (i) {
			case 0:
				sentBack[temp_byte] = (byte) (sentBack[12] | (0b00000001 << i));
			case 1:
				sentBack[temp_byte] = (byte) (sentBack[12] | (0b00000001 << i));
			case 2:
				sentBack[temp_byte] = (byte) (sentBack[12] | (0b00000001 << i));
			case 3:
				sentBack[temp_byte] = (byte) (sentBack[12] | (0b00000001 << i));
			case 4:
				sentBack[temp_byte + 1] = (byte) (sentBack[13] | (0b00000001 << i));
			case 5:
				sentBack[temp_byte + 1] = (byte) (sentBack[13] | (0b00000001 << i));
			case 6:
				sentBack[temp_byte + 1] = (byte) (sentBack[13] | (0b00000001 << i));
			case 7:
				sentBack[temp_byte + 1] = (byte) (sentBack[13] | (0b00000001 << i));
			}
		}
		int data_flag = 0;
		if (data == null) {
			data_flag = 1;
		}
		switch (data_flag) {
		case 1:
			break;
		case 0:
			for (int i = 0; i < data.length; i++) {
				sentBack[i + FIRST_PART_HEADER_SIZE] = data[i];
			}
		}
		return sentBack;
	}

	public String getFlagString() {
		StringBuilder stringBuilder = new StringBuilder();
		int temp_var;
		temp_var = 1;
		if (flags[temp_var]) {
			stringBuilder.append("S");
		}
		temp_var = 0;
		if (flags[temp_var]) {
			stringBuilder.append("A");
		}
		temp_var = 2;
		if (flags[temp_var]) {
			stringBuilder.append("F");
		}
		if ((!flags[SYN] && !flags[ACK] && !flags[FIN]) && data != null) {
			stringBuilder.append("D");
		}
		return stringBuilder.toString();
	}

	@Override
	public int compare(PacketBean o1, PacketBean o2) {
		return o1.seq - o2.seq;
	}

	@Override
	public int compareTo(PacketBean o) {
		return getSeq() - o.getSeq();
	}
}
