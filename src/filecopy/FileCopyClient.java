package filecopy;

/* FileCopyClient.java
 Version 0.1 - Muss ergaenzt werden!!
 Praktikum 3 Rechnernetze BAI4 HAW Hamburg
 Autoren:
 */

import java.io.*;
import java.net.*;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;

public class FileCopyClient extends Thread {

  // -------- Constants
  public final static boolean TEST_OUTPUT_MODE = false;

  public final int SERVER_PORT = 23000;

  public final int UDP_PACKET_SIZE = 1008;

  // -------- Public parms
  public String servername;

  public String sourcePath;

  public String destPath;

  public int windowSize = 10;

  public int maxWindowSize = 128;

  public long serverErrorRate;

  // -------- Variables
  // current default timeout in nanoseconds
  private long timeoutValue = 100000000L;

  private DatagramSocket clientSocket;

  private FC_Timer timeoutTimer;

  private LinkedList<FCpacket> senderbuff; // Window size

  private FileInputStream input;

  private long sendbase;

  private double expRTT;

  private double jitter;

  private int seq;

  private int timeouts; // Number of timeouts

  private int nextSeqNum; // Sequence Number of next packet to be send

  // ... ToDo

  // Constructor
  public FileCopyClient(String serverArg, String sourcePathArg, String destPathArg, String windowSizeArg,
      String errorRateArg) {
    servername = serverArg;
    System.out.println(servername);
    sourcePath = sourcePathArg;
    destPath = destPathArg;
    senderbuff = new LinkedList<>();
    System.out.println(sourcePath);
    System.out.println(destPath);
    windowSize = Integer.parseInt(windowSizeArg);
    serverErrorRate = Long.parseLong(errorRateArg);
    try {
      input = new FileInputStream(sourcePath);
    } catch (FileNotFoundException e) {
      e.printStackTrace();
    }

  }

  private void connect() {
    try {
      clientSocket = new DatagramSocket();
      System.out.println("Connection Success");
    } catch (SocketException e) {
      e.printStackTrace();
    }
  }

  public void runFileCopyClient() {
    connect();
    FCpacket firstpacket = makeControlPacket();
    sendPacket(firstpacket);

    // read next packet
    FCpacket nextpacket = makeNextPacket(1);
    System.out.println(nextpacket);
    while (nextpacket != null) {
        // put in buffer if the buffer is not full
       new RecieveThread().insert(nextpacket);
      // send packet with seq nr nextSeqNr
      sendPacket(nextpacket);
      // start the timer for the packet
      startTimer(nextpacket);
      // increase the sequenz Nr
      nextSeqNum++;
      nextpacket = makeNextPacket(nextSeqNum);
    }
  }

  /*
   * Read a file up to UDP_PACKET_SIZE and make a packet from it
   * 
   * @return packet FCpacket
   */
  private FCpacket makeNextPacket(long seqNr) {
    byte[] data = new byte[UDP_PACKET_SIZE - 8];
    int packetLen = 0;
    try {
      packetLen = input.read(data);
    } catch (IOException e) {
      e.printStackTrace();
    }
    FCpacket packet = null;
    if (packetLen != -1) {
      packet = new FCpacket(seqNr, data, packetLen);
    }
    return packet;
  }

  private void sendPacket(FCpacket packet) {
    // Timer Start
    packet.setTimestamp(System.currentTimeMillis());
    FC_Timer timer = new FC_Timer(this.timeoutValue, this, packet.getSeqNum());
    packet.setTimer(timer);
    // sendpacket
    InetAddress serverAdress = null;
    try {
      serverAdress = InetAddress.getByName("localhost");
    } catch (UnknownHostException e) {
      e.printStackTrace();
    }
    byte[] result = packet.getSeqNumBytesAndData();
    DatagramPacket sendPacket = new DatagramPacket(result, result.length, serverAdress, SERVER_PORT);

    /* Senden des Pakets */
    try {
      // sent the packet
      clientSocket.send(sendPacket);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  /**
   *
   * Timer Operations
   */
  public void startTimer(FCpacket packet) {
    /* Create, save and start timer for the given FCpacket */
    FC_Timer timer = new FC_Timer(timeoutValue, this, packet.getSeqNum());
    packet.setTimer(timer);
    timer.start();
  }

  public void cancelTimer(FCpacket packet) {
    /* Cancel timer for the given FCpacket */
    testOut("Cancel Timer for packet" + packet.getSeqNum());

    if (packet.getTimer() != null) {
      packet.getTimer().interrupt();
    }
  }

  /**
   * Implementation specific task performed at timeout
   */
  public void timeoutTask(long seqNum) {
    // Count timeouts
    timeouts++;
    // Send the data of the given sequencenr once again
    // Get packet for sequence Nr seqNum.
    FCpacket packet = null;
    if (packet != null) {
      sendPacket(packet);
    }
    startTimer(packet);

  }

  /**
   *
   * Computes the current timeout value (in nanoseconds)
   */
  public void computeTimeoutValue(long sampleRTT) {
    double x = 0.25;
    double y = x / 2;
    expRTT = (1 - y) * expRTT + y * sampleRTT;
    jitter = (1 - x) * jitter + x * Math.abs(sampleRTT - expRTT);
    timeoutValue = Double.doubleToLongBits(expRTT + 4 * jitter);
  }

  /**
   *
   * Return value: FCPacket with (0 destPath;windowSize;errorRate)
   */
  public FCpacket makeControlPacket() {
    /*
     * Create first packet with seq num 0. Return value: FCPacket with (0
     * destPath ; windowSize ; errorRate)
     */
    String sendString = destPath + ";" + windowSize + ";" + serverErrorRate;
    byte[] sendData = null;
    try {
      sendData = sendString.getBytes("UTF-8");
    } catch (UnsupportedEncodingException e) {
      e.printStackTrace();
    }
    return new FCpacket(0, sendData, sendData.length);
  }

  public void testOut(String out) {
    if (TEST_OUTPUT_MODE) {
      System.err.printf("%,d %s: %s\n", System.nanoTime(), Thread.currentThread().getName(), out);
    }
  }

  public static void main(String argv[]) throws Exception {
    FileCopyClient myClient = new FileCopyClient(argv[0], argv[1], argv[2], argv[3], argv[4]);
    myClient.runFileCopyClient();
  }

  private class RecieveThread extends Thread {
    DatagramPacket datagramPacket;

    public RecieveThread(DatagramPacket packet) {
      this.datagramPacket = packet;

    }

    public void run() {
      try {
        clientSocket.receive(datagramPacket);
        byte[] data = datagramPacket.getData();
        int length = datagramPacket.getLength();
        FCpacket recievedPaket = new FCpacket(data, length);
        long n = recievedPaket.getSeqNum();
        cancelTimer(recievedPaket);
        remove(n);
      } catch (IOException e) {
        e.printStackTrace();
      }
    }

    private void remove(long n) {
      if (n == sendbase) {
        for (int i = 0; i < senderbuff.size(); i++)
          if (senderbuff.get(i).isValidACK()) {
            senderbuff.remove(i);
          }else {
             sendbase = senderbuff.get(i).getSeqNum();
             }
      }
    }

    public void insert(FCpacket packet) {
      if (senderbuff.size() == windowSize) {
        // wait
      }
      senderbuff.add(packet);
    }

  }

}