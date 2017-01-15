/*
 Server
 usage: java Server [RTSP listening port]
*/

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.*;
import java.net.*;
import java.util.StringTokenizer;
import java.util.concurrent.ThreadLocalRandom;

public class Server extends JFrame implements ActionListener, Interface {

	private static final long serialVersionUID = 9827516272432497L;
	// RTP variables:
	// ----------------
	DatagramSocket RTPsocket; // socket to be used to send and receive UDP
								// packets
	DatagramPacket senddp; // UDP packet containing the video frames
	InetAddress ClientIPAddr; // Client IP address
	int RTP_dest_port = 0; // destination port for RTP packets (given by the RTSP
							// Client)

	// GUI:
	// ----------------
	JPanel sliderPanel = new JPanel();
	JSlider lossSlider = new JSlider(LOSS_MIN, LOSS_MAX, LOSS_INIT);
	JSlider xorSlider = new JSlider(XOR_MIN, XOR_MAX, XOR_INIT);
	JLabel label;
	JLabel serverConfig;

	// Slidervariables
	static final int XOR_MIN = 2;
	static final int XOR_MAX = 20;
	static final int XOR_INIT = 2;

	static final int LOSS_MIN = 0;
	static final int LOSS_MAX = 100;
	static final int LOSS_INIT = 0;

	// FEC Buffer:
	FECpacket fecBuffer = new FECpacket(this);

	// Package Lost
	private static int LOSS_RATE = 0;

	// XOR
	static int GROUP_SIZE = 2;
	int groupSize = 0;

	// Video variables:
	// ----------------
	int imgNumber = 0; // image nb of the image currently transmitted
	VideoStream video; // VideoStream object used to access video frames
	static int MJPEG_TYPE = 26; // RTP payload type for MJPEG video
	static int FRAME_PERIOD = 40; // Frame period of the video to stream, in ms
	static int VIDEO_LENGTH = 500; // length of the video in frames

	Timer timer; // timer used to send the images at the video frame rate
	byte[] curImage; // buffer used to store the images to send to the client

	// RTSP variables
	// --------------
	// Rtsp states
	final static int INIT = 0;
	final static int READY = 1;
	final static int PLAYING = 2;
	// rtsp message types
	final static int SETUP = 3;
	final static int PLAY = 4;
	final static int PAUSE = 5;
	final static int TEARDOWN = 6;
	final static int OPTIONS = 7;

	static int STATE; // RTSP Server STATE == INIT or READY or PLAY
	Socket RTSPsocket; // socket used to send/receive RTSP messages

	// Input and output stream filters
	// --------------------------------
	static BufferedReader RTSPBufferedReader;
	static BufferedWriter RTSPBufferedWriter;
	static String VideoFileName; // video file requested from the client
	static int RTSP_ID = 123456; // ID of the RTSP session
	int RTSPSeqNb = 0; // Sequence number of RTSP messages within the session

	final static String CRLF = "\r\n";

	// --------------------------------
	// Constructor
	// --------------------------------

	public Server() {
		// init Frame
		super("Server");
		setSize(300, 170);

		// init Timer
		timer = new Timer(FRAME_PERIOD, this);
		timer.setInitialDelay(0);
		timer.setCoalesce(true);

		// allocate memory for the sending buffer
		curImage = new byte[15000];

		// Handler to close the main window
		addWindowListener(new WindowAdapter() {
			public void windowClosing(WindowEvent e) {
				// stop the timer and exit
				timer.stop();
				System.exit(0);
			}
		});

		// GUI:
		label = new JLabel("Send frame #", JLabel.CENTER);
		getContentPane().add(label, BorderLayout.CENTER);

		lossSlider.setMajorTickSpacing(20);
		lossSlider.setMinorTickSpacing(10);
		lossSlider.setPaintTicks(true);
		lossSlider.setPaintLabels(true);
		lossSlider.addChangeListener(new SliderListener());

		xorSlider.setMajorTickSpacing(3);
		xorSlider.setMinorTickSpacing(1);
		xorSlider.setPaintTicks(true);
		xorSlider.setPaintLabels(true);
		xorSlider.addChangeListener(new SliderListener());

		serverConfig = new JLabel("loss rate: [" + LOSS_RATE + "]     XOR group size: [" + GROUP_SIZE + "]");

		sliderPanel.setLayout(new BorderLayout());
		sliderPanel.add(serverConfig, BorderLayout.SOUTH);
		sliderPanel.add(lossSlider, BorderLayout.NORTH);
		sliderPanel.add(xorSlider, BorderLayout.CENTER);
		getContentPane().add(sliderPanel, BorderLayout.SOUTH);
		getContentPane().setPreferredSize(new Dimension(300, 150));
	}

