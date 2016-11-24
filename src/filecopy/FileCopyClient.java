package filecopy;

/* FileCopyClient.java
 Version 0.1 - Muss ergaenzt werden!!
 Praktikum 3 Rechnernetze BAI4 HAW Hamburg
 Autoren:
 */

import java.io.*;
import java.net.*;
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

  public int windowSize;

  public int maxWindowSize = 128;

  public long serverErrorRate;

  // -------- Variables
  // current default timeout in nanoseconds
  private long timeoutValue = 100000000L;

  private DatagramSocket clientSocket;

  private FC_Timer timeoutTimer;

  private LinkedList<FCpacket> senderbuff; //Window size 
  
  private FileInputStream input;
  
  private long sendbase;

  private double expRTT;

  private double jitter;

  private DatagramSocket client;
  
  private int seq;

  private int timeouts;   //Number of timeouts 
  
  private int nextSeqNum; //Sequence Number of next packet to be send

  // ... ToDo

  // Constructor
  public FileCopyClient(String serverArg, String sourcePathArg, String destPathArg, String windowSizeArg,
      String errorRateArg) {
    servername = serverArg;
    sourcePath = sourcePathArg;
    destPath = destPathArg;
    windowSize = Integer.parseInt(windowSizeArg);
    serverErrorRate = Long.parseLong(errorRateArg);

    connect();
  }

  private void connect() {
    try {
      client = new DatagramSocket(FileCopyServer.SERVER_PORT, InetAddress.getByName(servername));
    } catch (SocketException e) {
      e.printStackTrace();
    } catch (UnknownHostException e) {
      e.printStackTrace();
    }
  }

  public void runFileCopyClient() {

    while (true) {
      
      //read next packet
      // put in buffer if the buffer is not full
      //send packet with seq nr nextSeqNr
      FC_Timer timer  = new FC_Timer(timeoutValue, null, nextSeqNum);
      timer.start();
      //increase the timer
      nextSeqNum++;
      //If timeout
      timeoutTask(nextSeqNum -- );
      //update the next timeout value
      computeTimeoutValue(timeoutValue);
      timer.start();
      //recieve Ack n  and put n in sendbuffer
       // * mark n as quitted
      // * timer for n stop
      timer.interrupt();
      //compute new roundtriptime
      //if n == sendbase delete all packet until a not quitted packet in sendbuffer is
      //set bendbase = seqnr of that packet
      for(int i = 0; i< senderbuff.size(); i++){
        if(senderbuff.get(i).isValidACK()){
          senderbuff.remove(i);
        }else{
          sendbase =  senderbuff.get(i).getSeqNum();
        }
      }
      
      //Todo empfang von quittungen als eigene Thread
      
//      if(seq >= this.windowSize || seq >= 100202){
//        //recieveAck
//      }else{
//        ++seq;
//      }
//      if (windowSize == 0) {
//        windowSize = Math.min(senderbuff.size(), maxWindowSize);
//      }
//      for (int i = 0; i < windowSize; i++) {
//        FCpacket packet = senderbuff.get(0);
//      }
    }
    // ToDo!!

  }
  
  private void recieveAck(){
    
    
  }
  
  private class recieveThread extends Thread{
    
    public void run(){
      
      
    }
  }
  
  private void insertPacketinBuffer(){
    
    
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

}