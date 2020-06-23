package nachos.userprog;

import nachos.machine.*;
import nachos.threads.KThread;
import nachos.threads.ThreadedKernel;

import java.io.EOFException;
import java.util.LinkedList;

/**
 * Encapsulates the state of a user process that is not contained in its user thread (or threads).
 * This includes its address translation state, a file table, and information about the program
 * being executed.
 *
 * <p>
 *
 * <p>This class is extended by other classes to support additional functionality (such as
 * additional syscalls).
 *
 * @see nachos.vm.VMProcess
 * @see nachos.network.NetProcess
 */
public class UserProcess {
  private static final int syscallHalt = 0,
      syscallExit = 1,
      syscallExec = 2,
      syscallJoin = 3,
      syscallCreate = 4,
      syscallOpen = 5,
      syscallRead = 6,
      syscallWrite = 7,
      syscallClose = 8,
      syscallUnlink = 9;
  private static final int pageSize = Processor.pageSize;
  private static final char dbgProcess = 'a';
  /** The number of pages in the program's stack. */
  protected final int stackPages = 8;
  /** The program being run by this process. */
  protected Coff coff;
  /** This process's page table. */
  protected TranslationEntry[] pageTable;
  /** The number of contiguous pages occupied by the program. */
  protected int numPages;
  // Creates an array of files to store files into processor
  protected FileAllocator falloc[] = new FileAllocator[16];
  private int initialPC, initialSP;
  private int argc, argv;
  private int parentID;
  private LinkedList<Integer> childrenID = new LinkedList<Integer>();
  private int myID;
  private UThread thread;
  private int exitStatus;

  /** Allocate a new process. */
  public UserProcess() {

    // Original code below
    // int numPhysPages = Machine.processor().getNumPhysPages();

    /*
    //assumed pages is 9 because its stack + PC + whatever else
    int numPhysPages = 9;

    pageTable = new TranslationEntry[numPhysPages];

    //edited to work with multiple processes
    for (int i = 0; i < numPhysPages; i++) {

        //Mapping virtual address to physical address
        pageTable[i] = new TranslationEntry(i, (int) UserKernel.freePhysPages.get(i), true, false, false, false);

        //removing from linked list to show pages are being used
        UserKernel.freePhysPages.remove(i);
    }
    */

    // initialize my ID

    for (int i = 0; i < 16; i++) falloc[i] = new FileAllocator();

    // STDIN
    falloc[0].file = UserKernel.console.openForReading();
    falloc[0].pos = 0;

    // STDOUT
    falloc[1].file = UserKernel.console.openForWriting();
    falloc[1].pos = 0;

    myID = UserKernel.getID();
    UserKernel.addProcess(myID, this);
  }

  /**
   * Allocate and return a new process of the correct class. The class name is specified by the
   * <tt>nachos.conf</tt> key <tt>Kernel.processClassName</tt>.
   *
   * @return a new process of the correct class.
   */
  public static UserProcess newUserProcess() {
    return (UserProcess) Lib.constructObject(Machine.getProcessClassName());
  }

  /**
   * Execute the specified program with the specified arguments. Attempts to load the program, and
   * then forks a thread to run it.
   *
   * @param name the name of the file containing the executable.
   * @param args the arguments to pass to the executable.
   * @return <tt>true</tt> if the program was successfully executed.
   */
  public boolean execute(String name, String[] args) {
    if (!load(name, args)) return false;

    thread = new UThread(this);
    thread.setName(name).fork();

    return true;
  }

  /**
   * Save the state of this process in preparation for a context switch. Called by
   * <tt>UThread.saveState()</tt>.
   */
  public void saveState() {}

  /**
   * Restore the state of this process after a context switch. Called by
   * <tt>UThread.restoreState()</tt>.
   */
  public void restoreState() {
    Machine.processor().setPageTable(pageTable);
  }

