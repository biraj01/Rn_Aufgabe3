package filecopy;

/* FileCopyClient.java
 Version 0.1!!
 Praktikum 3 Rechnernetze BAI4 HAW Hamburg
 Autoren:Biraj
 */
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.LinkedList;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class FileCopyClient extends Thread {

  // -------- Constants
  public final static boolean TEST_OUTPUT_MODE = true;

  public final int SERVER_PORT = 23000;

  public final int UDP_PACKET_SIZE = 1008;

  // -------- Public parms
  public String servername;

  public String sourcePath;

  public String destPath;

  public int windowSize = 5;

  public long serverErrorRate;

  private InetAddress SERVER_ADDRESS;

  // -------- Variables
  // current default timeout in nanoseconds
  private long timeoutValue = 100000000L;

  private DatagramSocket clientSocket;

  private LinkedList<FCpacket> senderbuff; // Window size

  private FileInputStream input;

  private long expRTT = 100000000L;

  private long jitter = 20;

  private int timeouts; // Number of timeouts

  private int nextSeqNum; // Sequence Number of next packet to be send

  private Lock bufferMutex;

  private final Condition notEmpty;

  private final Condition notFull;

  private boolean sending = true;

  // Constructor
  public FileCopyClient(String serverArg, String sourcePathArg, String destPathArg, String windowSizeArg,
      String errorRateArg) {
    servername = serverArg;
    sourcePath = sourcePathArg;
    destPath = destPathArg;
    senderbuff = new LinkedList<>();
    windowSize = Integer.parseInt(windowSizeArg);
    serverErrorRate = Long.parseLong(errorRateArg);
    bufferMutex = new ReentrantLock();
    notEmpty = bufferMutex.newCondition();
    notFull = bufferMutex.newCondition();

  }

  public void init() {

    try {
      SERVER_ADDRESS = InetAddress.getByName(servername);
    } catch (UnknownHostException e1) {
      e1.printStackTrace();
    }

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
    init();
    connect();
    FCpacket firstpacket = makeControlPacket();
    insertintoBuffer(firstpacket);
    new RecieveThread().start();
    sendPacket(firstpacket);
    startTimer(firstpacket);
    new RecieveThread().start();
    nextSeqNum++;
    // read next packet
    FCpacket nextpacket = makeNextPacket(nextSeqNum);
    while (nextpacket != null) {
      insertintoBuffer(nextpacket);
      sendPacket(nextpacket);
      // start the timer for the packet
      startTimer(nextpacket);
      // increase the sequenz Nr
      nextSeqNum++;
      nextpacket = makeNextPacket(nextSeqNum);
    }
    sending = false;
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
    byte[] result = packet.getSeqNumBytesAndData();
    DatagramPacket sendPacket = new DatagramPacket(result, result.length, SERVER_ADDRESS, SERVER_PORT);
    try {
      clientSocket.send(sendPacket);
      packet.setTimestamp(System.nanoTime());
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
    //System.out.println("Time out for packet :" + packet.getSeqNum() + " is " + timeoutValue);;
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
    String output = "Ack für Packet: " + seqNum + " not recieved.";
    testOut("Get: " + output);
    timeouts++;
    // Send the data of the given sequencenr once again
    // Get packet for sequence Nr seqNum.
    FCpacket packet = getFromBuffer(seqNum);
    if (packet != null) {
      sendPacket(packet);
    }
    packet.setTimestamp(System.nanoTime());
    startTimer(packet);

  }

  /**
   *
   * Computes the current timeout value (in nanoseconds)
   */
  public void computeTimeoutValue(long sampleRTT) {
    double x = 0.25;
    double y = x / 2;
    expRTT = (long)((1 - y) * expRTT + y * sampleRTT);
    jitter = (long)((1 - x) * jitter + x * Math.abs(sampleRTT - expRTT));
    timeoutValue =  (expRTT + 4 * jitter);
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

  public void insertintoBuffer(FCpacket packet) {
    bufferMutex.lock();
    while (senderbuff.size() >= windowSize) {
      try {
        notFull.await();
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    }
    if (!senderbuff.contains(packet)) { // no duplicates!
      senderbuff.add(packet);
      // sort in ascending order using the seq num
      Collections.sort(senderbuff);
      notEmpty.signal();
    }
    bufferMutex.unlock();
  }

  private void cleanBuffer() {
    bufferMutex.lock();
    {
      while (!senderbuff.isEmpty() && senderbuff.getFirst().isValidACK()) {
        senderbuff.removeFirst();
        notFull.signal();
      }
    }
    bufferMutex.unlock();
  }

  private FCpacket getFromBuffer(long seqNo) {
    FCpacket retval = null;
    bufferMutex.lock();
    int i = 0;
    for (i = 0; i < senderbuff.size(); i++) {
      if (senderbuff.get(i).getSeqNum() == seqNo) {
        break;
      }
    }
    try {
      testOut("Get: " + i);
      retval = senderbuff.get(i);
    } catch (IndexOutOfBoundsException e) {
    }
    bufferMutex.unlock();
    return retval;
  }


  private class RecieveThread extends Thread {

    public void run() {
      receiveAck();
    }

    private void receiveAck() {
      try {
        while (sending || !senderbuff.isEmpty()) {
          byte[] receiveData = new byte[8];
          DatagramPacket packet = new DatagramPacket(receiveData, 8);
          clientSocket.receive(packet);
          long ackNumber = makeLong(packet.getData(), 0, 8);
          FCpacket acknowlagedPacket = getFromBuffer(ackNumber);
          if (acknowlagedPacket != null) {
            cancelTimer(acknowlagedPacket);
            long duration = System.nanoTime() - acknowlagedPacket.getTimestamp();
            computeTimeoutValue(duration);
            acknowlagedPacket.setValidACK(true);
            System.out.println("recieved ack " + acknowlagedPacket.getSeqNum());
          }
          cleanBuffer();
        }

      } catch (IOException e) {
        e.printStackTrace();
      }
    }

    // Methode von FCpacket to change byte into long
    private long makeLong(byte[] buf, int i, int length) {
      long r = 0;
      length += i;

      for (int j = i; j < length; j++)
        r = (r << 8) | (buf[j] & 0xffL);

      return r;
    }

  }

}