	// ------------------------------------
	// main
	// ------------------------------------
	public static void main(String argv[]) throws Exception {
		// create a Server object
		Server theServer = new Server();

		// show GUI:

		// theServer.pack();
		theServer.setVisible(true);

		// get RTSP socket port from the command line
		int RTSPport = Integer.parseInt(argv[0]);

		// Initiate TCP connection with the client for the RTSP session
		ServerSocket listenSocket = new ServerSocket(RTSPport);
		theServer.RTSPsocket = listenSocket.accept();
		listenSocket.close();

		// Get Client IP address
		theServer.ClientIPAddr = theServer.RTSPsocket.getInetAddress();

		// Initiate RTSPstate
		STATE = INIT;

		// Set input and output stream filters:
		RTSPBufferedReader = new BufferedReader(new InputStreamReader(theServer.RTSPsocket.getInputStream()));
		RTSPBufferedWriter = new BufferedWriter(new OutputStreamWriter(theServer.RTSPsocket.getOutputStream()));

		// Wait for the SETUP message from the client
		int request_type;
		boolean done = false;

		while (!done) {
			request_type = theServer.parse_RTSP_request();
			if (request_type == SETUP) {
				done = true;
				// update RTSP STATE
				STATE = READY;
				System.out.println("New RTSP STATE: READY");
				// Send response
				theServer.send_RTSP_response("");
				// init the VideoStream object:
				theServer.video = new VideoStream(VideoFileName);
				// init RTP socket
				theServer.RTPsocket = new DatagramSocket();
				theServer.RTPsocket.setSoTimeout(5);
			}
		}

		// loop to handle RTSP requests
		while (true) {
			// parse the request
			request_type = theServer.parse_RTSP_request();
			if ((request_type == PLAY) && (STATE == READY)) {
				// send back response
				theServer.send_RTSP_response("");
				// start timer
				theServer.timer.start();
				// update STATE
				STATE = PLAYING;
				System.out.println("New RTSP STATE: PLAYING");
			} else if ((request_type == PAUSE) && (STATE == PLAYING)) {
				// send back response
				theServer.send_RTSP_response("");
				// stop timer
				theServer.timer.stop();
				// update STATE
				STATE = READY;
				System.out.println("New RTSP STATE: READY");
			} else if (request_type == TEARDOWN) {
				// send back response
				theServer.send_RTSP_response("");
				// stop timer
				theServer.timer.stop();
				// close sockets
				theServer.RTSPsocket.close();
				theServer.RTPsocket.close();
				System.exit(0);
			} else if (request_type == OPTIONS) {
				theServer.send_RTSP_response("OPTIONS");
			} 
		}
	}

	// ------------------------
	// Handler for timer
	// ------------------------
	public void actionPerformed(ActionEvent e) {
		// if the current image nb is less than the length of the video
		if (imgNumber < VIDEO_LENGTH) {

			// update current imageNumb
			imgNumber++;

			try {
				// get next frame to send from the video, as well as its size
				int image_length = video.getnextframe(curImage);

				// Builds an RTPpacket object containing the frame
				RTPpacket rtp_packet = new RTPpacket(MJPEG_TYPE, imgNumber, imgNumber * FRAME_PERIOD, curImage,
						image_length);

				// get to total length of the full rtp packet to send
				int packet_length = rtp_packet.getlength();

				// retrieve the packet bitstream and store it in an array of
				// bytes
				byte[] packet_bits = new byte[packet_length];
				rtp_packet.getpayloadSize(packet_bits);
				
				// send the packet as a DatagramPacket over the UDP socket
				senddp = new DatagramPacket(packet_bits, packet_length, ClientIPAddr, RTP_dest_port);

				// Package loss
				if (ThreadLocalRandom.current().nextInt(LOSS_MIN, LOSS_MAX + 1) > LOSS_RATE) {
					fecBuffer.setdata(GROUP_SIZE, rtp_packet);
					RTPsocket.send(senddp);
					System.out.println("Send Frame: " + imgNumber);
				} else {
					// change RTP Header Payload Typte and store Packet in fecBuffer
					RTPpacket.getHeader(127, rtp_packet.getsequencenumber(),rtp_packet.gettimestamp());					
					System.out.println("NEW: " + rtp_packet.getpayloadtype());
					fecBuffer.setdata(GROUP_SIZE, rtp_packet);
					System.out.println("Lost Frame: " + imgNumber);
				}
				// Package loss end				

				// print the header bitstream
				rtp_packet.printheader();

				// update GUI
				label.setText("Send frame: " + imgNumber);
			} catch (Exception ex) {
				System.out.println("Exception caught: " + ex);
				ex.printStackTrace();
				System.exit(0);
			}
		} else {
			// if we have reached the end of the video file, stop the timer
			timer.stop();
		}
	}

