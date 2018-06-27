package stp;



import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigDecimal;

public class STPLogger {
	
	private int error_flag = 0;
	private String format = "%-4s %-10s %-3s %-10d %-10d %-10s %n";
	private BufferedWriter bufferedWriter;
	private String packetTypeVar;
    private double timeOutput;
    private String outputTime;
	private String packetFlag;
	private int packetSeq;
	private int packetLength;
	private int packetACK;
	private int port;

	public STPLogger(String loggerFile) {
		File file = new File(loggerFile);
		int temp = 0;
		if (file.exists()) {
			temp = 1;
		} else {
			temp = 0;
		}
		switch (temp) {
		case 0:
			try {
				file.createNewFile();
			} catch (IOException e) {
				e.printStackTrace();
			}
		case 1:
			break;
		default:
			break;
		}
		try {
			this.bufferedWriter = new BufferedWriter(new FileWriter(file));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void recordLog(PacketType packetType, PacketBean packetBean, double time, int errorVar) {
		error_flag = errorVar;
		String loggerType = "notSet";
		switch (packetType) {
		case SEND:
			loggerType = "snd";
			break;
		case RECEIVE:
			loggerType = "rcv";
			break;
		case DROP:
			loggerType = "drop";
			break;
		}
		if (error_flag == 1) {
			System.err.println("error_flag is 1");
		}
		this.packetTypeVar = loggerType;
		BigDecimal bg = new BigDecimal(time);
		double timeToOutput = bg.setScale(2, BigDecimal.ROUND_HALF_UP).doubleValue();
		this.timeOutput = timeToOutput;
		java.text.DecimalFormat myformat=new java.text.DecimalFormat("0.00");
		outputTime = myformat.format(timeToOutput);
		this.packetFlag = packetBean.getFlagString();
		this.packetSeq = packetBean.getSeq();
		this.packetLength = packetBean.getDataLength();
		this.packetACK = packetBean.getAck();

		try {
			bufferedWriter
					.write(String.format(format, packetTypeVar, timeOutput, packetFlag, packetSeq, packetLength, packetACK));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void senderLog(int dataTransferred, int dataSegmentSend, int dropped, int delayed, int retransmitted,
			int duplicatedACK) {
		try {
			bufferedWriter.write("\n" + "DataTransferred: " + dataTransferred + "\n" + "DataSegmentSend: "
					+ dataSegmentSend + "\n" + "Dropped: " + dropped + "\n" + "Delayed: " + delayed + "\n"
					+ "Retransmitted: " + retransmitted + "\n" + "DuplicatedACK: " + duplicatedACK + "\n");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	//TODO:changed
	public void receiverLog(int data, int dataSegment, int duplicateSegment) {
		try {
			bufferedWriter.write("\n"+"DataReceived: " + data + "\n"+"DataSegmentReceived: " + dataSegment + "\n"+"DuplicateSegmentReceived: " + duplicateSegment + "\n");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}


	public void shutdown() {
		try {
			bufferedWriter.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public enum PacketType {
		SEND, RECEIVE, DROP;
	}


}