  /**
   * Read a null-terminated string from this process's virtual memory. Read at most <tt>maxLength +
   * 1</tt> bytes from the specified address, search for the null terminator, and convert it to a
   * <tt>java.lang.String</tt>, without including the null terminator. If no null terminator is
   * found, returns <tt>null</tt>.
   *
   * @param vaddr the starting virtual address of the null-terminated string.
   * @param maxLength the maximum number of characters in the string, not including the null
   *     terminator.
   * @return the string read, or <tt>null</tt> if no null terminator was found.
   */
  public String readVirtualMemoryString(int vaddr, int maxLength) {
    Lib.assertTrue(maxLength >= 0);

    byte[] bytes = new byte[maxLength + 1];

    int bytesRead = readVirtualMemory(vaddr, bytes);

    for (int length = 0; length < bytesRead; length++) {
      if (bytes[length] == 0) return new String(bytes, 0, length);
    }

    return null;
  }

  /**
   * Transfer data from this process's virtual memory to all of the specified array. Same as
   * <tt>readVirtualMemory(vaddr, data, 0, data.length)</tt>.
   *
   * @param vaddr the first byte of virtual memory to read.
   * @param data the array where the data will be stored.
   * @return the number of bytes successfully transferred.
   */
  public int readVirtualMemory(int vaddr, byte[] data) {
    return readVirtualMemory(vaddr, data, 0, data.length);
  }

  /**
   * Transfer data from this process's virtual memory to the specified array. This method handles
   * address translation details. This method must <i>not</i> destroy the current process if an
   * error occurs, but instead should return the number of bytes successfully copied (or zero if no
   * data could be copied).
   *
   * @param vaddr the first byte of virtual memory to read.
   * @param data the array where the data will be stored.
   * @param offset the first byte to write in the array.
   * @param length the number of bytes to transfer from virtual memory to the array.
   * @return the number of bytes successfully transferred.
   */
  public int readVirtualMemory(int vaddr, byte[] data, int offset, int length) {
    return accessVirtualMemory(vaddr, data, offset, length, false);
  }

  /**
   * Transfer all data from the specified array to this process's virtual memory. Same as
   * <tt>writeVirtualMemory(vaddr, data, 0, data.length)</tt>.
   *
   * @param vaddr the first byte of virtual memory to write.
   * @param data the array containing the data to transfer.
   * @return the number of bytes successfully transferred.
   */
  public int writeVirtualMemory(int vaddr, byte[] data) {
    return writeVirtualMemory(vaddr, data, 0, data.length);
  }

  /**
   * Transfer data from the specified array to this process's virtual memory. This method handles
   * address translation details. This method must <i>not</i> destroy the current process if an
   * error occurs, but instead should return the number of bytes successfully copied (or zero if no
   * data could be copied).
   *
   * @param vaddr the first byte of virtual memory to write.
   * @param data the array containing the data to transfer.
   * @param offset the first byte to transfer from the array.
   * @param length the number of bytes to transfer from the array to virtual memory.
   * @return the number of bytes successfully transferred.
   */
  public int writeVirtualMemory(int vaddr, byte[] data, int offset, int length) {
    return accessVirtualMemory(vaddr, data, offset, length, true);
  }

  public int accessVirtualMemory(int vaddr, byte[] data, int offset, int length, boolean write) {
    Lib.assertTrue(offset >= 0 && length >= 0 && offset + length <= data.length);

    byte[] memory = Machine.processor().getMemory();

    int transfer = 0;

    while (transfer < data.length && length > 0) {
      int vpn = Processor.pageFromAddress(vaddr);
      if (pageTable == null
          || vpn >= pageTable.length
          || pageTable[vpn] == null
          || !pageTable[vpn].valid) {
        break;
      }
      int pageOffset = Processor.offsetFromAddress(vaddr) % Processor.pageSize;
      int paddr = Processor.makeAddress(pageTable[vpn].ppn, pageOffset);
      int amount = Math.min(data.length - transfer, pageSize - pageOffset);
      if (!write) {
        System.arraycopy(memory, paddr, data, offset, amount);
      } else {
        System.arraycopy(data, offset, memory, paddr, amount);
      }
      if (amount > 0) {
        pageTable[vpn].used = true;
        if (write) pageTable[vpn].dirty = true;
      }
      vaddr += amount;
      offset += amount;
      length -= amount;
      transfer += amount;
    }

    return transfer;
  }

