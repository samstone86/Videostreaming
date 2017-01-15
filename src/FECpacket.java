/*
 FECpacket Class
*/

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

// 0                   1                   2                   3
// 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
// +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
// |E|L|P|X|  cc   |M| PT recovery |            SN Base          |
// +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
// |                    timeStamp recovery                       |
// +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
// |                       length recovery                       |
// +=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+

class FECpacket {

	static int PayloadType = 127;
	static int SequenceNumber = 0;
	static int HEADER_SIZE = 3;

	// Bitstream of the RTP header
	public byte[] RTP_header;
	public byte[] FEC_Header;
	public byte[] payload;
	public int payloadSize;

	int k = 0;

	HashMap<Integer,RTPpacket> fecBuffer = new HashMap<Integer, RTPpacket>();
	Interface sender;

	public FECpacket(List<RTPpacket> rtp_packet) {
		payload = XOR(rtp_packet);
		RTP_header = RTPpacket.getHeader(PayloadType, 0, 0);
		FEC_Header = addSequenceNumber(rtp_packet.size(), rtp_packet.get(rtp_packet.size() - 1).SequenceNumber);
	}

	public FECpacket(byte[] data) {
		// RTP Header
		RTP_header = new byte[RTPpacket.HEADER_SIZE];
		System.arraycopy(data, 0, RTP_header, 0, RTP_header.length);

		// FEC Header
		FEC_Header = new byte[HEADER_SIZE];
		System.arraycopy(data, RTP_header.length, FEC_Header, 0, HEADER_SIZE);

		// Payload
		int fec_payloadSize = data.length - RTP_header.length - HEADER_SIZE;
		payload = new byte[fec_payloadSize];
		System.arraycopy(data, RTP_header.length + HEADER_SIZE, payload, 0,
				data.length - (RTP_header.length + HEADER_SIZE));

		k = getInt(FEC_Header[0]);
		SequenceNumber = getInt(FEC_Header[2]) + 256 * getInt(FEC_Header[1]);

	}

	// Function to store in FEC Header
	private byte[] addSequenceNumber(int k, int seqNr) {
		byte[] header = new byte[HEADER_SIZE];
		header[0] = (byte) k;
		header[1] = (byte) ((seqNr) >> 8);
		header[2] = (byte) (seqNr);
		return header;
	}

	public byte[] XOR(List<RTPpacket> rtp) {

		byte[] payload = new byte[payloadSize];

		for (RTPpacket rtp_packet : rtp) {
			payload = getnewPayload(rtp_packet.payload, payload);
		}
		return payload;
	}
	
	public static byte[] getnewPayload(byte[] payload1, byte[] payload2) {
		int maxLength = 0;
		
		if (payload1.length >= payload2.length)
			maxLength = payload1.length;
		else if (payload1.length < payload2.length)
			maxLength = payload2.length;				
		
		byte[] newPayload = new byte[maxLength];
		
		for (int i = 0; i < payload1.length; i++) {
			newPayload[i] = (byte) (payload1[i] ^ newPayload[i]);
		}
		for (int i = 0; i < payload2.length; i++) {
			newPayload[i] = (byte) (payload2[i] ^ newPayload[i]);
		}
		return newPayload;
	}

	public List<Integer> getSeqNr(int nr) {
		List<Integer> SeqNumber = new ArrayList<>();
		for (int i = SequenceNumber - k; i <= SequenceNumber; i++) {
			if (i != nr) {
				SeqNumber.add(i);
			}
		}
		return SeqNumber;
	}

	public byte[] getFecPacket() {
		byte[] fec_packet;
		int total_length = RTP_header.length + FEC_Header.length + payloadSize;
		fec_packet = new byte[total_length];

		System.arraycopy(RTP_header, 0, fec_packet, 0, RTP_header.length);
		System.arraycopy(FEC_Header, 0, fec_packet, RTP_header.length, FEC_Header.length);
		System.arraycopy(payload, 0, fec_packet, (RTP_header.length + FEC_Header.length), payloadSize);

		return fec_packet;
	}

	public boolean inRange(int index) {
		if (index <= SequenceNumber && index > SequenceNumber - k) {
			return true;
		}
		return false;
	}

	// return the unsigned value of 8-bit integer 
	int getInt(int nr) {
		if (nr >= 0) {
			return (nr);
		}
		else {
			return (256 + nr);
		}
	}

	public FECpacket(Interface con) {
		this.sender = con;
	}

	public FECpacket(HashMap<Integer,RTPpacket> buffer) {
		
	}
	
	// Store RTP-Packets
	void setdata(int nr, RTPpacket data) {
		fecBuffer.put(nr, data);
		if (fecBuffer.size() >= nr) {
			FECpacket fec_packet = new FECpacket(fecBuffer);
			fecBuffer.clear();
		}
	}
}