package nachos.threads;

import nachos.machine.Lib;
import nachos.machine.Machine;

import java.util.HashSet;
import java.util.Random;
import java.util.TreeSet;

/**
 * A scheduler that chooses threads using a lottery.
 *
 * <p>
 *
 * <p>A lottery scheduler associates a number of tickets with each thread. When a thread needs to be
 * dequeued, a random lottery is held, among all the tickets of all the threads waiting to be
 * dequeued. The thread that holds the winning ticket is chosen.
 *
 * <p>
 *
 * <p>Note that a lottery scheduler must be able to handle a lot of tickets (sometimes billions), so
 * it is not acceptable to maintain state for every ticket.
 *
 * <p>
 *
 * <p>A lottery scheduler must partially solve the priority inversion problem; in particular,
 * tickets must be transferred through locks, and through joins. Unlike a priority scheduler, these
 * tickets add (as opposed to just taking the maximum).
 */
public class LotteryScheduler extends Scheduler {
  /** The default priority for a new thread. Do not change this value. */
  public static final int priorityDefault = 1;
  /** The minimum priority that a thread can have. Do not change this value. */
  public static final int priorityMinimum = 1;
  /** The maximum priority that a thread can have. Do not change this value. */
  public static final int priorityMaximum = Integer.MAX_VALUE;

  protected static int threadCount = 0;

  /** Allocate a new lottery scheduler. */
  public LotteryScheduler() {}

  static final int safeAdd(int left, int right) {
    if (right > 0 ? left > Integer.MAX_VALUE - right : left < Integer.MIN_VALUE - right) {
      return right > 0 ? Integer.MAX_VALUE : Integer.MIN_VALUE;
    }
    return left + right;
  }

  /**
   * Allocate a new lottery thread queue.
   *
   * @param transferPriority <tt>true</tt> if this queue should transfer tickets from waiting
   *     threads to the owning thread.
   * @return a new lottery thread queue.
   */
  public ThreadQueue newThreadQueue(boolean transferPriority) {
    return new LotteryQueue(transferPriority);
  }

  public int getPriority(KThread thread) {
    Lib.assertTrue(Machine.interrupt().disabled());

    return getThreadState(thread).getPriority();
  }

  public int getEffectivePriority(KThread thread) {
    Lib.assertTrue(Machine.interrupt().disabled());

    return getThreadState(thread).getEffectivePriority();
  }

  public void setPriority(KThread thread, int priority) {
    Lib.assertTrue(Machine.interrupt().disabled());

    Lib.assertTrue(priority >= priorityMinimum && priority <= priorityMaximum);

    getThreadState(thread).setPriority(priority);
  }

  public boolean increasePriority() {
    boolean intStatus = Machine.interrupt().disable();

    KThread thread = KThread.currentThread();

    int priority = getPriority(thread);
    if (priority == priorityMaximum) return false;

    setPriority(thread, priority + 1);

    Machine.interrupt().restore(intStatus);
    return true;
  }

  public boolean decreasePriority() {
    boolean intStatus = Machine.interrupt().disable();

    KThread thread = KThread.currentThread();

    int priority = getPriority(thread);
    if (priority == priorityMinimum) return false;

    setPriority(thread, priority - 1);

    Machine.interrupt().restore(intStatus);
    return true;
  }

  /**
   * Return the scheduling state of the specified thread.
   *
   * @param thread the thread whose scheduling state to return.
   * @return the scheduling state of the specified thread.
   */
  protected ThreadState getThreadState(KThread thread) {
    if (thread.schedulingState == null) thread.schedulingState = new ThreadState(thread);

    return (ThreadState) thread.schedulingState;
  }

  protected class LotteryQueue extends ThreadQueue {
    /**
     * <tt>true</tt> if this queue should transfer priority from waiting threads to the owning
     * thread.
     */
    public boolean transferPriority;
    /** Thread that owns the resource associated with this queue */
    private ThreadState owner = null;
    /** TreeSet<ThreadState>() of threads waiting for access to this resource */
    private TreeSet<ThreadState> threadQueue = new TreeSet<ThreadState>();
    /** Random number generator for ticket selection */
    private Random rng = new Random();

    LotteryQueue(boolean transferPriority) {
      this.transferPriority = transferPriority;
    }

    public void waitForAccess(KThread thread) {
      Lib.assertTrue(Machine.interrupt().disabled());
      getThreadState(thread).waitForAccess(this);
    }

    public void acquire(KThread thread) {
      Lib.assertTrue(Machine.interrupt().disabled());
      getThreadState(thread).acquire(this);
    }

    public KThread nextThread() {
      Lib.assertTrue(Machine.interrupt().disabled());
      if (threadQueue.isEmpty()) return null;
      ThreadState t = pickNextThread();
      if (t == null || t.thread == null) return null;
      t.acquire(this);
      return t.thread;
    }

    private ThreadState pickNextThread() {
      int total = 0;
      for (ThreadState ts : threadQueue) total = safeAdd(total, ts.getEffectivePriority());
      if (total == 0) return null;
      Random rng = new Random();
      int ticket = rng.nextInt(total) + 1;
      int count = 0;
      ThreadState result = null;
      for (ThreadState ts : threadQueue) {
        result = ts;
        count = safeAdd(count, ts.getEffectivePriority());
        if (count >= ticket) break;
      }
      return result;
    }