  /**
   * Load the executable with the specified name into this process, and prepare to pass it the
   * specified arguments. Opens the executable, reads its header information, and copies sections
   * and arguments into this process's virtual memory.
   *
   * @param name the name of the file containing the executable.
   * @param args the arguments to pass to the executable.
   * @return <tt>true</tt> if the executable was successfully loaded.
   */
  private boolean load(String name, String[] args) {
    Lib.debug(dbgProcess, "UserProcess.load(\"" + name + "\")");

    OpenFile executable = ThreadedKernel.fileSystem.open(name, false);
    if (executable == null) {
      Lib.debug(dbgProcess, "\topen failed");
      return false;
    }

    try {
      coff = new Coff(executable);
    } catch (EOFException e) {
      executable.close();
      Lib.debug(dbgProcess, "\tcoff load failed");
      return false;
    }

    // make sure the sections are contiguous and start at page 0
    numPages = 0;
    for (int s = 0; s < coff.getNumSections(); s++) {
      CoffSection section = coff.getSection(s);
      if (section.getFirstVPN() != numPages) {
        coff.close();
        Lib.debug(dbgProcess, "\tfragmented executable");
        return false;
      }
      numPages += section.getLength();
    }

    // make sure the argv array will fit in one page
    byte[][] argv = new byte[args.length][];
    int argsSize = 0;
    for (int i = 0; i < args.length; i++) {
      argv[i] = args[i].getBytes();
      // 4 bytes for argv[] pointer; then string plus one for null byte
      argsSize += 4 + argv[i].length + 1;
    }
    if (argsSize > pageSize) {
      coff.close();
      Lib.debug(dbgProcess, "\targuments too long");
      return false;
    }

    // program counter initially points at the program entry point
    initialPC = coff.getEntryPoint();

    // next comes the stack; stack pointer initially points to top of it
    numPages += stackPages;
    initialSP = numPages * pageSize;

    // and finally reserve 1 page for arguments
    numPages++;

    /* Initialize the pageTable based on the executable to run */
    pageTable = new TranslationEntry[numPages];

    for (int i = 0; i < numPages; i++) {

      // Mapping virtual address to physical address
      // removing from linked list to show pages are being used
      pageTable[i] = new TranslationEntry(i, UserKernel.getPage(), true, false, false, false);
    }

    /* End of pageTable initialization */

    if (!loadSections()) return false;

    // store arguments in last page
    int entryOffset = (numPages - 1) * pageSize;
    int stringOffset = entryOffset + args.length * 4;

    this.argc = args.length;
    this.argv = entryOffset;

    for (int i = 0; i < argv.length; i++) {
      byte[] stringOffsetBytes = Lib.bytesFromInt(stringOffset);
      Lib.assertTrue(writeVirtualMemory(entryOffset, stringOffsetBytes) == 4);
      entryOffset += 4;
      Lib.assertTrue(writeVirtualMemory(stringOffset, argv[i]) == argv[i].length);
      stringOffset += argv[i].length;
      Lib.assertTrue(writeVirtualMemory(stringOffset, new byte[] {0}) == 1);
      stringOffset += 1;
    }

    return true;
  }

  /**
   * Allocates memory for this process, and loads the COFF sections into memory. If this returns
   * successfully, the process will definitely be run (this is the last step in process
   * initialization that can fail).
   *
   * @return <tt>true</tt> if the sections were successfully loaded.
   */
  protected boolean loadSections() {
    if (numPages > Machine.processor().getNumPhysPages()) {
      coff.close();
      Lib.debug(dbgProcess, "\tinsufficient physical memory");
      return false;
    }

    // load sections
    for (int s = 0; s < coff.getNumSections(); s++) {
      CoffSection section = coff.getSection(s);

      Lib.debug(
          dbgProcess,
          "\tinitializing " + section.getName() + " section (" + section.getLength() + " pages)");

      for (int i = 0; i < section.getLength(); i++) {
        int vpn = section.getFirstVPN() + i;

        // for now, just assume virtual addresses=physical addresses
        // section.loadPage(i, vpn);

        // use the ppn to load the page
        section.loadPage(i, pageTable[vpn].ppn);

        // match pageTable information with section
        pageTable[vpn].readOnly = section.isReadOnly();
      }
    }

    return true;
  }

