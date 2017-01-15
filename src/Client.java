/*
 Client
 usage: java Client [Server hostname] [Server RTSP listening port] [Video file requested]
*/

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.*;
import java.net.*;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

public class Client {

	// GUI
	// ----
	JFrame f = new JFrame("Client");
	JButton setupButton = new JButton("Setup");
	JButton playButton = new JButton("Play");
	JButton pauseButton = new JButton("Pause");
	JButton tearButton = new JButton("Teardown");
	JButton optionButton = new JButton("Options");
	JPanel mainPanel = new JPanel();
	JPanel buttonPanel = new JPanel();
	
	JLabel statistic = new JLabel();
	
	JLabel iconLabel = new JLabel();
	ImageIcon icon;

	// Statistics:
	// ------------------
	int PackageCounter = 0;
	int LostPackage = 0;
	double LossRate = 0.0;
	float PercentLossRate = 0;
	double DR = 0.0;	

	// RTP variables:
	// ----------------
	DatagramPacket rcvdp; // UDP packet received from the server
	DatagramSocket RTPsocket; // socket to be used to send and receive UDP packets
	static int RTP_RCV_PORT = 25000; // port where the client will receive the RTP packets

	Timer timer; // timer used to receive data from the UDP socket
	Timer statTimer;
	Timer fecTimer;
	byte[] buf; // buffer used to store data received from the server

	// Buffers
	List<RTPpacket> rtpBuffer = new ArrayList<>();
	List<Integer> rtpBufferLost = new ArrayList<>();
	List<Integer> fecImgBuffer;
	List<RTPpacket> fecBuffer = new ArrayList<RTPpacket>();

	int LOST_PackagesCount = 0;
	int LOST_Packages = 0;
	int FEC_Number = 0;
	int FEC_PastNumber = 0;
	int SEQ_PastNr = 0;
	
	// RTSP variables
	// ----------------
	// rtsp states
	final static int INIT = 0;
	final static int READY = 1;
	final static int PLAYING = 2;
	static int state; // RTSP STATE == INIT or READY or PLAYING
	Socket RTSPsocket; // socket used to send/receive RTSP messages

	// input and output stream filters
	static BufferedReader RTSPBufferedReader;
	static BufferedWriter RTSPBufferedWriter;
	static String VideoFileName; // video file to request to the server
	int RTSPSeqNb = 0; // Sequence number of RTSP messages within the session
	int RTSPid = 0; // ID of the RTSP session (given by the RTSP Server)

	final static String CRLF = "\r\n";

	// Video constants:
	// ------------------
	static int MJPEG_TYPE = 26; // RTP payload type for MJPEG video

	// --------------------------
	// Constructor
	// --------------------------

	public Client() {
		// build GUI
		// --------------------------

		// FrameRTSPBufferedReader
		f.addWindowListener(new WindowAdapter() {
			public void windowClosing(WindowEvent e) {
				System.exit(0);
			}
		});

		// Buttons
		buttonPanel.setLayout(new GridLayout(1, 0));
		buttonPanel.add(setupButton);
		buttonPanel.add(playButton);
		buttonPanel.add(pauseButton);
		buttonPanel.add(tearButton);
		buttonPanel.add(optionButton);

		setupButton.addActionListener(new setupButtonListener());
		playButton.addActionListener(new playButtonListener());
		pauseButton.addActionListener(new pauseButtonListener());
		tearButton.addActionListener(new tearButtonListener());
		optionButton.addActionListener(new optionButtonListener());

		// Image display label
		iconLabel.setIcon(null);

		// frame layout
		mainPanel.setLayout(null);
		mainPanel.add(iconLabel);
		mainPanel.add(buttonPanel);
		mainPanel.add(statistic);
		iconLabel.setBounds(0, 0, 380, 280);
		buttonPanel.setBounds(0, 280, 380, 50);
		statistic.setBounds(0, 340, 440, 100);

		f.getContentPane().add(mainPanel, BorderLayout.CENTER);
		f.setSize(new Dimension(400, 490));
		f.setVisible(true);

		updateLabels();

		// init timer
		// --------------------------
		timer = new Timer(40, new timerListener());
		timer.setInitialDelay(1500);
		timer.setCoalesce(true);

		statTimer = new Timer(1000, new timerListenerSetText());

		fecTimer = new Timer(20, new timerListenerFec());
		fecTimer.setInitialDelay(0);
		fecTimer.setCoalesce(true);

		// allocate enough memory for the buffer used to receive data from the server
		buf = new byte[15000];
	}

