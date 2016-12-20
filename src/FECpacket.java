import java.util.Arrays;

public class FECpacket {
	int FEC_group = 500; 	// Anzahl an Medienpaketen für eine Gruppe
	
    final static int HEADER_SIZE = 10;
	public byte[] header;	//Bitstream of header

	
	// size of the RTP payload
	public byte[] FEC_Package;
	public byte[] FEC_TmpPackage;
	public int payload_size = 0;
	int counter = 0;
	
	public byte[] payload;
	
	//##########
	// Sender
	//##########
	
	// nimmt Nutzdaten entgegen
	void setdata(byte[] data, int data_length) {
		if (this.payload_size < data_length) {
			this.payload_size = data_length;
			System.arraycopy(FEC_Package, 0, FEC_TmpPackage, 0, payload_size);
			FEC_Package = new byte[payload_size];
			System.arraycopy(FEC_TmpPackage, 0, FEC_Package, 0, payload_size);
		}
		System.arraycopy(data, 0, FEC_TmpPackage, 0, payload_size);
		
		if (counter > 0) {
			for (int i = 1; i <= payload_size; i++) {
				FEC_Package[i] = (byte) (FEC_Package[i]^FEC_TmpPackage[i]);
			}
		}
		counter++;
	}
	
	// holt FEC-Paket (Länge -> längstes Medienpaket) int
	int getdata(byte[] data) { 
		//counter reset
		counter = 0;
		
		
		
		
		
		//nach aufarbeiten des Rückgabewertes FEC_Package 0 initialisieren 
		FEC_Package = new byte[0];
		
		return 0;
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