  /** Release any resources allocated by <tt>loadSections()</tt>. */
  protected void unloadSections() {
    // re-add entries to list of free physical pages
    for (int i = 0; i < pageTable.length; i++) {
      UserKernel.addPage(pageTable[i].ppn);
      // released, so the pages are no longer valid
      pageTable[i].valid = false;
    }
    // delete pageTable
    pageTable = null;
  }

  /**
   * Initialize the processor's registers in preparation for running the program loaded into this
   * process. Set the PC register to point at the start function, set the stack pointer register to
   * point at the top of the stack, set the A0 and A1 registers to argc and argv, respectively, and
   * initialize all other registers to 0.
   */
  public void initRegisters() {
    Processor processor = Machine.processor();

    // by default, everything's 0
    for (int i = 0; i < processor.numUserRegisters; i++) processor.writeRegister(i, 0);

    // initialize PC and SP according
    processor.writeRegister(Processor.regPC, initialPC);
    processor.writeRegister(Processor.regSP, initialSP);

    // initialize the first two argument registers to argc and argv
    processor.writeRegister(Processor.regA0, argc);
    processor.writeRegister(Processor.regA1, argv);
  }

  /** Handle the halt() system call. */
  private int handleHalt() {

    Machine.halt();

    Lib.assertNotReached("Machine.halt() did not halt machine!");
    return 0;
  }

  private int handleCreate(int a0) {
    String nameFile = readVirtualMemoryString(a0, 256);
    OpenFile nFile = UserKernel.fileSystem.open(nameFile, true);
    FileAllocator pristFile = new FileAllocator(nFile, nameFile);

    if (nFile == null) return -1;
    else {
      int pgIndex = emptyFileSlotFinder();
      if (pgIndex == -1) return -1;
      else {
        falloc[pgIndex] = pristFile;
        return pgIndex;
      }
    }
  }

  private int handleOpen(int a0) {
    String nameFile = readVirtualMemoryString(a0, 256);
    OpenFile nFile = UserKernel.fileSystem.open(nameFile, false);
    FileAllocator pristFile = new FileAllocator(nFile, nameFile);

    if (nFile == null) return -1;
    else {
      int pgIndex = emptyFileSlotFinder();
      if (pgIndex == -1) return -1;
      else {
        falloc[pgIndex] = pristFile;
        return pgIndex;
      }
    }
  }

  private int handleRead(int indiFile, int virtAddrs, int byteCount) {
    if (indiFile < 0 || indiFile > 15 || falloc[indiFile].file == null) return -1;

    int readVal;
    byte[] bytCArr = new byte[byteCount];

    readVal = falloc[indiFile].file.read(falloc[indiFile].pos, bytCArr, 0, byteCount);

    if (readVal == -1) return -1;
    else {
      falloc[indiFile].pos = falloc[indiFile].pos + writeVirtualMemory(virtAddrs, bytCArr);
      return readVal;
    }
  }

  private int handleWrite(int indiFile, int virtAddrs, int byteCount) {
    if (indiFile < 0 || indiFile > 15 || falloc[indiFile].file == null) return -1;

    byte[] bytCArr = new byte[byteCount];
    int numBytes = readVirtualMemory(virtAddrs, bytCArr);
    int writeBVal;

    writeBVal = falloc[indiFile].file.write(falloc[indiFile].pos, bytCArr, 0, numBytes);

    if (writeBVal == -1) return -1;
    else {
      falloc[indiFile].pos = falloc[indiFile].pos + writeBVal;
      return writeBVal;
    }
  }

  private int handleClose(int indiFile) {
    if (indiFile < 0 || indiFile > 15 || falloc[indiFile].file == null) return -1;

    falloc[indiFile].pos = 0;
    falloc[indiFile].file.close();
    falloc[indiFile].file = null;
    boolean successful = true;

    if (falloc[indiFile].unlinked) {
      successful = UserKernel.fileSystem.remove(falloc[indiFile].name);
      falloc[indiFile].unlinked = false;
    }

    falloc[indiFile].name = "";

    if (successful) return 0;
    else return -1;
  }

  // exit() never returns

