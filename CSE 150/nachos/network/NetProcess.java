package nachos.network;

import nachos.machine.Machine;
import nachos.machine.MalformedPacketException;
import nachos.userprog.UserProcess;

/** A <tt>VMProcess</tt> that supports networking syscalls. */
public class NetProcess extends UserProcess {
  private static final int syscallConnect = 11, syscallAccept = 12;

  /** Allocate a new process. */
  public NetProcess() {
    super();
  }

  /**
   * Handle a syscall exception. Called by <tt>handleException()</tt>. The <i>syscall</i> argument
   * identifies which syscall the user executed:
   *
   * <p>
   *
   * <table>
   * <tr><td>syscall#</td><td>syscall prototype</td></tr>
   * <tr><td>11</td><td><tt>int  connect(int host, int port);</tt></td></tr>
   * <tr><td>12</td><td><tt>int  accept(int port);</tt></td></tr>
   * </table>
   *
   * @param syscall the syscall number.
   * @param a0 the first syscall argument.
   * @param a1 the second syscall argument.
   * @param a2 the third syscall argument.
   * @param a3 the fourth syscall argument.
   * @return the value to be returned to the user.
   */
  public int handleSyscall(int syscall, int a0, int a1, int a2, int a3) {
    switch (syscall) {
      case syscallConnect:
        return handleConnect(a0, a1);
      case syscallAccept:
        return handleAccept(a0);
      default:
        return super.handleSyscall(syscall, a0, a1, a2, a3);
    }
  }

  private int handleConnect(int host, int port) {
    int link = Machine.networkLink().getLinkAddress();
    int srcPort = NetKernel.postOffice.findPort();

    Connection connection = new Connection(host, port, link, srcPort);

    int descriptor;
    for (descriptor = 2; descriptor < falloc.length; ++descriptor) {
      if (falloc[descriptor].getFile() == null) {
        falloc[descriptor].setFile(connection);
      }
    }

    try {
      MailMessage message = new MailMessage(host, port, link, srcPort, 0, new byte[0]);
      NetKernel.postOffice.send(message);
      ((Connection)falloc[descriptor].getFile()).setState(Connection.stateSynSent);
    } catch (MalformedPacketException e) {
      return -1;
    }

    MailMessage ack = NetKernel.postOffice.receive(srcPort);

    return descriptor;
  }

  private int handleAccept(int port) {
    MailMessage message = NetKernel.postOffice.receive(port);
    if (message == null) {
      return -1;
    }

    int dstLink = message.packet.srcLink;
    int srcLink = Machine.networkLink().getLinkAddress();
    int dstPort = message.srcPort;
    Connection connection = new Connection(dstLink, dstPort, srcLink, port);
    NetKernel.postOffice.portUsed(port);
    int descriptor;
    for (descriptor = 2; descriptor < falloc.length; ++descriptor) {
      if (falloc[descriptor].getFile() == null) {
        falloc[descriptor].setFile(connection);
      }
    }
    try {
      MailMessage ack = new MailMessage(dstLink, dstPort, srcLink, port, 0, new byte[0]);
      NetKernel.postOffice.send(ack);
    } catch (MalformedPacketException e) {
      return -1;
    }
    return descriptor;
  }
}