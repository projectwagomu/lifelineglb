package handist.glb.examples.util;

import java.io.Serializable;

public class FixedRailQueueInt implements Serializable {

  private final int[] internalStorage;
  private int head;
  private int tail;

  /** Construct a fixed size queue */
  @SuppressWarnings("unchecked")
  public FixedRailQueueInt(int n) {
    this.internalStorage = new int[n];
    this.head = 0;
    this.tail = 0;
  }

  /** Check if the queue is empty */
  public boolean isEmpty() {
    return this.head == this.tail;
  }

  /** Add the element to the front of the queue. */
  public void push(int t) {
    // Add the element and increase the size
    this.internalStorage[this.tail++] = t;
  }

  /** Output the contents of the queue in the order they are stored */
  public void print() {
    System.out.println("h = " + head + ", t = " + tail + ", ");
    System.out.print("[");
    for (int i = this.head; i < this.tail; ++i) {
      System.out.print(((i == this.head) ? "" : ",") + this.internalStorage[i]);
    }
    System.out.println("]");
  }

  /** Remove and return one element of the queue if FIFO order. */
  public int pop() {
    // Remove the first element from the queue.
    return this.internalStorage[this.head++];
  }

  /** Remove and return one element of the queue in LIFO order. */
  public int top() {
    return this.internalStorage[--this.tail];
  }

  /** Rewind. */
  public void rewind() {
    this.head = 0;
  }

  public int size() {
    return (this.tail - this.head);
  }
}