  private int handleUnlink(int filAddrs) {
    String nameFile = readVirtualMemoryString(filAddrs, 256);
    int pgIndex = namedFileSlotFinder(nameFile);
    boolean sucRemoved = true;

    if (pgIndex == -1) sucRemoved = UserKernel.fileSystem.remove(nameFile);
    else falloc[pgIndex].unlinked = true;

    if (sucRemoved) return 0;
    else return -1;
  }

  /**
   * Terminate the current process immediately. Any open file descriptors belonging to the process
   * are closed. Any children of the process no longer have a parent process.
   *
   * <p>status is returned to the parent process as this process's exit status and can be collected
   * using the join syscall. A process exiting normally should (but is not required to) set status
   * to 0.
   *
   * <p>exit() never returns.
   */
  void handleExit(int status) {
    // close all files
    for (int i = 0; i < falloc.length; i++) {
      if (falloc[i].file != null) handleSyscall(syscallClose, i, 0, 0, 0);
    }
    // remove this process as a parent
    while (childrenID != null && !childrenID.isEmpty()) {
      UserProcess child = UserKernel.getProcess(childrenID.removeFirst());
      child.parentID = UserKernel.getROOT();
    }

    exitStatus = status;

    // free memory
    unloadSections();

    // finish the thread unless I am the root, in which case terminate the kernel
    if (myID != UserKernel.getROOT()) KThread.currentThread().finish();
    else Kernel.kernel.terminate();

    Lib.assertNotReached();
  }

  /**
   * Execute the program stored in the specified file, with the specified arguments, in a new child
   * process. The child process has a new unique process ID, and starts with stdin opened as file
   * descriptor 0, and stdout opened as file descriptor 1.
   *
   * <p>file is a null-terminated string that specifies the name of the file containing the
   * executable. Note that this string must include the ".coff" extension.
   *
   * <p>argc specifies the number of arguments to pass to the child process. This number must be
   * non-negative.
   *
   * <p>argv is an array of pointers to null-terminated strings that represent the arguments to pass
   * to the child process. argv[0] points to the first argument, and argv[argc-1] points to the last
   * argument.
   *
   * <p>exec() returns the child process's process ID, which can be passed to join(). On error,
   * returns -1.
   */
  private int handleExec(int file, int argc, int argv) {
    if (argc < 0) return -1;

    String nameFile = readVirtualMemoryString(file, 256);
    if (nameFile == null) return -1;

    if (nameFile.substring(nameFile.length() - 4, nameFile.length()).equals(".coff")) return -1;

    // read the in the arguments as String
    String args[] = new String[argc];
    byte temp[] = new byte[4];
    for (int i = 0; i < argc; i++) {
      // check for valid strings
      if (readVirtualMemory(argv + i * 4, temp) != 4) return -1;

      // fill in the arguments
      args[i] = readVirtualMemoryString(Lib.bytesToInt(temp, 0), 256);
    }

    UserProcess child = newUserProcess();
    child.parentID = myID;
    childrenID.add(child.myID);

    if (child.execute(nameFile, args)) return child.myID;
    else return -1;
  }

  /**
   * Suspend execution of the current process until the child process specified by the processID
   * argument has exited. If the child has already exited by the time of the call, returns
   * immediately. When the current process resumes, it disowns the child process, so that join()
   * cannot be used on that process again.
   *
   * <p>processID is the process ID of the child process, returned by exec().
   *
   * <p>status points to an integer where the exit status of the child process will be stored. This
   * is the value the child passed to exit(). If the child exited because of an unhandled exception,
   * the value stored is not defined.
   *
   * <p>If the child exited normally, returns 1. If the child exited as a result of an unhandled
   * exception, returns 0. If processID does not refer to a child process of the current process,
   * returns -1.
   */
  private int handleJoin(int processID, int status) {

    UserProcess child = null;

    for (Integer id : childrenID) {
      if (id == processID) {
        child = UserKernel.getProcess(processID);
        break;
      }
    }

    // only let children join
    if (child == null) {
      Lib.debug(dbgProcess, "handleJoin: this is not my child");
      return -1;
    }

    child.thread.join();

    UserKernel.removeProcess(processID);

    if (child.exitStatus == -1) {
      return 0;
    }

    // store the exit status in the given pointer
    byte arr[] = Lib.bytesFromInt(child.exitStatus);
    if (writeVirtualMemory(status, arr) == 4) {
      return 1;
    } else {
      return 0;
    }
  }

