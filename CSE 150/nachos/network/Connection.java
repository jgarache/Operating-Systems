package nachos.network;

import nachos.machine.MalformedPacketException;
import nachos.machine.OpenFile;

import java.util.Arrays;

public class Connection extends OpenFile {
  public static final int stateClosed = 0;
  public static final int stateSynSent = 1;
  public static final int stateSynReceived = 2;
  public static final int stateEstablished = 3;
  public static final int stateStpReceived = 4;
  public static final int stateStpSent = 5;
  public static final int stateClosing = 6;

  public int srcLink;
  public int srcPort;
  public int dstLink;
  public int dstPort;
  public int state = stateClosed;
  public int curSeqNum = 0;
  public int sendSeqNum = 0;

  public Connection(int srcLink, int srcPort, int dstLink, int dstPort) {
    super(null, "Connection");
    this.srcLink = srcLink;
    this.srcPort = srcPort;
    this.dstLink = dstLink;
    this.dstPort = dstPort;
  }

  public int getState() {
    return state;
  }

  public void setState(int state) {
    this.state = state;
  }

  public int read(byte[] buffer, int offset, int length) {
    MailMessage message = NetKernel.postOffice.receive(srcPort);
    if (message == null) {
      return 0;
    }
    curSeqNum++;
    int numBytesRead = Math.min(length, message.contents.length);
    System.arraycopy(message.contents, 0, buffer, offset, numBytesRead);

    return numBytesRead;
  }

  public int write(byte[] buffer, int offset, int length) {
    int numBytesToWrite = Math.min(offset + length, buffer.length);
    byte[] contents = Arrays.copyOfRange(buffer, offset, numBytesToWrite);
    try {
      MailMessage message =
          new MailMessage(dstLink, dstPort, srcLink, srcPort, ++sendSeqNum, contents);
      NetKernel.postOffice.send(message);
      return numBytesToWrite;
    } catch (MalformedPacketException e) {
      return -1;
    }
  }
}