	// ------------------------------------
	// Parse RTSP Request
	// ------------------------------------
	private int parse_RTSP_request() {

		int request_type = -1;
		try {
			// parse request line and extract the request_type:
			String RequestLine = RTSPBufferedReader.readLine();
			// System.out.println("RTSP Server - Received from Client:");
			System.out.println(RequestLine);

			StringTokenizer tokens = new StringTokenizer(RequestLine);
			String request_type_string = tokens.nextToken();

			// convert to request_type structure:
			if ((new String(request_type_string)).compareTo("SETUP") == 0)
				request_type = SETUP;
			else if ((new String(request_type_string)).compareTo("PLAY") == 0)
				request_type = PLAY;
			else if ((new String(request_type_string)).compareTo("PAUSE") == 0)
				request_type = PAUSE;
			else if ((new String(request_type_string)).compareTo("TEARDOWN") == 0)
				request_type = TEARDOWN;
			else if ((new String(request_type_string)).compareTo("OPTIONS") == 0)
				request_type = OPTIONS;

			if (request_type == SETUP) {
				// extract VIDEO_FILE_NAME from RequestLine
				VideoFileName = tokens.nextToken();
			}

			// parse the SeqNumLine and extract CSeq field
			String SeqNumLine = RTSPBufferedReader.readLine();
			System.out.println(SeqNumLine);
			tokens = new StringTokenizer(SeqNumLine);
			tokens.nextToken();
			RTSPSeqNb = Integer.parseInt(tokens.nextToken());

			// get LastLine
			String LastLine = RTSPBufferedReader.readLine();
			System.out.println(LastLine);

			if (request_type == SETUP) {
				// extract RTP_dest_port from LastLine
				tokens = new StringTokenizer(LastLine);
				for (int i = 0; i < 3; i++)
					tokens.nextToken(); // skip unused stuff
				RTP_dest_port = Integer.parseInt(tokens.nextToken());
			}
			// else LastLine will be the SessionId line ... do not check for
			// now.
		} catch (Exception ex) {
			System.out.println("Exception caught: " + ex);
			ex.printStackTrace();
			System.exit(0);
		}

		return (request_type);
	}

	// ------------------------------------
	// Send RTSP Response
	// ------------------------------------
	private void send_RTSP_response(String msg) {
		try {
			RTSPBufferedWriter.write("RTSP/1.0 200 OK" + CRLF);
			RTSPBufferedWriter.write("CSeq: " + RTSPSeqNb + CRLF);
			RTSPBufferedWriter.write("Session: " + RTSP_ID + CRLF);

			if (msg == "OPTIONS") {
				RTSPBufferedWriter.write("Public: DESCRIBE, SETUP, TEARDOWN, PLAY, PAUSE" + CRLF); // added
			}

			RTSPBufferedWriter.flush();
			System.out.println("RTSP Server - Sent response to Client.");
		} catch (Exception ex) {
			System.out.println("send_RTSP_response Exception caught: " + ex);
			ex.printStackTrace();
			System.exit(0);
		}
	}

	class SliderListener implements ChangeListener {
		public void stateChanged(ChangeEvent e) {
			JSlider source = (JSlider) e.getSource();
			if (!source.getValueIsAdjusting()) {
				if (source.equals(lossSlider))
					LOSS_RATE = (int) source.getValue();
				if (source.equals(xorSlider))
					GROUP_SIZE = (int) source.getValue();
				serverConfig.setText("loss rate: [" + LOSS_RATE + "]     XOR group size: [" + GROUP_SIZE + "]");
			}
		}
	}
	
	// Send Packet GrupSize
	public void sendPacketByte(byte[] data) throws IOException {
		RTPsocket.send(senddp);
		System.out.println("Send FEC Frame: " + imgNumber);
	}
}
