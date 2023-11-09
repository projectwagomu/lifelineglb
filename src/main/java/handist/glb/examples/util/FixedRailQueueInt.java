/*
 * Copyright (c) 2023 Wagomu project.
 *
 * This program and the accompanying materials are made available to you under
 * the terms of the Eclipse Public License 1.0 which accompanies this
 * distribution,
 * and is available at https://www.eclipse.org/legal/epl-v10.html
 *
 * SPDX-License-Identifier: EPL-1.0
 */
package handist.glb.examples.util;

import java.io.Serializable;

public class FixedRailQueueInt implements Serializable {

  private static final long serialVersionUID = -4091123581702286687L;
  private final int[] internalStorage;
  private int head;
  private int tail;

  /** Construct a fixed size queue */
  public FixedRailQueueInt(int n) {
    internalStorage = new int[n];
    head = 0;
    tail = 0;
  }

  /** Check if the queue is empty */
  public boolean isEmpty() {
    return head == tail;
  }

  /** Remove and return one element of the queue if FIFO order. */
  public int pop() {
    // Remove the first element from the queue.
    return internalStorage[head++];
  }

  /** Output the contents of the queue in the order they are stored */
  public void print() {
    System.out.println("h = " + head + ", t = " + tail + ", ");
    System.out.print("[");
    for (int i = head; i < tail; ++i) {
      System.out.print(((i == head) ? "" : ",") + internalStorage[i]);
    }
    System.out.println("]");
  }

  /** Add the element to the front of the queue. */
  public void push(int t) {
    // Add the element and increase the size
    internalStorage[tail++] = t;
  }

  /** Rewind. */
  public void rewind() {
    head = 0;
  }

  public int size() {
    return (tail - head);
  }

  /** Remove and return one element of the queue in LIFO order. */
  public int top() {
    return internalStorage[--tail];
  }
}
