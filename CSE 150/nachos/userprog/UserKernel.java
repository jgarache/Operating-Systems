package nachos.userprog;

import nachos.machine.Coff;
import nachos.machine.Lib;
import nachos.machine.Machine;
import nachos.machine.Processor;
import nachos.threads.KThread;
import nachos.threads.ThreadedKernel;

import java.util.HashMap;
import java.util.LinkedList;

/** A kernel that can support multiple user processes. */
public class UserKernel extends ThreadedKernel {
  // map to keep track of user processes
  public static HashMap<Integer, UserProcess> UPMap = new HashMap<Integer, UserProcess>();
  /** Globally accessible reference to the synchronized console. */
  public static SynchConsole console;
  // global linked list of free physical pages
  private static LinkedList<Integer> freePhysPages = new LinkedList<Integer>();
  // use to uniquely identify processes
  private static int nextProcessID = 0;
  private static int ROOT = 1;
  // dummy variables to make javac smarter
  private static Coff dummy1 = null;

  /** Allocate a new user kernel. */
  public UserKernel() {
    super();
  }

  /**
   * Returns the current process.
   *
   * @return the current process, or <tt>null</tt> if no process is current.
   */
  public static UserProcess currentProcess() {
    if (!(KThread.currentThread() instanceof UThread)) return null;

    return ((UThread) KThread.currentThread()).process;
  }

  // get a free page from the list of available pages
  public static int getPage() {
    boolean status = Machine.interrupt().disable();
    int page = -1;
    if (freePhysPages.getFirst() != null) page = freePhysPages.pop();
    Machine.interrupt().restore(status);
    return page;
  }

  // add a page back to the list
  public static void addPage(int toAdd) {
    boolean status = Machine.interrupt().disable();
    freePhysPages.push(toAdd);
    Machine.interrupt().restore(status);
  }

  public static int getID() {
    Machine.interrupt().disable();
    nextProcessID++;
    Machine.interrupt().enable();
    return nextProcessID;
  }

  // get a user process based on its unique ID
  public static UserProcess getProcess(int id) {
    return UPMap.get(id);
  }

  public static void addProcess(int id, UserProcess up) {
    Machine.interrupt().disable();
    UPMap.put(id, up);
    Machine.interrupt().enable();
  }

  public static UserProcess removeProcess(int id) {
    Machine.interrupt().disable();
    UserProcess deleted;
    deleted = UPMap.remove(id);
    Machine.interrupt().enable();
    return deleted;
  }

  public static int getROOT() {
    return ROOT;
  }

  /**
   * Initialize this kernel. Creates a synchronized console and sets the processor's exception
   * handler.
   */
  public void initialize(String[] args) {
    super.initialize(args);

    console = new SynchConsole(Machine.console());

    Machine.processor()
        .setExceptionHandler(
            new Runnable() {
              public void run() {
                exceptionHandler();
              }
            });

    // initializing free physical pages inside of global linked list
    for (int i = 0; i < Machine.processor().getNumPhysPages(); i++) freePhysPages.add(i);
  }

  /** Test the console device. */
  public void selfTest() {
    super.selfTest();

    System.out.println("Testing the console device. Typed characters");
    System.out.println("will be echoed until q is typed.");

    char c;

    do {
      c = (char) console.readByte(true);
      console.writeByte(c);
    } while (c != 'q');

    System.out.println("");
  }

  /**
   * The exception handler. This handler is called by the processor whenever a user instruction
   * causes a processor exception.
   *
   * <p>
   *
   * <p>When the exception handler is invoked, interrupts are enabled, and the processor's cause
   * register contains an integer identifying the cause of the exception (see the
   * <tt>exceptionZZZ</tt> constants in the <tt>Processor</tt> class). If the exception involves a
   * bad virtual address (e.g. page fault, TLB miss, read-only, bus error, or address error), the
   * processor's BadVAddr register identifies the virtual address that caused the exception.
   */
  public void exceptionHandler() {
    Lib.assertTrue(KThread.currentThread() instanceof UThread);

    UserProcess process = ((UThread) KThread.currentThread()).process;
    int cause = Machine.processor().readRegister(Processor.regCause);
    process.handleException(cause);
  }

  /**
   * Start running user programs, by creating a process and running a shell program in it. The name
   * of the shell program it must run is returned by <tt>Machine.getShellProgramName()</tt>.
   *
   * @see nachos.machine.Machine#getShellProgramName
   */
  public void run() {
    super.run();

    UserProcess process = UserProcess.newUserProcess();

    String shellProgram = Machine.getShellProgramName();
    Lib.assertTrue(process.execute(shellProgram, new String[] {}));

    KThread.currentThread().finish();
  }

  /** Terminate this kernel. Never returns. */
  public void terminate() {
    super.terminate();
  }
}
