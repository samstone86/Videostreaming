public class FECpacket {
	int FEC_group = 500; // Anzahl an Medienpaketen für eine Gruppe

	final static int HEADER_SIZE = 12; // size of FEC header:
	public byte[] header; // Bitstream of header

	public int payload_size; // size of the RTP payload
	public byte[] payload; // Bitstream of the FEC payload

	public byte[] FEC_Package; // Bitstream of the FEC Package
	public byte[] FEC_TempPackage; // Bitstream of the temporary FEC Package

	int counter = 0; // dont XOR at first call

	int dataLength = 0;

	// Fields that compose the RTP header, used for compatibility
	public int Version;
	public int Padding;
	public int Extension;
	public int CC;
	public int Marker;
	public int PayloadType;
	public int SequenceNumber; // smallest SeqNr of a media package
	public int TimeStamp;
	public int Ssrc;

	public FECpacket() {

	}

	public FECpacket(int packageSizeFEC, int imgNumber) {
		Version = 2;
		Padding = 0;
		Extension = 0;
		CC = 0;
		Marker = 0;
		Ssrc = 0;
		PayloadType = 127;

		FEC_group = packageSizeFEC;
		SequenceNumber = imgNumber; // number of first rtp paket belonging to group

		header = new byte[HEADER_SIZE];

		payload_size = 0;
		FEC_TempPackage = new byte[payload_size];
		FEC_Package = new byte[payload_size];

		// fill the header array of byte with FEC header fields
		header[0] = (byte) (Version << 6); // V
		header[0] = (byte) (header[0] | Padding << 5); // P
		header[0] = (byte) (header[0] | Extension << 4); // X
		header[0] = (byte) (header[0] | CC); // X

		header[1] = (byte) (Marker << 7); // M
		header[1] = (byte) (header[1] | PayloadType); // PT

		header[2] = (byte) (SequenceNumber >> 8);
		header[3] = (byte) (SequenceNumber & 0xFF);
	}

	// ##########
	// Sender
	// ##########

	// nimmt Nutzdaten entgegen
	void setdata(byte[] data, int data_length) {
		if (this.payload_size < data_length) {
			this.payload_size = data_length;
			FEC_Package = new byte[payload_size];
			FEC_TempPackage = new byte[payload_size];
			System.arraycopy(FEC_Package, 0, FEC_TempPackage, 0, payload_size);
			System.arraycopy(FEC_TempPackage, 0, FEC_Package, 0, payload_size);
		}
		System.arraycopy(data, 0, FEC_TempPackage, 0, payload_size);

		if (counter > 0) {
			for (int i = 1; i <= payload_size; i++) {
				FEC_Package[i] = (byte) (FEC_Package[i] ^ FEC_TempPackage[i]);
			}
		}
		counter++;
	}

	// holt FEC-Paket (Länge -> längstes Medienpaket) int
	int getdata(byte[] data) {
		for (int i = 0; i < HEADER_SIZE; i++)
			data[i] = header[i];
		for (int i = 0; i < payload_size; i++)
			data[i + HEADER_SIZE] = payload[i];

		// counter reset
		counter = 0;
		// nach aufarbeiten des Rückgabewertes FEC_Package und FEC_TempPackage 0
		// initialisieren
		FEC_Package = new byte[0];
		FEC_TempPackage = new byte[0];

		return (payload_size + HEADER_SIZE);
	}

	// #######################################################
	// Empfänger
	// getrennete Puffer für Mediendaten und FEC
	// Puffergröße sollte Vielfaches der Gruppengröße sein
	// #######################################################

	// UDP-Payload, Nr. des Bildes bzw. TRP-SN
	void rcvdata(int nr, byte[] data) {
		Client.rtpBuffer.add(data);
		Client.rtpBufferCount.add(nr);
	}

	// FEC-Daten
	void rcvfec(int nr, byte[] data) {
		Client.fecBuffer.add(data);
		Client.fecBufferCount.add(nr);
	}

	// übergibt korrigiertes Paket oder Fehler (null)
	byte[] getjpg(int nr) {
		// else return rtpbuffer wo rtpbuffercount wert = nr
		byte[] rebuild = null;
		byte[] rebuildTemp = null;
		byte[] result = null;

		// if (Client.fecBuffer.isEmpty()) {
		//
		// }

		// // else if(){}
		// else {
		int i = 0;
		for (int a : Client.rtpBufferCount) {
			if (a == nr) {
				System.out.println("Bildnummer:" + a);
				return Client.rtpBuffer.get(i);
			}
			i++;
		}
		System.out.println("havent found it");
		// not found? reconstruct paket

		for (int j = 0; j < FEC_group; j++) {
			int size = Client.rtpBuffer.get(SequenceNumber + j).length;
			rebuild = new byte[size];
			int size2 = Client.rtpBuffer.get(SequenceNumber + j + 1).length;
			rebuildTemp = new byte[size2];

			int k = 0; // for each byte in the byte array
			rebuild = Client.rtpBuffer.get(j);
			rebuildTemp = Client.rtpBuffer.get(j + 1);
			for (byte b : rebuild) {
				rebuild[k] = (byte) (b ^ rebuildTemp[k]);
				k++;
			}
		}
		return rebuild;
	/*	byte[] arr = new byte[payload_size];
		if (FEC_Package == arr) {
			return null;
		} else {
			return FEC_Package;
		}
		*/
	}
	
	// helper
	public int getLength() {
		return (payload_size + HEADER_SIZE);
	}
}

/*
 * extra head am anfang gruppengröße in erstes bit
 * 
 * xor aus allen gruppenmitgliedern
 * 
 * payload 127
 * 
 * 
 */