  /**
   * Handle a syscall exception. Called by <tt>handleException()</tt>. The <i>syscall</i> argument
   * identifies which syscall the user executed:
   *
   * <p>
   *
   * <table>
   * <tr><td>syscall#</td><td>syscall prototype</td></tr>
   * <tr><td>0</td><td><tt>void halt();</tt></td></tr>
   * <tr><td>1</td><td><tt>void exit(int status);</tt></td></tr>
   * <tr><td>2</td><td><tt>int  exec(char *name, int argc, char **argv);
   * </tt></td></tr>
   * <tr><td>3</td><td><tt>int  join(int pid, int *status);</tt></td></tr>
   * <tr><td>4</td><td><tt>int  creat(char *name);</tt></td></tr>
   * <tr><td>5</td><td><tt>int  open(char *name);</tt></td></tr>
   * <tr><td>6</td><td><tt>int  read(int fd, char *buffer, int size);
   * </tt></td></tr>
   * <tr><td>7</td><td><tt>int  write(int fd, char *buffer, int size);
   * </tt></td></tr>
   * <tr><td>8</td><td><tt>int  close(int fd);</tt></td></tr>
   * <tr><td>9</td><td><tt>int  unlink(char *name);</tt></td></tr>
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
      case syscallHalt:
        return handleHalt();

      case syscallCreate:
        return handleCreate(a0);
      case syscallOpen:
        return handleOpen(a0);
      case syscallRead:
        return handleRead(a0, a1, a2);
      case syscallWrite:
        return handleWrite(a0, a1, a2);
      case syscallClose:
        return  handleClose(a0);
      case syscallUnlink:
        return handleUnlink(a0);

      case syscallExit:
        handleExit(a0);

      case syscallExec:
        return handleExec(a0, a1, a2);

      case syscallJoin:
        return handleJoin(a0, a1);

      default:
        Lib.debug(dbgProcess, "Unknown syscall " + syscall);
        Lib.assertNotReached("Unknown system call!");
    }
    return 0;
  }

  /**
   * Handle a user exception. Called by <tt>UserKernel.exceptionHandler()</tt>. The <i>cause</i>
   * argument identifies which exception occurred; see the <tt>Processor.exceptionZZZ</tt>
   * constants.
   *
   * @param cause the user exception that occurred.
   */
  public void handleException(int cause) {
    Processor processor = Machine.processor();

    switch (cause) {
      case Processor.exceptionSyscall:
        int result =
            handleSyscall(
                processor.readRegister(Processor.regV0),
                processor.readRegister(Processor.regA0),
                processor.readRegister(Processor.regA1),
                processor.readRegister(Processor.regA2),
                processor.readRegister(Processor.regA3));
        processor.writeRegister(Processor.regV0, result);
        processor.advancePC();
        break;

      default:
        // free the memory in case of abnormal exit
        // unloadSections();
        handleExit(-1);

        Lib.debug(dbgProcess, "Unexpected exception: " + Processor.exceptionNames[cause]);
        // Lib.assertNotReached("Unexpected exception");
    }
  }

  private int emptyFileSlotFinder() {
    for (int i = 0; i < 16; i++) {
      if (falloc[i].file == null) return i;
    }

    return -1;
  }

  private int namedFileSlotFinder(String fname) {
    for (int i = 0; i < 16; i++) {
      if (falloc[i].name == fname) return i;
    }

    return -1;
  }

  public int getParentID() {
    return parentID;
  }

  public class FileAllocator { // Properties of a file
    private OpenFile file = null;
    private String name = "";
    private int pos = 0;
    private boolean unlinked = false;

    public FileAllocator() {}

    public FileAllocator(OpenFile file, String name) {
      this.file = file;
      this.name = name;
    }

    public OpenFile getFile() {
      return file;
    }

    public void setFile(OpenFile file) {
      this.file = file;
    }

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public int getPos() {
      return pos;
    }

    public void setPos(int pos) {
      this.pos = pos;
    }

    public boolean isUnlinked() {
      return unlinked;
    }

    public void setUnlinked(boolean unlinked) {
      this.unlinked = unlinked;
    }
  }
}