    public void print() {
      Lib.assertTrue(Machine.interrupt().disabled());
      for (ThreadState t : threadQueue) {
        System.out.println(t.toString());
      }
    }
  }

  /**
   * The scheduling state of a thread. This should include the thread's priority, its effective
   * priority, any objects it owns, and the queue it's waiting for, if any.
   *
   * @see nachos.threads.KThread#schedulingState
   */
  protected class ThreadState implements Comparable<ThreadState> {
    /** The thread with which this object is associated. */
    protected KThread thread;
    /** The priority of the associated thread. */
    protected Integer priority = priorityDefault;
    /** Cached effective priority */
    protected Integer effective = priorityMinimum;
    /** Time when thread was inserted to queue */
    protected Long time = Machine.timer().getTime();
    /** ID for thread state */
    protected Integer id;
    /** Resources held by this thread */
    protected HashSet<LotteryQueue> resources = new HashSet<LotteryQueue>();
    /** LotteryQueue of resources this thread is waiting on */
    protected LotteryQueue waitQueue = null;
    /** Set of threads with diffs that have been updated */
    protected HashSet<Integer> updated = new HashSet<Integer>();

    /**
     * Allocate a new <tt>ThreadState</tt> object and associate it with the specified thread.
     *
     * @param thread the thread this state belongs to.
     */
    public ThreadState(KThread thread) {
      this.thread = thread;
      this.id = threadCount++;
    }

    /**
     * Return the priority of the associated thread.
     *
     * @return the priority of the associated thread.
     */
    public int getPriority() {
      return priority;
    }

    /**
     * Set the priority of the associated thread to the specified value.
     *
     * @param priority the new priority.
     */
    public void setPriority(int priority) {
      Lib.assertTrue(Machine.interrupt().disabled());
      if (this.priority == priority) return;
      this.priority = priority;
      updatePriorities();
    }

    /**
     * Return the effective priority of the associated thread.
     *
     * @return the effective priority of the associated thread.
     */
    public Integer getEffectivePriority() {
      Lib.assertTrue(Machine.interrupt().disabled());
      return effective;
    }

    public void updatePriorities() {
      int initEff = this.getEffectivePriority() > 0 ? this.getEffectivePriority() : priorityMinimum;
      effective = this.getPriority() > 0 ? this.getPriority() : priorityMinimum;

      for (LotteryQueue q : resources)
        if (q.transferPriority)
          for (ThreadState t : q.threadQueue)
            effective = safeAdd(effective, t.getEffectivePriority());

      if (waitQueue != null && waitQueue.owner != null)
        waitQueue.owner.updateDiff(effective - initEff, updated);
      updated.clear();
    }

    public void updateDiff(int diff, HashSet<Integer> updated) {
      effective = safeAdd(effective, diff);
      updated.add(id);
      if (waitQueue != null
          && waitQueue.owner != null
          && waitQueue.transferPriority
          && !updated.contains(waitQueue.owner.id)) {
        waitQueue.owner.updateDiff(diff, updated);
      }
    }

    /**
     * Called when <tt>waitForAccess(thread)</tt> (where <tt>thread</tt> is the associated thread)
     * is invoked on the specified priority queue. The associated thread is therefore waiting for
     * access to the resource guarded by <tt>threadQueue</tt>. This method is only called if the
     * associated thread cannot immediately obtain access.
     *
     * @param queue the queue that the associated thread is now waiting on.
     * @see nachos.threads.ThreadQueue#waitForAccess
     */
    public void waitForAccess(LotteryQueue queue) {
      Lib.assertTrue(Machine.interrupt().disabled());

      time = Machine.timer().getTime();
      queue.threadQueue.add(this);
      this.waitQueue = queue;

      updatePriorities();
      if (queue.owner != null) queue.owner.updatePriorities();
    }

    /**
     * Called when the associated thread has acquired access to whatever is guarded by
     * <tt>threadQueue</tt>. This can occur either as a result of <tt>acquire(thread)</tt> being
     * invoked on <tt>threadQueue</tt> (where <tt>thread</tt> is the associated thread), or as a
     * result of <tt>nextThread()</tt> being invoked on <tt>threadQueue</tt>.
     *
     * @see nachos.threads.ThreadQueue#acquire
     * @see nachos.threads.ThreadQueue#nextThread
     */
    public void acquire(LotteryQueue queue) {
      Lib.assertTrue(Machine.interrupt().disabled());
      if (queue.owner != null) queue.owner.resources.remove(queue);
      queue.threadQueue.remove(this);
      queue.owner = this;
      resources.add(queue);

      updatePriorities();
    }

    @Override
    public int compareTo(ThreadState thread) {
      int p = getEffectivePriority().compareTo(thread.getEffectivePriority());
      int t = thread.time.compareTo(time);
      int i = id.compareTo(thread.id);

      if (p == 0 && t != 0) return t;
      else if (p != 0) return p;
      else return i;
    }

    @Override
    public String toString() {
      return "ThreadState{" + "priority=" + priority + '}';
    }

    @Override
    public boolean equals(Object o) {
      return (o instanceof ThreadState) && (this.compareTo((ThreadState) o) == 0);
    }
  }
}
