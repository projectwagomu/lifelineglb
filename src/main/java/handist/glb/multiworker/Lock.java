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
package handist.glb.multiworker;

import java.io.Serializable;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinPool.ManagedBlocker;
import java.util.concurrent.Semaphore;

/**
 * {@link ManagedBlocker} implementation relying on a Semaphore.
 *
 * <p>This class is used to make threads of the {@link ForkJoinPool} used in the APGAS runtime for
 * Java yield to one another, thus guaranteeing the proper functioning of the {@link GLBcomputer}.
 *
 * @author Patrick Finnerty
 */
public class Lock implements ForkJoinPool.ManagedBlocker, Serializable {

  /** Serial Version UID */
  private static final long serialVersionUID = -3222675796580210125L;

  /** Semaphore used for this lock implementation */
  final Semaphore lock;

  /** Constructor Initializes a lock with no permits. */
  public Lock() {
    lock = new Semaphore(0);
  }

  /*
   * (non-Javadoc)
   *
   * @see java.util.concurrent.ForkJoinPool.ManagedBlocker#block()
   */
  @Override
  public boolean block() {
    try {
      lock.acquire();
    } catch (final InterruptedException e) {
      e.printStackTrace();
      block();
    }
    return true;
  }

  /*
   * (non-Javadoc)
   *
   * @see java.util.concurrent.ForkJoinPool.ManagedBlocker#isReleasable()
   */
  @Override
  public boolean isReleasable() {
    return lock.tryAcquire();
  }

  /** Drains all the permits in this lock. */
  public void reset() {
    lock.drainPermits();
  }

  /** Called to unblock the thread that is blocked using this {@link Lock}. */
  public void unblock() {
    lock.drainPermits(); // Avoids unnecessary accumulation of permits in the
    // Lock. In our situation, a maximum of one permit is
    // sufficient.
    lock.release();
  }
}
