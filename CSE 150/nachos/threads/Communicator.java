package nachos.threads;

/**
 * A <i>communicator</i> allows threads to synchronously exchange 32-bit messages. Multiple threads
 * can be waiting to <i>speak</i>, and multiple threads can be waiting to <i>listen</i>. But there
 * should never be a time when both a speaker and a listener are waiting, because the two threads
 * can be paired off at this point.
 */
public class Communicator {
  private Lock lock;
  private Condition2 condListen;
  private Condition2 condSpeak;
  private int word;
  private boolean wordSpoken;

  /** Allocate a new communicator. */
  public Communicator() {
    lock = new Lock();
    condListen = new Condition2(lock);
    condSpeak = new Condition2(lock);
    wordSpoken = false;
  }

  /**
   * Wait for a thread to listen through this communicator, and then transfer <i>word</i> to the
   * listener.
   *
   * <p>
   *
   * <p>Does not return until this thread is paired up with a listening thread. Exactly one listener
   * should receive <i>word</i>.
   *
   * @param word the integer to transfer.
   */
  public void speak(int word) {
    if (!lock.isHeldByCurrentThread()) lock.acquire();

    while (wordSpoken) {
      condListen.wakeAll();
      condSpeak.sleep();
    }

    this.word = word;
    wordSpoken = true;
    condListen.wakeAll();
    condSpeak.sleep();

    lock.release();
  }

  /**
   * Wait for a thread to speak through this communicator, and then return the <i>word</i> that
   * thread passed to <tt>speak()</tt>.
   *
   * @return the integer transferred.
   */
  public int listen() {
    if (!lock.isHeldByCurrentThread()) lock.acquire();

    while (!wordSpoken) condListen.sleep();

    int word = this.word;
    condSpeak.wakeAll();
    wordSpoken = false;

    lock.release();

    return word;
  }
}
