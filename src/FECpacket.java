import java.nio.Buffer;

public class FECpacket {
	int FEC_group = 500; 	// Anzahl an Medienpaketen für eine Gruppe
	
	// size of the RTP payload
	public int payload_size;
	public byte[] FEC_Package;
	public byte[] FEC_TempPackage;
	int dataLength = 0;
	int counter = 0;
	
	public FECpacket () {};
	
	public FECpacket (byte[] data, int data_length) {
		this (new RTPpacket(data, data_length));
	};
	
	public FECpacket (RTPpacket rtPpacket) {};
	
	
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
		RTPpacket fecPacket = new RTPpacket(127, counter, FEC_group, data, dataLength);
		fecPacket.getlength();
		return fecPacket.getlength();
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