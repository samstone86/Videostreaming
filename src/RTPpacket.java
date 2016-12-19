import java.util.Arrays;

public class FECpacket {
	int FEC_group = 500; 	// Anzahl an Medienpaketen für eine Gruppe
	
    final static int HEADER_SIZE = 10;
	public byte[] header;	//Bitstream of header

	
	// size of the RTP payload
	public int payload_size;
	public byte[] FEC_Package;
	public byte[] FEC_TempPackage;
	int dataLength = 0;
	int counter = 0;
	
	public byte[] payload;
	
	//##########
	// Sender
	//##########
	
	// nimmt Nutzdaten entgegen
	void setdata(byte[] data, int data_length) {
		
	}
	
	// holt FEC-Paket (Länge -> längstes Medienpaket) int
	int getdata(byte[] data) { 
		
		if (this.dataLength < data.length) {
			this.dataLength = data.length;
			FEC_TempPackage = Arrays.copyOf(data, dataLength);
			FEC_Package = Arrays.copyOf(FEC_TempPackage, dataLength + HEADER_SIZE);
		}

		for (int i = 1; i <= dataLength; i++) {
			FEC_Package[i] = (byte) (FEC_Package[i]^data[i]);
		}
		
		// return total size of the packet
		return (dataLength + HEADER_SIZE);
		
		/*		
		if (this.dataLength < data.length) {
			this.dataLength = data.length;
			FEC_TempPackage = FEC_Package.clone();
			FEC_Package = new byte[data.length];
			FEC_Package = FEC_TempPackage.clone();
		}
		
		for (int i = 1; i <= data.length; i++) {
			FEC_Package[i] = (byte) (FEC_Package[i]^data[i]);
		}		
		*/
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
