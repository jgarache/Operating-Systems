package nachos.threads;

import nachos.machine.Lib;
import nachos.machine.Machine;

import java.util.TreeSet;

/**
 * A scheduler that chooses threads based on their priorities.
 *
 * <p>
 *
 * <p>A priority scheduler associates a priority with each thread. The next thread to be dequeued is
 * always a thread with priority no less than any other waiting thread's priority. Like a
 * round-robin scheduler, the thread that is dequeued is, among all the threads of the same
 * (highest) priority, the thread that has been waiting longest.
 *
 * <p>
 *
 * <p>Essentially, a priority scheduler gives access in a round-robin fassion to all the
 * highest-priority threads, and ignores all other threads. This has the potential to starve a
 * thread if there's always a thread waiting with higher priority.
 *
 * <p>
 *
 * <p>A priority scheduler must partially solve the priority inversion problem; in particular,
 * priority must be donated through locks, and through joins.
 */
public class PriorityScheduler extends Scheduler {
  /** The default priority for a new thread. Do not change this value. */
  public static final int priorityDefault = 1;
  /** The minimum priority that a thread can have. Do not change this value. */
  public static final int priorityMinimum = 0;
  /** The maximum priority that a thread can have. Do not change this value. */
  public static final int priorityMaximum = 7;

  protected static int queueCount = 0;
  protected static int threadCount = 0;

  /** Allocate a new priority scheduler. */
  public PriorityScheduler() {}

  /**
   * Allocate a new priority thread queue.
   *
   * @param transferPriority <tt>true</tt> if this queue should transfer priority from waiting
   *     threads to the owning thread.
   * @return a new priority thread queue.
   */
  public ThreadQueue newThreadQueue(boolean transferPriority) {
    return new PriorityQueue(transferPriority);
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

  public void updatePriorities(boolean reorder, ThreadState thread) {
    if (thread.waitQueue != null) {
      TreeSet<ThreadState> queue = thread.waitQueue.threadQueue;
      /** reorder queue after effective priority of a thread has been changed */
      if (reorder && queue.contains(thread)) {
        queue.remove(thread);
        queue.add(thread);
      }
      /**
       * compare priority of owner thread with highest priority thread in queue and recursively
       * update priority and reorder for everything owner holds
       */
      ThreadState t = thread.waitQueue.currentThread;
      if (t != null && !queue.isEmpty())
        if (thread.waitQueue.transferPriority
            && queue.last().getEffectivePriority() > t.getEffectivePriority()) {
          t.effective = queue.last().getEffectivePriority();
          updatePriorities(true, t);
        }
    }
  }

  /** A <tt>ThreadQueue</tt> that sorts threads by priority. */
  protected class PriorityQueue extends ThreadQueue implements Comparable<PriorityQueue> {
    /**
     * <tt>true</tt> if this queue should transfer priority from waiting threads to the owning
     * thread.
     */
    public boolean transferPriority;

    protected int id;

    protected ThreadState currentThread = null;

    protected TreeSet<ThreadState> threadQueue = new TreeSet<ThreadState>();

    PriorityQueue(boolean transferPriority) {
      this.transferPriority = transferPriority;
      this.id = queueCount++;
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
      ThreadState t = threadQueue.pollLast();
      if (t == null || t.thread == null) return null;
      t.id = -1;
      if (currentThread != null) {
        currentThread.effective = priorityMinimum;
        TreeSet<PriorityQueue> r = currentThread.resources;
        r.remove(this);
        if (!r.isEmpty() && r.last().pickNextThread() != null)
          currentThread.effective =
              Math.max(
                  r.last().pickNextThread().getEffectivePriority(),
                  currentThread.getEffectivePriority());
      }
      t.acquire(this);
      return t.thread;
    }

    /**
     * Return the next thread that <tt>nextThread()</tt> would return, without modifying the state
     * of this queue.
     *
     * @return the next thread that <tt>nextThread()</tt> would return.
     */
    protected ThreadState pickNextThread() {
      Lib.assertTrue(Machine.interrupt().disabled());
      return threadQueue.isEmpty() ? null : threadQueue.last();
    }

    public void print() {
      Lib.assertTrue(Machine.interrupt().disabled());
      for (ThreadState t : threadQueue.descendingSet()) {
        System.out.println(t.toString());
      }
    }

    @Override
    public int compareTo(PriorityQueue q) {
      if (id == q.id) return 0;
      if (q.threadQueue.isEmpty()) return 1;
      if (threadQueue.isEmpty()) return -1;

      return threadQueue.last().compareTo(q.threadQueue.last());
    }

    @Override
    public boolean equals(Object o) {
      return (o instanceof PriorityQueue) && (this.compareTo((PriorityQueue) o) == 0);
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
    protected Long time = 0L;
    /** ID for thread state */
    protected Integer id;
    /** Resources held by this thread */
    protected TreeSet<PriorityQueue> resources = new TreeSet<PriorityQueue>();
    /** PriorityQueue of resources this thread is waiting on */
    protected PriorityQueue waitQueue;

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

      updatePriorities(true, this);
    }

    /**
     * Return the effective priority of the associated thread.
     *
     * @return the effective priority of the associated thread.
     */
    public Integer getEffectivePriority() {
      Lib.assertTrue(Machine.interrupt().disabled());
      return priority > effective ? priority : effective;
    }

    /**
     * Called when <tt>waitForAccess(thread)</tt> (where <tt>thread</tt> is the associated thread)
     * is invoked on the specified priority queue. The associated thread is therefore waiting for
     * access to the resource guarded by <tt>threadQueue</tt>. This method is only called if the
     * associated thread cannot immediately obtain access.
     *
     * @param waitQueue the queue that the associated thread is now waiting on.
     * @see nachos.threads.ThreadQueue#waitForAccess
     */
    public void waitForAccess(PriorityQueue waitQueue) {
      Lib.assertTrue(Machine.interrupt().disabled());

      time = Machine.timer().getTime();
      waitQueue.threadQueue.add(this);
      this.waitQueue = waitQueue;

      id = id == -1 ? threadCount++ : id;

      updatePriorities(false, this);
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
    public void acquire(PriorityQueue waitQueue) {
      Lib.assertTrue(Machine.interrupt().disabled());
      if (waitQueue.currentThread != null) waitQueue.currentThread.resources.remove(waitQueue);
      waitQueue.currentThread = this;

      resources.add(waitQueue);

      this.waitQueue = null;

      TreeSet<ThreadState> queue = waitQueue.threadQueue;

      if (!queue.isEmpty() && waitQueue.transferPriority) {
        effective = Math.max(queue.last().getEffectivePriority(), getEffectivePriority());
        updatePriorities(true, this);
      }
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
