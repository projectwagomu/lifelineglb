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

/**
 * Computation abstraction for the multi-worker Global Load Balancer.
 *
 * <p>The requirements on the computation to be distributed are the following. We see the
 * computation as a large bag. In the Global Load Balancer routine, a certain amount of computation
 * present in the bag are computed before checking if load balance operations are necessary. The
 * method responsible for the computation of a set amount of tasks is method {@link #process(int,
 * Fold)} while the {@link #split(boolean)} and {@link #merge(Bag)} methods perform the load balance
 * operations.
 *
 * <p>Load balance operations consist in splitting (method {@link #split(boolean)}) the bag into two
 * instances, each containing part of all the tasks present in the original bag at the time of
 * splitting. One bag is then transfered to a remote host of a local worker which ran out of work
 * and merged (method {@link #merge(Bag)}) into the bag held by that remote host or worker.
 *
 * <p>To ensure that tasks can be relocated from one host to another and be executed successfully,
 * computations that require some form of access to (mutable) information located on a remote host
 * is not supported. The Global Load Balancer can only handle computation that can contains in
 * itself all the information needed perform the computation.
 *
 * <p>When all the bags have been depleted on all the remote hosts used in the computation, the
 * result computation takes place. The first step of this operation consists for each {@link Bag}
 * instance to put its contribution into the user-defined result instance provided instance in
 * method {@link #submit(Fold)}. Each instance of the result type (one per host) are then gathered
 * and "folded" back on one host before being returned to the user. This last operation places some
 * requirements on the kind of result produced by the computation which are further discussed in the
 * description of the {@link Fold} interface.
 *
 * <p>There are two type parameters to the interface, <em>B</em> and <em>R</em>. The first parameter
 * <em>B</em> should be the implementing class itself and is used in methods {@link #split(boolean)}
 * and {@link #merge(Bag)} as a reflective operation. The second parameter <em>R</em> is the kind of
 * result this {@link Bag} produces and should be an implementation of interface {@link Fold}. In
 * order for {@link Bag} implementations to be processed successfully by the {@link GLBcomputer},
 * they will also need to implement the {@link Serializable} interface. This is required in order
 * for instances to be sent to remote hosts as part of the load balancing operations.
 *
 * <p>Example:
 *
 * <pre>
 * public class MyBag implements Bag&lt;MyBag, MyResult&gt;, Serializable {
 *
 * 	private static final long serialVersionUID = 3582168956043482749L;
 * 	// implementation ...
 * }
 * </pre>
 *
 * @param <B> Type parameter used for the return type of method {@link #split(boolean)} and the
 *     parameter type of method {@link #merge(Bag)}. The programmer should choose the implementing
 *     class itself as first parameter.
 * @param <R> Type parameter used for method {@link #submit(Fold)}. The chosen class is "so to
 *     speak" the result produced by the implemented distributed computation. As such it is required
 *     to implement the {@link Fold} interface.
 * @author Patrick Finnerty
 * @see Fold
 */
public interface Bag<B extends Bag<B, R> & Serializable, R extends Fold<R> & Serializable> {

  /**
   * Returns for the count of tasks, which are currently in the queue
   *
   * @return number of tasks currently in the queue
   */
  long getCurrentTaskCount();

  /**
   * Returns for the result produced by this fragment of the computation
   *
   * @return the result
   */
  R getResult();

  /**
   * Initializes statically known tasks. Only needed when using computeStatic(). If so, it is
   * automatically called from GLBComputer
   *
   * @param workerId the worker id whose tasks need to be prepared
   */
  void initStaticTasks(int workerId);

  /**
   * Indicates if this bag has some computation left.
   *
   * @return {@code true} if there is still computation to do within this bag, {@code false}
   *     otherwise.
   */
  boolean isEmpty();

  /**
   * Indicates if this bag can be split, i.e. if a fragment of the computation left inside the bag
   * could be taken off by calling method {@link #split(boolean)} and given to an other worker for
   * computation without depleting the instance of all its work. If this bag is empty, this method
   * should also return {@code false}.
   *
   * @return {@code true} if this bag can be split, {@code false} otherwise
   */
  boolean isSplittable();

  /**
   * Merges the content of the bag given as parameter into this instance.
   *
   * @param b the bag to be merged into this instance
   */
  void merge(B b);

  /**
   * Performs a certain amount of the computation as indicated by the first parameter. If there is
   * less computation in the {@link Bag} then the requested amount of work, should complete all the
   * tasks and then return.
   *
   * <p>The second parameter is the object shared with the other local concurrent workers.
   * Information can be shared between workers through this instance. The programmer will be careful
   * to enforce proper synchronization on that shared object where it is necessary, the library does
   * not enforce any particular protection on that object.
   *
   * @param workAmount the amount of computation to be done
   * @param sharedObject instance shared between workers of one place through which some data
   *     between workers can be shared
   * @return the number of processed tasks
   */
  int process(int workAmount, R sharedObject);

  /**
   * Takes a chunk of computation from this bag and returns it in a new instance. The computation
   * fragment this method returns will be computed by a different thread, possibly on a different
   * host.
   *
   * <p>In the case {@code this} instance is not splittable and the parameter {@code takeAll} is
   * {@code true}, the whole content of the bag should be returned in a new instance, resulting in
   * {@code this} instance becoming empty.
   *
   * <p>If this method is called when this instance is empty, should return an empty bag instance.
   *
   * @param takeAll indicates if the caller wants the whole content of this instance in the event it
   *     cannot be split. Can be ignored by the programmer in other situations.
   * @return a fragment of the computation held in this bag in a new instance
   */
  B split(boolean takeAll);

  /**
   * Asks for the result produced by this fragment of the computation to be placed in the given
   * result R instance.
   *
   * @param r the instance in which the partial result is to be stored
   */
  void submit(R r);
}