	// ------------------------------------
	// main
	// ------------------------------------
	public static void main(String argv[]) throws Exception {

		// Create a Client object
		Client theClient = new Client();

		// get server RTSP port and IP address from the command line
		// ------------------
		int RTSP_server_port = Integer.parseInt(argv[1]);
		String ServerHost = argv[0];
		InetAddress ServerIPAddr = InetAddress.getByName(ServerHost);

		// get video filename to request:
		VideoFileName = argv[2];

		// Establish a TCP connection with the server to exchange RTSP messages
		// ------------------
		theClient.RTSPsocket = new Socket(ServerIPAddr, RTSP_server_port);

		// Set input and output stream filters:
		RTSPBufferedReader = new BufferedReader(new InputStreamReader(theClient.RTSPsocket.getInputStream()));
		RTSPBufferedWriter = new BufferedWriter(new OutputStreamWriter(theClient.RTSPsocket.getOutputStream()));

		// init RTSP STATE:
		state = INIT;
	}

	// ------------------------------------
	// Handler for buttons
	// ------------------------------------

	// Handler for Setup button
	// -----------------------
	class setupButtonListener implements ActionListener {
		public void actionPerformed(ActionEvent e) {
			System.out.println("Setup Button pressed !");

			if (state == INIT) {
				// Init non-blocking RTPsocket that will be used to receive data
				try {
					// construct a new DatagramSocket to receive RTP packets from the server on port RTP_RCV_PORT
					RTPsocket = new DatagramSocket(RTP_RCV_PORT);
					RTPsocket.setSoTimeout(5); // set TimeOut value of the socket to 5msec.
				} catch (SocketException se) {
					System.out.println("setupButtonListener Socket exception: " + se);
					se.printStackTrace();
					System.exit(0);
				}

				// init RTSP sequence number
				RTSPSeqNb = 1;
				// Send SETUP message to the server
				send_RTSP_request("SETUP");
				// Wait for the response
				if (parse_server_response() != 200)
					System.out.println("Invalid Server Response");
				else {
					// change RTSP STATE and print new STATE
					state = READY;
					System.out.println("New RTSP STATE: READY");
				}
			}
		}
	}

	// Handler for Play button
	// -----------------------
	class playButtonListener implements ActionListener {
		public void actionPerformed(ActionEvent e) {
			System.out.println("Play Button pressed !");

			if (state == READY) {
				// increase RTSP sequence number
				RTSPSeqNb++;
				// Send PLAY message to the server
				send_RTSP_request("PLAY");
				// Wait for the response
				if (parse_server_response() != 200)
					System.out.println("Invalid Server Response");
				else {
					// change RTSP STATE and print out new STATE
					state = PLAYING;
					System.out.println("New RTSP STATE: PLAYING");
					// start the timer
					timer.start();
					statTimer.start();
					fecTimer.start();
				}
			}
		}
	}

	// Handler for Pause button
	// -----------------------
	class pauseButtonListener implements ActionListener {
		public void actionPerformed(ActionEvent e) {
			System.out.println("Pause Button pressed !");

			if (state == PLAYING) {
				// increase RTSP sequence number
				RTSPSeqNb++;
				// Send PAUSE message to the server
				send_RTSP_request("PAUSE");
				// stop the timer
				timer.stop();
				statTimer.stop();
				// Wait for the response
				if (parse_server_response() != 200)
					System.out.println("Invalid Server Response");
				else {
					// change RTSP STATE and print out new STATE
					state = READY;
					System.out.println("New RTSP STATE: READY");
				}
			}
		}
	}

	// Handler for Teardown button
	// -----------------------
	class tearButtonListener implements ActionListener {
		public void actionPerformed(ActionEvent e) {
			System.out.println("Teardown Button pressed !");

			// increase RTSP sequence number
			RTSPSeqNb++;
			// Send TEARDOWN message to the server
			send_RTSP_request("TEARDOWN");
			// Wait for the response
			if (parse_server_response() != 200)
				System.out.println("Invalid Server Response");
			else {
				// change RTSP STATE and print out new STATE
				state = INIT;
				System.out.println("New RTSP STATE: INIT");
				// stop the timer
				timer.stop();
				statTimer.stop();
				// exit
				System.exit(0);
			}
		}
	}

