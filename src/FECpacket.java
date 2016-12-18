public class FECpacket {
	int FEC_group = 500; 	// Anzahl an Medienpaketen für eine Gruppe
	
    final static int HEADER_SIZE = 8;
	public byte[] header;	//Bitstream of header
	
    final static int BODY_SIZE = 24;
    public byte[] body;		//Bitstream of the body
	
	// size of the RTP payload
	public int payload_size;
	public byte[] FEC_Package;
	public byte[] FEC_TempPackage;
	int dataLength = 0;
	int counter = 0;
	
	//##########
	// Sender
	//##########
	
	// nimmt Nutzerdaten entgegen
	void setdata(byte[] data, int data_length) {
		
		if (this.dataLength < data_length) {
			this.dataLength = data_length;
			FEC_TempPackage = FEC_Package.clone();
			FEC_Package = new byte[data.length];
			FEC_Package = FEC_TempPackage.clone();
		}
		
		for (int i = 1; i <= data.length; i++) {
			FEC_Package[i] = (byte) (FEC_Package[i]^data[i]);
		}		
	}
	
	// holt FEC-Paket (Länge -> längstes Medienpaket)
	int getdata(byte[] data) { 
        //construct the packet = header + body
        System.arraycopy(header, 0, data, 0, HEADER_SIZE);
        System.arraycopy(body, 0, data, HEADER_SIZE, BODY_SIZE);

        //return total size of the packet
        return (BODY_SIZE + HEADER_SIZE);
	}
	
	//#######################################################
	// Empfänger
	// getrennete Puffer für Mediendaten und FEC
	// Puffergröße sollte Vielfaches der Gruppengröße sein
	//#######################################################
	
	// UDP-Payload, Nr. des Bildes bzw. TRP-SN
	void rcvdata(int nr, byte[] data) {	
		
	}
	
	//FEC-Daten
	void rcvfec(int nr, byte[] data) {	
		
	}
	
	// übergibt korrigiertes Paket oder Fehler (null)
	byte[] getjpg(int nr) {
		return null;		
	}
}

/*
extra head am anfang gruppengröße in erstes bit 

xor aus allen gruppenmitgliedern

payload 127


*/
