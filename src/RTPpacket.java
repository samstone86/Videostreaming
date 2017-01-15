/*
 RTPpacket Class
*/

import java.util.Arrays;

public class RTPpacket {

	// size of the RTP header:
	static int HEADER_SIZE = 12;

	// Fields that compose the RTP header
	public static int Version = 0;
	public static int Padding;
	public static int Extension;
	public static int CC;
	public static int Marker;
	public int PayloadType;
	public int SequenceNumber;
	public int TimeStamp;
	public static int Ssrc;

	// Bitstream of the RTP header
	public byte[] header;
	// size of the RTP payload
	public int payload_size;
	// Bitstream of the RTP payload
	public byte[] payload;

	// --------------------------
	// Constructor of an RTPpacket object from header fields and payload
	// bitstream
	// --------------------------
	public RTPpacket(int payload_type, int imgNumber, int timestamp, byte[] data, int data_length) {
		// fill by default header fields:
		Version = 2;
		Padding = 0;
		Extension = 0;
		CC = 0;
		Marker = 0;
		Ssrc = 0;

		// fill changing header fields:
		SequenceNumber = imgNumber;
		TimeStamp = timestamp;
		PayloadType = payload_type;

		// build the header bistream:
		// --------------------------
		header = new byte[HEADER_SIZE];

		// fill the header array of byte with RTP header fields
		header[0] = (byte) (Version << (7 - 1));
		header[0] = (byte) (header[0] | Padding << (7 - 2));
		header[0] = (byte) (header[0] | Extension << (7 - 3));
		header[0] = (byte) (header[0] | CC);

		header[1] = (byte) (Marker << 7);
		header[1] = (byte) (header[1] | PayloadType);

		header[2] = (byte) (SequenceNumber >> 8);
		header[3] = (byte) (SequenceNumber & 0xFF);

		header[4] = (byte) (TimeStamp >> 24);
		header[5] = (byte) (TimeStamp >> 16);
		header[6] = (byte) (TimeStamp >> 8);
		header[7] = (byte) (TimeStamp & 0xFF);

		// fill the payload bitstream:
		// --------------------------
		payload_size = data_length;
		payload = new byte[data_length];

		// fill payload array of byte from data (given in parameter of the
		// constructor)
		payload = Arrays.copyOf(data, payload_size);
	}
	public byte[] setPayloadType(int payload_type) {
		header[1] = (byte) (header[1] | payload_type);
		return header;		
	}

	public static byte[] getHeader(int payload_type, int seqNr, int timestamp) {
		// build the header bistream:
		// --------------------------
		byte[] header = new byte[HEADER_SIZE];

		// is bit set check with HEX value
		header[0] = (byte) (Version << (7 - 1));
		header[0] = (byte) (header[0] | Padding << (7 - 2));
		header[0] = (byte) (header[0] | Extension << (7 - 3));
		header[0] = (byte) (header[0] | CC);

		header[1] = (byte) (Marker << 7);
		header[1] = (byte) (header[1] | payload_type);

		header[2] = (byte) (seqNr >> 8);
		header[3] = (byte) (seqNr & 0xFF);

		header[4] = (byte) (timestamp >> 24);
		header[5] = (byte) (timestamp >> 16);
		header[6] = (byte) (timestamp >> 8);
		header[7] = (byte) (timestamp & 0xFF);

		header[8] = (byte) (Ssrc >> 24);
		header[9] = (byte) (Ssrc >> 16);
		header[10] = (byte) (Ssrc >> 8);
		header[11] = (byte) Ssrc;

		return header;
	}

	// --------------------------
	// Constructor of an RTPpacket object from the packet bitstream
	// --------------------------
	public RTPpacket(byte[] data, int data_size) {

		// fill default fields:
		Version = 2;
		Padding = 0;
		Extension = 0;
		CC = 0;
		Marker = 0;
		Ssrc = 0;

		// check if total packet size is lower than the header size
		if (data_size >= HEADER_SIZE) {
			// get the header bitsream:
			header = new byte[HEADER_SIZE];

			for (int i = 0; i < HEADER_SIZE; i++)
				header[i] = data[i];

			// get the payload bitstream:
			payload_size = data_size - HEADER_SIZE;
			payload = new byte[payload_size];

			for (int i = HEADER_SIZE; i < data_size; i++)
				payload[i - HEADER_SIZE] = data[i];

			// interpret the changing fields of the header:
			PayloadType = header[1] & 127;
			SequenceNumber = getbitValue(header[3]) + 256 * getbitValue(header[2]);
			TimeStamp = getbitValue(header[7]) + 256 * getbitValue(header[6]) + 65536 * getbitValue(header[5])
					+ 16777216 * getbitValue(header[4]);
		}
	}

	// --------------------------
	// getbitValue: get value of 8-bit int
	// --------------------------
	static int getbitValue(int nr) {
		if (nr >= 0) return (nr);
		else return (256 + nr);
	}

	// --------------------------
	// getpayload: return the payload bistream of the RTPpacket and its size
	// --------------------------
	public int getpayload(byte[] data) {
		for (int i = 0; i < payload_size; i++)
			data[i] = payload[i];
		return (payload_size);
	}

	// --------------------------
	// getpayload_length: return the length of the payload
	// --------------------------
	public int getpayload_length() {
		return (payload_size);
	}

	// --------------------------
	// getlength: return the total length of the RTP packet
	// --------------------------
	public int getlength() {
		return (payload_size + HEADER_SIZE);
	}

	// --------------------------
	// getpayloadSize: returns the packet bitstream and its length
	// --------------------------
	public int getpayloadSize(byte[] data) {
		// construct the packet = header + payload
		for (int i = 0; i < HEADER_SIZE; i++)
			data[i] = header[i];
		for (int i = 0; i < payload_size; i++)
			data[i + HEADER_SIZE] = payload[i];
		// return total size of the packet
		return (payload_size + HEADER_SIZE);
	}

	// --------------------------
	// getpayload: get header + payload
	// --------------------------
	public byte[] getpayload() {
		byte[] data = new byte[getlength()];
		// construct the packet = header + payload
		for (int i = 0; i < HEADER_SIZE; i++)
			data[i] = header[i];
		for (int i = 0; i < payload_size; i++)
			data[i + HEADER_SIZE] = payload[i];
		// return total size of the packet
		return data;
	}

	// --------------------------
	// gettimestamp
	// --------------------------
	public int gettimestamp() {
		return (TimeStamp);
	}

	// --------------------------
	// getsequencenumber
	// --------------------------
	public int getsequencenumber() {
		return (SequenceNumber);
	}

	// --------------------------
	// getpayloadtype
	// --------------------------
	public int getpayloadtype() {
		return (PayloadType);
	}

	// ------------------------------
	// print headers without the SSRC
	// ------------------------------
	public void printheader() {
		for (int i = 0; i < (HEADER_SIZE - 4); i++) {
			for (int j = 7; j >= 0; j--)
				if (((1 << j) & header[i]) != 0)
					System.out.print("1");
				else
					System.out.print("0");
			System.out.print(" ");
		}
		System.out.println();
	}
}