	// Handler for Option button
	// -----------------------
	class optionButtonListener implements ActionListener {
		public void actionPerformed(ActionEvent e) {

			if (state == READY) {
				RTSPSeqNb++;
				send_RTSP_request("OPTIONS");
				// Wait for the response
				if (parse_server_response() != 200)
					System.out.println("Invalid Server Response");
				else {
					try {
						System.out.println(RTSPBufferedReader.readLine());
					} catch (Exception eoBL) {
						System.out.println("OPTIONS causes Error: " + eoBL);
						eoBL.printStackTrace();
					}

					state = READY;
					System.out.println("New RTSP STATE: OPTIONS");
					// stop the timer
					timer.stop();
					statTimer.stop();
				}
			}
		}
	}

	// ------------------------------------
	// Handler for timer
	// ------------------------------------
	class timerListener implements ActionListener {
		public void actionPerformed(ActionEvent e) {
			if (rtpBuffer.size() == 0) return;

			RTPpacket image_packet = rtpBuffer.get(0);
			
			// get the payload bitstream from the RTPpacket object
			int payload_length = image_packet.getpayload_length();
			byte[] payload = new byte[payload_length];
			image_packet.getpayload(payload);

			// get an Image object from the payload bitstream
			Toolkit toolkit = Toolkit.getDefaultToolkit();
			Image image = toolkit.createImage(payload, 0, payload_length);

			// display the image as an ImageIcon object
			icon = new ImageIcon(image);
			iconLabel.setIcon(icon);
			rtpBuffer.remove(0);
		}
	}

	class timerListenerFec implements ActionListener {
		public void actionPerformed(ActionEvent e) {
			// Construct a DatagramPacket to receive data from the UDP socket
			rcvdp = new DatagramPacket(buf, buf.length);

			try {
				// receive the DP from the socket:
				RTPsocket.receive(rcvdp);
				// create an RTPpacket object from the DP
				RTPpacket rtp_packet = new RTPpacket(rcvdp.getData(), rcvdp.getLength());
				// print important header fields of the RTP packet received:
				System.out.println("Got RTP packet with SeqNum # " + rtp_packet.getsequencenumber() + " timeStamp "
						+ rtp_packet.gettimestamp() + " ms, of type " + rtp_packet.getpayloadtype());
				// print header bitstream:
				rtp_packet.printheader();
				// get the payload bitstream from the RTPpacket object
				int payload_length = rtp_packet.getpayload_length();
				byte[] payload = new byte[payload_length];
				rtp_packet.getpayload(payload);
				
				// Statistics
				if (rtp_packet.getsequencenumber() == SEQ_PastNr + 1) {
					PackageCounter++;
				} else {
					LostPackage = LostPackage + (rtp_packet.getsequencenumber() - SEQ_PastNr - 1);
					LostPackage++;
				}
				SEQ_PastNr = rtp_packet.getsequencenumber();
				DR = (PackageCounter / (double) rtp_packet.gettimestamp()) * 1000;
				LossRate = (LostPackage / (double) rtp_packet.gettimestamp()) * 1000;
				PercentLossRate = (LostPackage * 100) / (PackageCounter + LostPackage);

				// FEC Error Correction
				if (rtp_packet.getpayloadtype() == 127) {
					// FEC Packet
					System.out.println("Enter Error Correction");
					FECpacket fec_packet = new FECpacket(rtp_packet.getpayload());

					// Is LOST_Packages in Range of FEC packet and is it restoreable
					for (int i = 0; i < rtpBufferLost.size(); i++) {
						LOST_PackagesCount = rtpBufferLost.get(i);
						// is Packet in Range of FECGroup
						if (fec_packet.inRange(LOST_PackagesCount)) {
							LOST_Packages++;
						}
					}
					System.out.println(LOST_Packages);
					if (LOST_Packages > 1) {
						System.out.println("Konnte Packet nicht wiederherstellen.\nMehrere Packete fehlen im FEC-Packet");
					}
					// Restore
					if (LOST_Packages == 1) {
						fecImgBuffer = fec_packet.getSeqNr(LOST_PackagesCount);
						for (int fec_ImgNr : fecImgBuffer) {
							for (RTPpacket rtp : rtpBuffer) {
								System.out.println("SEQ: " + rtp.SequenceNumber);
								System.out.println("SEQ2: " + rtp.getsequencenumber());
								if (rtp.SequenceNumber == fec_ImgNr) {
									fecBuffer.add(rtp);
								}
							}
						}

						byte[] restored_payload = FECpacket.getnewPayload(fec_packet.payload, new byte[0]);
						if (fecBuffer.size() == fecImgBuffer.size()) {
							for (RTPpacket packet : fecBuffer) {
								restored_payload = FECpacket.getnewPayload(restored_payload, packet.payload);
							}
						}

						// add restored packet to List for display
						RTPpacket recover_packet = new RTPpacket(0, LOST_PackagesCount, 0, restored_payload, restored_payload.length);
						for (int i = 0; i < rtpBuffer.size(); i++) {
							if (LOST_PackagesCount > rtpBuffer.get(i).SequenceNumber && LOST_PackagesCount < rtpBuffer.get(i + 1).SequenceNumber) {
								rtpBuffer.add(i + 1, recover_packet);
							}
						}
					}
				}

				// Storage to Display them in timerListener
				rtpBuffer.add(rtp_packet);

				FEC_Number = rtp_packet.getsequencenumber();
				if (FEC_Number != FEC_PastNumber + 1) {
					// Store index of packetslost
					rtpBufferLost.add(FEC_PastNumber + 1);
				}
				FEC_PastNumber = FEC_Number;
			} catch (InterruptedIOException iioe) {
			} catch (IOException ioe) {
				System.out.println("timerListenerFec IOException caught: " + ioe);
				ioe.printStackTrace();
			}
		}
	}

	class timerListenerSetText implements ActionListener {
		public void actionPerformed(ActionEvent e) {
			updateLabels();
		}
	}
	
	private void updateLabels() {
		statistic.setText("<html>" 
				+ "Packete erhalten gesamt: " + PackageCounter 
				+ "<br>Packete verloren: " + LostPackage
				+ "<br>Packetverlustrate: " + new DecimalFormat("##.##").format(LossRate)+ " Pkt/sec" 
				+ "<br>Datenrate: " + new DecimalFormat("##.##").format(DR) + " Frames/sec"
				+ "<br>Pakete verloren in Prozent: " + new DecimalFormat("##.##").format(PercentLossRate) + " %"
				+ "</html>");
	}

	// ------------------------------------
	// Parse Server Response
	// ------------------------------------
	private int parse_server_response() {
		int reply_code = 0;

		try {
			// parse status line and extract the reply_code:
			String StatusLine = RTSPBufferedReader.readLine();
			System.out.println("RTSP Client - Received from Server:");
			System.out.println(StatusLine);

			StringTokenizer tokens = new StringTokenizer(StatusLine);
			tokens.nextToken(); // skip over the RTSP version
			reply_code = Integer.parseInt(tokens.nextToken());

			// if reply code is OK get and print the 2 other lines
			if (reply_code == 200) {
				String SeqNumLine = RTSPBufferedReader.readLine();
				System.out.println(SeqNumLine);

				String SessionLine = RTSPBufferedReader.readLine();
				System.out.println(SessionLine);

				tokens = new StringTokenizer(SessionLine);
				String temp = tokens.nextToken();
				// if state == INIT gets the Session Id from the SessionLine
				if (state == INIT && temp.compareTo("Session:") == 0) {
					RTSPid = Integer.parseInt(tokens.nextToken());
				} else if (temp.compareTo("Content-Base:") == 0) {
					// Get the DESCRIBE lines
					String newLine;
					for (int i = 0; i < 6; i++) {
						newLine = RTSPBufferedReader.readLine();
						System.out.println(newLine);
					}
				}
			}
		} catch (Exception ex) {
			System.out.println("parse_server_response Exception caught: " + ex);
			ex.printStackTrace();
			System.exit(0);
		}
		return (reply_code);
	}

	// ------------------------------------
	// Send RTSP Request
	// ------------------------------------

	private void send_RTSP_request(String request_type) {
		try {
			// Use the RTSP_BUFFERED_WRITER to write to the RTSP socket
			// write the request line:
			RTSPBufferedWriter.write(request_type + " " + VideoFileName + " " + "RTSP/1.0" + CRLF);

			// write the CSeq line:
			RTSPBufferedWriter.write("Csequenznumber: " + RTSPSeqNb + CRLF);

			// check if request_type is equal to "SETUP" and in this case write
			// the Transport: line advertising to the server the port used to
			// receive the RTP packets RTP_RCV_PORT
			if (request_type == "SETUP") {
				RTSPBufferedWriter.write("Transport: RTP/UDP; client_port= " + RTP_RCV_PORT + CRLF);
			}
			// otherwise, write the Session line from the RTSP_ID field
			else {
				RTSPBufferedWriter.write("Session: " + RTSPid + CRLF);
			}
			RTSPBufferedWriter.flush();
		} catch (Exception ex) {
			System.out.println("send_RTSP_request Exception caught: " + ex);
			ex.printStackTrace();
			System.exit(0);
		}
	}
}