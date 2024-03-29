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

import static apgas.Constructs.*;

import apgas.Configuration;
import apgas.Constructs;
import apgas.GlobalRuntime;
import apgas.Place;
import apgas.impl.GlobalRuntimeImpl;
import apgas.impl.elastic.EvolvingHandler;
import apgas.impl.elastic.GetCpuLoad;
import apgas.impl.elastic.MalleableHandler;
import apgas.util.ConsolePrinter;
import apgas.util.GlobalID;
import apgas.util.GlobalRef;
import apgas.util.PlaceLocalObject;
import handist.glb.multiworker.lifeline.LifelineStrategy;
import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerArray;

/**
 * Class {@link GLBcomputer} implements a lifeline-based work-stealing scheme between hosts with an
 * internal management that allows multiple workers to perform computation concurrently on the same
 * Java process.
 *
 * <p>This {@link GLBcomputer} handles distributed computation that fits the {@link Bag} interface
 * using multiple concurrent workers on the same hosts. It follows the design proposed by Yamashita
 * and Kamada in their article <a href=
 * "https://www.jstage.jst.go.jp/article/ipsjjip/24/2/24_416/_article">Introducing a Multithread and
 * Multistage Mechanism for the Global Load Balancing Library of X10</a>. The current design does
 * not implement the multi-stage mechanisms described in their article.
 *
 * <p>The requirements on the kind of computation and the features available to the programmer are
 * further detailed in the documentation of classes {@link Bag} and {@link Fold}. The customization
 * possibilities of the load balance algorithm are presented in class {@link
 * GLBMultiWorkerConfiguration}.
 *
 * <p>The work-stealing scheme is similar in spirit to the original lifeline-based Global Load
 * Balancer implemented in the X10 programming language. Inactive hosts passively wait for some work
 * to reach them through their lifelines to resume computation. The major difference with the
 * original scheme comes in the fact that this implementation accommodates for several concurrent
 * workers in a single Java process. Some load balance is performed internally to keep the workers
 * occupied as much as possible while still allowing remote hosts to steal work. The design relies
 * on two {@link Bag} instances that are kept aside to perform load balance. One is primarily in
 * charge of load balance operations between the workers running on the local host (member {@link
 * #intraPlaceQueue}) while the other in dedicated to steals from remote hosts (member {@link
 * #interPlaceQueue}).
 *
 * <p>This implementation was then extended by Jonas Posner to make it malleable, i.e. capable of
 * dynamically adding and removing places to the distributed program. The scheme is adapted to
 * disconnect places to be removed from the work-stealing network and relocate the work they hold to
 * continued places. When grow orders are received, the new places are connected to the
 * work-stealing network so that they receive work through the usual work-stealing mechanism.
 *
 * @author Patrick Finnerty, Jonas Posner
 * @param <R> type of the result produced by the computation
 * @param <B> type of the computation bag
 */
public class GLBcomputer<R extends Fold<R> & Serializable, B extends Bag<B, R> & Serializable>
    extends PlaceLocalObject implements MalleableHandler, EvolvingHandler {

  /** Printing Helper */
  private static final ConsolePrinter console = ConsolePrinter.getInstance();

  /** Place this instance is located on */
  final Place HOME;

  final Object lifelineLock = new Object();

  /**
   * ForkJoinPool of the APGAS runtime used at this place to process the activities. This member is
   * kept in order for asynchronous {@link #workerProcess(WorkerBag)} activities to check if the
   * pool has pending "shorter" activities and yield if necessary.
   *
   * <p><em>This is not safe and subject to failures if the APGAS library were to evolve.</em>
   *
   * <p>The current APGAS implementation relies on a {@link ForkJoinPool} on each place to keep all
   * the asynchronous tasks submitted to a place's runtime. If an other class were to be used,
   * errors when initializing this field in the constructor of {@link GLBcomputer} are likely to
   * appear.
   *
   * @see #workerProcess(WorkerBag)
   * @see #workerLock
   * @see <a href=
   *     "https://github.com/x10-lang/apgas/blob/master/apgas/src/apgas/impl/GlobalRuntimeImpl.java">apgas/src/apgas/impl/GlobalRuntimeImpl.java</a>
   */
  final ForkJoinPool POOL;

  /**
   * Collection of the {@link WorkerBag} of the inactive worker threads on this place.
   *
   * <p><em>Before the computation</em>, as many empty {@link WorkerBag}s as concurrent workers
   * ({@link GLBMultiWorkerConfiguration}) are placed in this collection as part of method {@link
   * #reset(SerializableSupplier, SerializableSupplier, SerializableSupplier, boolean, List,
   * boolean)}.
   *
   * <p><em>During the computation</em>, this collection contains the {@link Bag}s of the workers
   * that are not active. Prior to launching a new asynchronous {@link #workerProcess(WorkerBag)}, a
   * {@link WorkerBag} is polled from this collection and some computation is merged into it. When a
   * {@link #workerProcess(WorkerBag)} terminates because it has completed its fraction of the
   * computation and could not get more work from the intra place load balancing mechanisms, it
   * places its {@link WorkerBag} back into this collection.
   *
   * <p>This collection is also used as the lock provider for synchronized blocks when member {@link
   * #workerCount} needs to be read or modified in a protected manner. This includes segments of
   * methods {@link #deal(int, Bag, GlobalRef)} and {@link #workerProcess(WorkerBag)}.
   *
   * <p><em>After the computation</em>, all the {@link Bag}s processed by the workers are present in
   * this collection. This allows access for the collection of each computation fragment in method
   * {@link #collectResult()}.
   */
  final ConcurrentLinkedQueue<WorkerBag> workerBags;

  private final long[] lastPrint;

  /** Holds the lifelineStrategy */
  private final LifelineStrategy lifelineStrategy;

  /** List containing all old removed places because of malleability */
  private final ConcurrentLinkedQueue<Integer> mallRemovedPlaces;

  /**
   * Array containing a flag for each worker (the worker's id is used as index in the array). A
   * {@code 1} value at index {@code i} indicates that the "i"th worker is requested to send work to
   * the {@link #interPlaceQueue}. The whole array is turned to 1 values when it is discovered by a
   * worker that the {@link #interPlaceQueue} is empty. Workers put their assigned flag back to 0
   * when they feed the {@link #interPlaceQueue} as part of their main routine {@link
   * #workerProcess(WorkerBag)}.
   */
  private final AtomicIntegerArray feedInterQueueRequested;

  /** Bag used to perform load balance between the worker within this place */
  B interPlaceQueue;

  /** Flag used to signal the fact member {@link #interPlaceQueue} is empty */
  volatile boolean interQueueEmpty;

  /**
   * Bag used to perform load-balance with remote hosts. It is also used to provide the lock used
   * when accessing members {@link #intraPlaceQueue} or {@link #interPlaceQueue}.
   */
  B intraPlaceQueue;

  /** Flag used to signal the fact member {@link #intraPlaceQueue} is empty */
  volatile boolean intraQueueEmpty;

  /**
   * Lifelines this place can establish. Access is protected by synchronized {@link #lifelineLock}
   */
  volatile int[] LIFELINE;

  /**
   * Lock used by the {@link #lifelineAnswerThread()} to yield its thread. When a lifeline answer
   * becomes possible, a {@link #workerProcess(WorkerBag)} will {@link Lock#unblock()} this lock to
   * allow progress of the {@link #lifelineAnswerThread()}.
   */
  Lock lifelineAnswerLock;

  /**
   * Flag used to confirm that the lifelineAnswerThread has exited. This prevents a potential bug
   * where a lifeline answer comes just as the old lifeline answer thread is woken up for exit. When
   * the lifeline answer calls proceeds to method {@link #run(Bag)}, it can put {@link #shutdown}
   * back to {@code false} before the old thread could exit, resulting in multiple lifeline answer
   * thread running on the same place.
   *
   * <p>To solve this issue, method {@link #run(Bag)} actively waits until this flag is set back to
   * true by the exiting lifeline answer thread before spawning a new one.
   */
  volatile boolean lifelineAnswerThreadExited;

  /**
   * Collection used to keep track of the lifelines this place has established on other places.
   *
   * <p>The key is the integer identifying the remote place on which this place may establish
   * lifelines, the value is {@code true} when the lifeline is established, {@code false} otherwise.
   * There is always a mapping in this member for every potential lifeline established by this
   * place.
   */
  ConcurrentHashMap<Integer, Boolean> lifelineEstablished;

  /**
   * Collection used to record the lifeline thieves that have requested some work from this place
   * but could not be given some work straight away as part of method {@link #steal(int,
   * GlobalRef)}. The thieves stored in this member will be answered by the thread running the
   * {@link #lifelineAnswerThread()} when work becomes available.
   */
  ConcurrentLinkedQueue<Integer> lifelineThieves;

  /**
   * Flag used by {@link #workerProcess(WorkerBag)} to signal that one of them has unblocked the
   * progress of the lifelineAnswer thread and that it needs to answer lifelines. When a {@link
   * #workerProcess(WorkerBag)} decided to unlock the progress of the lifeline answer thread ({@link
   * #lifelineAnswerThread()}), it sets the value of {@link #lifelineToAnswer} to {@code true}. The
   * value is set back to {@code false} by the {@link #lifelineAnswerThread()} when it actually
   * becomes active again.
   *
   * <p>Worker processes check the value of this boolean before considering waking the lifeline
   * answer thread. This avoids having multiple workers repetitively attempting to wake up the
   * lifeline answer thread. {@link #lifelineAnswerThread()}.
   */
  volatile boolean lifelineToAnswer;

  /**
   * Logger instance used to log the runtime of the {@link GLBcomputer} instance located at place
   * {@link #HOME}.
   */
  PlaceLogger logger;

  /**
   * Indicates if the log aggregation has already being performed in method {@link #getLog()}.
   * Avoids a second aggregation of the logs if multiple calls to this method are made following a
   * computation.
   */
  boolean logsGiven;

  /** Integer reflecting the current highest place ID. Needed for malleability */
  AtomicInteger mallHighestPlaceID;

  /** Flag used to signal that this place will be shutdown because of malleability. */
  AtomicBoolean mallShutdown;

  /** Random instance used to decide the victims of random steals. */
  Random random;

  /**
   * Instance in which the result of the computation performed at this place is going to be stored.
   * It is initialized with the given neutral element before the computation starts in method {@link
   * #reset(SerializableSupplier, SerializableSupplier, SerializableSupplier, boolean, List,
   * boolean)}.
   */
  R result;

  /**
   * Places that can establish a lifeline on this place Access is protected by synchronized {@link
   * #lifelineLock}
   */
  volatile int[] REVERSE_LIFELINE;

  /**
   * Flag used to signal the {@link #lifelineAnswerThread()} that it needs to shutdown. Used when
   * the place runs out of work and has established all its lifelines.
   *
   * @see #run(Bag)
   */
  volatile boolean shutdown;

  /**
   * State of this place.
   *
   * <ul>
   *   <li>0 running
   *   <li>-1 stealing
   *   <li>-2 inactive
   *   <li>-3 inactive because malleability will remove this place
   * </ul>
   *
   * <p>This member accesses are protected by synchronized blocks with member {@link #workerBags} as
   * lock provider.
   */
  volatile int state;

  /**
   * Concurrent data structure for worker processes trying to yield. Each worker must poll an
   * available lock from this data structure before using it. This avoids having concurrent workers
   * attempting to yield using the same lock. In practice, the current implementation has a single
   * lock: {@link #workerLock}, allowing a single worker to yield at the time.
   */
  ConcurrentLinkedQueue<Lock> workerAvailableLocks;

  /**
   * Keep tracks of the number of {@link #workerProcess(WorkerBag)} launched on this place. Access
   * is protected by synchronized blocks with {@link #workerBags} as lock provider.
   *
   * <p>Note that the value carried by this variable can be different than the actual number of
   * workers working concurrently. For instance, when a new asynchronous {@link
   * #workerProcess(WorkerBag)} needs to be launched, this variable is incremented <em>before</em>
   * the asynchronous process is launched. Moreover, the {@link #workerProcess(WorkerBag)} can
   * cooperatively yield its thread usage to allow some other asynchronous activities to be
   * performed. Those yields do not change the value of {@link #workerCount} but are tracked
   * separately in class member {@link #logger} with methods {@link PlaceLogger#workerYieldStart()}
   * and {@link PlaceLogger#workerYieldStop()}.
   */
  volatile int workerCount;

  /**
   * Lock instance used by {@link #workerProcess(WorkerBag)} to yield their execution to allow other
   * activities (such as remote steals or lifeline answers) to take place.
   */
  Lock workerLock;

  /**
   * {@link Logger} instance used to gather the {@link PlaceLogger}s of each place and hold runtime
   * information common to the whole computation.
   *
   * @see #getLog()
   */
  private Logger computationLog;

  private SerializableSupplier<B> queueInitializer;

  /** Initializer needed for malleability, when places are added at runtime */
  private SerializableSupplier<R> resultInitializer;

  private SerializableSupplier<B> workerInitializer;

  /** Constructor (package visibility) */
  GLBcomputer() {
    LifelineStrategy ls = null;
    try {
      ls =
          (LifelineStrategy)
              Class.forName(
                      GLBMultiWorkerConfiguration.GLBOPTION_MULTIWORKER_LIFELINESTRATEGY.get())
                  .getDeclaredConstructor()
                  .newInstance();
    } catch (NoSuchMethodException
        | InvocationTargetException
        | InstantiationException
        | IllegalAccessException
        | ClassNotFoundException e) {
      e.printStackTrace();
    }

    lifelineStrategy = ls;

    mallRemovedPlaces = new ConcurrentLinkedQueue<>();

    POOL = (ForkJoinPool) GlobalRuntime.getRuntime().getExecutorService();
    HOME = here();

    LIFELINE = lifelineStrategy.lifeline(HOME.id, places());
    REVERSE_LIFELINE = lifelineStrategy.reverseLifeline(HOME.id, places());

    console.println("LIFELINE=" + Arrays.toString(LIFELINE));
    console.println("REVERSE_LIFELINE=" + Arrays.toString(REVERSE_LIFELINE));

    random = new Random(HOME.id);

    feedInterQueueRequested =
        new AtomicIntegerArray(
            GLBMultiWorkerConfiguration.GLBOPTION_MULTIWORKER_WORKERPERPLACE.get());

    lastPrint = new long[GLBMultiWorkerConfiguration.GLBOPTION_MULTIWORKER_WORKERPERPLACE.get()];
    for (int i = 0; i < lastPrint.length; i++) {
      lastPrint[i] = System.nanoTime();
    }

    lifelineEstablished = new ConcurrentHashMap<>(LIFELINE.length);
    lifelineThieves = new ConcurrentLinkedQueue<>();
    logger = new PlaceLogger(HOME.id);
    workerBags = new ConcurrentLinkedQueue<>();

    lifelineAnswerLock = new Lock();
    workerAvailableLocks = new ConcurrentLinkedQueue<>();
    workerLock = new Lock();
    workerAvailableLocks.add(workerLock);
  }

  public int getTaskCountOfWorkerBags() {
    int tasksInBags = 0;
    for (WorkerBag bag : workerBags) {
      tasksInBags += (int) bag.bag.getCurrentTaskCount();
    }
    return tasksInBags;
  }

  /**
   * Sends the order to all places to gather their results in their {@link #result} member before
   * sending it to place 0. This is done asynchronously, this method will block until all places
   * have completed their {@link #collectResult} method.
   */
  void collectAllResult() {
    try {
      finish(
          () -> {
            for (final Place p : places()) {
              if (!isValidPlace(p.id)) {
                continue;
              }
              asyncAt(p, () -> collectResult());
            }
          });
    } catch (final Throwable t) {
      console.println("Exception caught");
      t.printStackTrace(System.out);
    }
  }

  /**
   * Makes the place gather the results contained by all its bags into its member {@link #result}.
   * If this place ({@link #HOME}) is not place 0, sends the content of the result to place 0 to be
   * merged with all the other results there.
   */
  void collectResult() {
    synchronized (result) { // Synchronized in case this is place 0 and remote
      // results are going to merge in
      for (final WorkerBag wb : workerBags) {
        wb.bag.submit(result);
      }

      // because of mall, results can also be saved in these queues
      result.fold(interPlaceQueue.getResult());
      result.fold(intraPlaceQueue.getResult());
    }

    final R r = result;
    if (HOME.id != 0) {
      asyncAt(
          place(0),
          () -> {
            synchronized (result) { // Synchronized to avoid concurrent
              // merging/gathering on place 0
              result.fold(r);
            }
          });
    }
  }

  /**
   * Computes the given bag and returns the aggregated result of this computation.
   *
   * @param bag the computation to be performed
   * @param initResultSupplier function that provides new empty result instances
   * @param emptyBagSupplier function that provides new empty computation bag instances
   * @return aggregated result of the computation
   */
  public R computeDynamic(
      B bag, SerializableSupplier<R> initResultSupplier, SerializableSupplier<B> emptyBagSupplier) {
    return computeDynamic(bag, initResultSupplier, emptyBagSupplier, emptyBagSupplier);
  }

  /**
   * Computes the given bag and returns the result of the distributed computation.
   *
   * @param work initial work to be processed
   * @param resultInitializer initializer for the result instance
   * @param queueInitializer initializer for the queue used for load balancing purposes
   * @param workerInitializer initializer for the workers bag
   * @return instance of type R containing the result of the distributed computation
   */
  public R computeDynamic(
      B work,
      SerializableSupplier<R> resultInitializer,
      SerializableSupplier<B> queueInitializer,
      SerializableSupplier<B> workerInitializer) {
    // We reset every place
    final long initStart = System.nanoTime();
    this.resultInitializer = resultInitializer;
    this.queueInitializer = queueInitializer;
    this.workerInitializer = workerInitializer;
    resetAll(false);

    // We set the malleable handler to `this` if in malleable mode
    if (Configuration.CONFIG_APGAS_ELASTIC.get().equals(Configuration.APGAS_ELASTIC_MALLEABLE)) {
      defineMalleableHandler(this);
    }

    // We launch the computation
    final long start = System.nanoTime();
    workerCount = 1;
    state = 0;

    try {
      finish(
          () -> {
            // Dirty Fix: defineEvolvingHandler must be called inside finish,
            // otherwise, tasks could be lost when shrinking.
            // We set the evolving handler to `this` and
            // choose a task-based load if in evolving mode.
            if (Configuration.CONFIG_APGAS_ELASTIC
                .get()
                .equals(Configuration.APGAS_ELASTIC_EVOLVING)) {
              if (Configuration.CONFIG_APGAS_EVOLVING_MODE.get().equals("cpu")) {
                GetCpuLoad load = new GetCpuLoad();
                defineEvolvingHandler(this, load);
              }
              if (Configuration.CONFIG_APGAS_EVOLVING_MODE.get().equals("task")) {
                GetTaskLoad load = new GetTaskLoad();
                load.setGLBcomputer(this);
                defineEvolvingHandler(this, load);
              }
            }

            run(work);
          });
    } catch (final Throwable t) {
      console.println("Exception caught");
      t.printStackTrace(System.out);
    }
    console.println("after finish run, workerCount=" + workerCount);

    if (Configuration.CONFIG_APGAS_ELASTIC.get().equals(Configuration.APGAS_ELASTIC_MALLEABLE)
        || Configuration.CONFIG_APGAS_ELASTIC.get().equals(Configuration.APGAS_ELASTIC_EVOLVING)) {
      disableElasticCommunicator();
    }

    final long computationFinish = System.nanoTime();
    // We gather the result back into place 0
    collectAllResult();
    final long resultGathering = System.nanoTime();

    // Preparation for method getLog if it is called
    computationLog.setTimings(initStart, start, computationFinish, resultGathering);
    return result;
  }

  public R computeStatic(
      SerializableSupplier<R> resultInitializer,
      SerializableSupplier<B> queueInitializer,
      SerializableSupplier<B> workerInitializer) {
    // We reset every place
    final long initStart = System.nanoTime();
    this.resultInitializer = resultInitializer;
    this.queueInitializer = queueInitializer;
    this.workerInitializer = workerInitializer;
    resetAll(true);

    // We set the malleable handler to `this` if in malleable mode
    if (Configuration.CONFIG_APGAS_ELASTIC.get().equals(Configuration.APGAS_ELASTIC_MALLEABLE)) {
      defineMalleableHandler(this);
    }

    // We launch the computation
    final long start = System.nanoTime();

    // run(null) will do:
    // workerCount = 1;
    // state = 0;
    try {
      finish(
          () -> {
            // Dirty Fix: defineEvolvingHandler must be called inside finish,
            // otherwise, tasks could be lost when shrinking.
            // We set the evolving handler to `this` and
            // choose a task-based load if in evolving mode.
            if (Configuration.CONFIG_APGAS_ELASTIC
                .get()
                .equals(Configuration.APGAS_ELASTIC_EVOLVING)) {
              if (Configuration.CONFIG_APGAS_EVOLVING_MODE.get().equals("cpu")) {
                GetCpuLoad load = new GetCpuLoad();
                defineEvolvingHandler(this, load);
              }
              if (Configuration.CONFIG_APGAS_EVOLVING_MODE.get().equals("task")) {
                GetTaskLoad load = new GetTaskLoad();
                load.setGLBcomputer(this);
                defineEvolvingHandler(this, load);
              }
            }

            for (final Place p : places()) {
              asyncAt(
                  p,
                  () -> {
                    run(null);
                  });
            }
          });
    } catch (final Throwable t) {
      console.println("Exception caught");
      t.printStackTrace(System.out);
    }
    console.println("after finish run, workerCount=" + workerCount);

    if (Configuration.CONFIG_APGAS_ELASTIC.get().equals(Configuration.APGAS_ELASTIC_MALLEABLE)
        || Configuration.CONFIG_APGAS_ELASTIC.get().equals(Configuration.APGAS_ELASTIC_EVOLVING)) {
      disableElasticCommunicator();
    }

    final long computationFinish = System.nanoTime();
    // We gather the result back into place 0
    collectAllResult();
    final long resultGathering = System.nanoTime();

    // Preparation for method getLog if it is called
    computationLog.setTimings(initStart, start, computationFinish, resultGathering);
    return result;
  }

  /**
   * Method called on this place when a victim of steal is answering and providing some loot.
   *
   * <p>This method checks if the current place is "alive", meaning if it has any workers.
   *
   * <ul>
   *   <li>If there are active workers, the loot is merged into the {@link #intraPlaceQueue}.
   *   <li>If no workers exist and the place is performing some steals, the loot is placed in the
   *       first workerBag of collection {@link #workerBags} before unlocking the "main" {@link
   *       #run(Bag)} thread progress which is either stealing from random victims or stealing from
   *       lifelines. This will cause it to resume computation by spawning a first worker (with the
   *       merged loot) as part of the {@link #run(Bag)} routine.
   *   <li>If the place is inactive, method {@link #run(Bag)} is launched with the loot as
   *       parameter.
   * </ul>
   *
   * @param victim the id from place sending the loot or {@code -1} if it is a random steal
   * @param loot the work that was stolen by this place
   * @param waitLatch CountDownLatch to notify a waiting thief, can be null!
   */
  void deal(int victim, B loot, GlobalRef<CountDownLatch> waitLatch) {
    if (intraPlaceQueue == null) {
      console.println("intraPlaceQueue " + intraPlaceQueue);
    }
    workerLock.unblock();
    if (victim < 0) {
      logger.stealsSuccess.incrementAndGet();
    } else {
      logger.lifelineStealsSuccess.incrementAndGet();
      lifelineEstablished.put(victim, false);
    }

    boolean startNewWorker = false;
    synchronized (workerBags) {
      switch (state) {
        case 0:
          /*
           * There are workers on the place -> we merge the loot into the intra-place
           * queue
           */
          synchronized (intraPlaceQueue) {
            intraPlaceQueue.merge(loot);
            logger.intraQueueFed.incrementAndGet();
            intraQueueEmpty = false;
          }
          if (waitLatch != null) {
            waitLatch.get().countDown();
          }
          /*
           * Placing this return instruction allows us to put the run call out of the
           * synchronized block without having to use an extra condition.
           */
          return;

        case -1:

          /*
           * If the place is currently stealing, the bag is given to the head of
           * collection workerBags. This head is the one which is going to be run when the
           * stealing stops and a new workerProcess is spawned in method run
           */
          workerBags.peek().bag.merge(loot);
          state = 0; // Back into a running state
          workerCount = 1;

          if (waitLatch != null) {
            waitLatch.get().countDown();
          }
          return;
        case -2:
          // There are no workers on this place, it needs to be waken up
          workerCount = 1;
          state = 0; // Possible concurrent lifeline answers will not spawn
          // a new run method as this signals that this place is now "alive"
          startNewWorker = true;
          break;

        case -3:
          // This place will be removed caused by malleability, thus the loot is
          // sent back
          System.out.println(here() + " case -3: sent loot back to " + victim);
          final Place victimP;
          if (victim < 0) {
            victimP = place(-victim - 1);
          } else {
            victimP = place(victim);
          }
          try {
            asyncAt(
                victimP,
                () -> {
                  deal(-1, loot, null);
                });
          } catch (final Throwable t) {
            t.printStackTrace(System.out);
          }
      }
    }

    if (waitLatch != null) {
      waitLatch.get().countDown();
    }

    if (startNewWorker) {
      console.println("start new worker, workerCount=" + workerCount);
      async(
          () -> {
            run(loot);
          });
    }
  }

  /**
   * Gives back the log of the previous computation.
   *
   * @return the {@link PlaceLogger} instance of this place
   */
  public Logger getLog() {
    if (!logsGiven) {
      try {
        finish(
            () -> {
              for (final Place p : places()) {
                if (!isValidPlace(p.id)) {
                  continue;
                }
                asyncAt(
                    p,
                    () -> {
                      final PlaceLogger l = logger;
                      asyncAt(
                          place(0),
                          () -> {
                            computationLog.addPlaceLogger(l);
                          });
                    });
              }
            });
      } catch (final Throwable t) {
        console.println("Exception caught");
        t.printStackTrace(System.out);
      }
      logsGiven = true;
    }
    return computationLog;
  }

  private boolean isValidPlace(int id) {
    boolean validPlace = true;
    synchronized (lifelineLock) {
      if (id < 0 || isDead(place(id)) || mallRemovedPlaces.contains(id)) {
        validPlace = false;
      }
    }
    return validPlace;
  }

  private boolean isValidRemotePlace(int id) {
    if (HOME.id == id) {
      return false;
    }
    return isValidPlace(id);
  }

  /**
   * Activity spawned by method {@link #run(Bag)} to answer lifelines that were not able to be
   * answered straight away.
   *
   * <p>This process yields until a lifeline answer is signaled as possible or the <em>shutdown</em>
   * is activated by method {@link #run(Bag)}.
   */
  void lifelineAnswerThread() {
    logger.lifelineAnswerThreadStarted();

    do {
      /*
       * 1. Yield
       */
      logger.lifelineAnswerThreadInactive();
      try {
        ForkJoinPool.managedBlock(lifelineAnswerLock);
      } catch (final InterruptedException e) {
        // Should not happen since the implementation of Lock does not throw
        // InterruptedException
        e.printStackTrace();
      }

      // for mall
      if (mallShutdown.get()) {
        console.println("lifelineAnswerThread returns because of mall");
        lifelineAnswerThreadExited = true;
        shutdown = true;
        return;
      }

      synchronized (lifelineLock) {
        lifelineToAnswer = false;
        workerLock.unblock();
        logger.lifelineThreadWokenUp++;

        logger.lifelineAnswerThreadActive();

        /*
         * 2. Answer lifelines
         */
        while (!lifelineThieves.isEmpty()) {
          B loot;
          synchronized (intraPlaceQueue) {
            if (interQueueEmpty) {
              break;
            }
            console.println(
                "BEFORE performing interPlaceQueue.split(true) and interPlaceQueue should now contain tasks empty, interPlaceQueue.isEmpty="
                    + interQueueEmpty
                    + ", interPlaceQueue.size()="
                    + interPlaceQueue.getCurrentTaskCount()
                    + " loot.size()=0");
            loot = interPlaceQueue.split(true);
            logger.interQueueSplit.incrementAndGet();
            interQueueEmpty = interPlaceQueue.isEmpty();
            console.println(
                "AFTER performing interPlaceQueue.split(true) and interPlaceQueue should now be empty, interPlaceQueue.isEmpty="
                    + interQueueEmpty
                    + ", interPlaceQueue.size()="
                    + interPlaceQueue.getCurrentTaskCount()
                    + " loot.size()="
                    + loot.getCurrentTaskCount());
          }
          // Send the loot
          final int h = HOME.id;
          final int lifelineThief = lifelineThieves.poll();
          console.println(
              "sends loot to lifeline="
                  + lifelineThief
                  + ", loot.size="
                  + loot.getCurrentTaskCount());
          try {
            asyncAt(
                place(lifelineThief),
                () -> {
                  // null because of nobody waits because of delayed lifeline
                  deal(h, loot, null);
                });
          } catch (final Throwable t) {
            t.printStackTrace(System.out);
            console.printlnAlways(
                "could not sent to "
                    + lifelineThief
                    + "/"
                    + place(lifelineThief)
                    + " merge the loot back into interPlaceQueue. However, this should never happen!!!!");
            this.interPlaceQueue.merge(loot);
          }
          logger.lifelineStealsSuffered.incrementAndGet();
        }
        if (interQueueEmpty) {
          requestInterQueueFeed();
        }
      }

      /*
       * 3. Until "shutdown" is activated, repeat from step 1.
       */
    } while (!shutdown);

    console.println("lifelineAnswerThread shutdown");

    logger.lifelineAnswerThreadEnded();
    lifelineAnswerThreadExited = true;
  }

  /**
   * Sub-routine of methods {@link #performRandomSteals()} and {@link #performLifelineSteals()}.
   * Gets some loot from the inter/intra place queues and performs the status updates on these
   * queues as necessary.
   *
   * @return some loot to be sent to thieves
   */
  B loot() {
    B loot = null;
    // Quick check on the other queue
    if (!interQueueEmpty) {
      synchronized (intraPlaceQueue) {
        if (!interQueueEmpty) {
          loot = interPlaceQueue.split(true);
          logger.interQueueSplit.incrementAndGet();
          interQueueEmpty = interPlaceQueue.isEmpty(); // Update flag
        }
      }
      if (interQueueEmpty) {
        requestInterQueueFeed();
      }
    }

    return loot;
  }

  private void notifyWaitingThief(int thief, GlobalRef<CountDownLatch> waitLatch) {
    if (waitLatch == null) {
      return;
    }
    try {
      uncountedAsyncAt(
          place(thief),
          () -> {
            waitLatch.get().countDown();
          });
    } catch (final Throwable t) {
      t.printStackTrace(System.out);
    }
  }

  /**
   * Part of the {@link #run(Bag)} procedure. Performs lifeline steals until either of two things
   * happen:
   *
   * <ul>
   *   <li>Some work is received through a lifeline
   *   <li>All lifelines have been established
   * </ul>
   *
   * @return {@code true} if some work is received during the method's execution, {@code false}
   *     otherwise
   */
  boolean performLifelineSteals() {
    console.println("places()=" + places() + ", mallHighestPlaceID=" + mallHighestPlaceID.get());

    if (mallShutdown.get()) {
      console.println("cancel because of mall");
      return false;
    }

    synchronized (lifelineLock) {
      for (int i = 0; i < LIFELINE.length; i++) {

        final int lifelineID = LIFELINE[i];

        boolean isLifelineEstablished = lifelineEstablished.getOrDefault(lifelineID, false);

        if (!isLifelineEstablished
            && isValidRemotePlace(lifelineID)) { // We check if the lifeline was
          // previously established or not and if it is a valid place
          logger.lifelineStealsAttempted.incrementAndGet();
          lifelineEstablished.put(lifelineID, true);

          final int h = HOME.id;
          final Place lifeline = place(lifelineID);
          console.println(
              "sends steal request to lifeline=" + lifeline + ", workerCount=" + workerCount);
          final GlobalRef<CountDownLatch> waitLatch = new GlobalRef<>(new CountDownLatch(1));
          try {
            uncountedAsyncAt(
                lifeline,
                () -> {
                  steal(h, waitLatch);
                });
          } catch (final Throwable t) {
            t.printStackTrace(System.out);
          }

          try {
            final boolean await = waitLatch.get().await(5, TimeUnit.SECONDS);
            if (!await) {
              console.println(
                  "TIMEOUT: waitLatch.get().await(5, TimeUnit.SECONDS), lifeline=" + lifelineID);
            }
          } catch (final InterruptedException e) {
            e.printStackTrace();
          }
        }

        // Checks if some work was received
        synchronized (workerBags) {
          if (state == 0) { // State is put back to 0 in lifelineDeal when an
            // answer is received
            return true;
          }
          if (i == LIFELINE.length - 1) {
            // If all lifelines were established and still no positive answer was
            // received
            state = -2;
          }
        }
      }
      return false;
    }
  }

  /**
   * Part of the {@link #run(Bag)} procedure. Performs random steals until one of two things happen:
   *
   * <ul>
   *   <li>Some work is received by this place, either by a previously established lifeline or
   *       through a random steal initiated by this method
   *   <li>The maximum number of steals on a random place is reached
   * </ul>
   *
   * <p>These two events are not mutually exclusive, it can happen that the maximum number of random
   * steals was reached and that some work was received by this place concurrently.
   *
   * @return {@code true} if some work is received during the method's execution, {@code false}
   *     otherwise
   */
  boolean performRandomSteals() {
    console.println("places()=" + places() + ", mallHighestPlaceID=" + mallHighestPlaceID.get());
    if (places().size() < 2 || places().size() > (mallHighestPlaceID.get() + 1)) {
      return false;
    }

    if (mallShutdown.get()) {
      console.println("Cancel because of mall"); // victim is shutting down.
      return false;
    }

    for (int i = 0; i < GLBMultiWorkerConfiguration.GLBOPTION_MULTIWORKER_W.get(); i++) {
      logger.stealsAttempted.incrementAndGet();
      // Choose a victim
      int victimID = -1;
      final int randomTries = 20;
      for (int r = 0; r < randomTries; r++) {
        victimID = random.nextInt(mallHighestPlaceID.get() + 1);
        if (isValidRemotePlace(victimID)) {
          break;
        }
      }
      if (!isValidRemotePlace(victimID)) {
        System.err.println("No random victim found, return");
        return false;
      }
      final Place victim = place(victimID);
      console.println("Sends steal request to random=" + victim + ", workerCount=" + workerCount);
      final int h = HOME.id;
      final GlobalRef<CountDownLatch> waitLatch = new GlobalRef<>(new CountDownLatch(1));
      try {
        uncountedAsyncAt(victim, () -> steal(-h - 1, waitLatch));
      } catch (final Throwable t) {
        t.printStackTrace(System.out);
      }

      try {
        final boolean await = waitLatch.get().await(5, TimeUnit.SECONDS);
        if (!await) {
          console.println("TIMEOUT: waitLatch.get().await(5, TimeUnit.SECONDS), random=" + victim);
        }
      } catch (final InterruptedException e) {
        e.printStackTrace();
      }

      // Checks if some work was received
      synchronized (workerBags) {
        if (state == 0) { // State is put back to 0 when an answer is received
          return true;
        }
      }
    }

    return false;
  }

  /** Nothing in particular needs to be performed before a grow order is put into place. */
  @Override
  public void preGrow(int nbPlaces) {
    long before = System.nanoTime();
    System.err.println("preGrow called");
    long preGrowTimeGLB = System.nanoTime() - before;
    Constructs.sendToScheduler("preGrowTimeGLB;" + nbPlaces + ";" + preGrowTimeGLB);
  }

  /**
   * After new places were spawned, new GlbComputer instances need to be instantiated on the new
   * places. After that, these new places are integrated into the lifeline network so that they can
   * start stealing work from the currently running places.
   */
  @Override
  public void postGrow(
      int nbPlaces, List<? extends Place> continuedPlaces, List<? extends Place> newPlaces) {
    ConsolePrinter.getInstance().printlnAlways("postGrow called");
    long before = System.nanoTime();
    final GlobalID globalID = getId(this);
    final SerializableSupplier<R> _resultInitializer = resultInitializer;
    final SerializableSupplier<B> _queueInitializer = queueInitializer;
    final SerializableSupplier<B> _workerInitializer = workerInitializer;

    final ArrayList<Integer> newPlaceIds = new ArrayList<>();
    for (final Place p : newPlaces) {
      newPlaceIds.add(p.id);
    }

    // Prepare new GLBcomputer objects on the new places
    final GlobalRef<CountDownLatch> newPlacesCount =
        new GlobalRef<>(new CountDownLatch(newPlaces.size()));

    for (final Place newPlace : newPlaces) {
      try {
        immediateAsyncAt(
            newPlace,
            () -> {
              try {
                ConsolePrinter.getInstance().printlnAlways("Initializing GLBcomputer on " + here());
                // Create a GLBcomputer instance and bind it to the existing GlobalId
                final GLBcomputer<R, B> newComputer = new GLBcomputer<>();
                newComputer.id = globalID;
                globalID.putHere(newComputer);

                // Set the new GLBComputer for loadEvaluation
                GetTaskLoad load = new GetTaskLoad();
                load.setGLBcomputer(newComputer);
                // Reset it so that it prepares the necessary data structures to start stealing
                newComputer.reset(
                    _resultInitializer,
                    _queueInitializer,
                    _workerInitializer,
                    true,
                    newPlaceIds,
                    false);

                immediateAsyncAt(
                    newPlacesCount.home(),
                    () -> {
                      newPlacesCount.get().countDown();
                    });

              } catch (final Exception e) {
                System.err.println("Exception when initializing new GLBcomputer on " + here());
                e.printStackTrace();
                throw e;
              }
            });
      } catch (final Exception e) {
        System.err.println("Exception submitting the tasks to setup new places");
        e.printStackTrace();
        throw e;
      }
    }

    try {
      newPlacesCount.get().await();
    } catch (final InterruptedException e) {
      e.printStackTrace();
    }

    final GlobalRef<CountDownLatch> continuedPlacesCount =
        new GlobalRef<>(new CountDownLatch(places().size()));

    for (final Place p : places()) {
      // Recalculate the lifeline for the continued places
      try {
        immediateAsyncAt(
            p,
            () -> {
              try {
                ConsolePrinter.getInstance().printlnAlways("Re-calculating lifelines on " + here());
                recalculateLifelinesAfterGrow(newPlaces);
              } catch (final Exception e) {
                System.err.println("Exception when refreshing lifelines on " + here());
                e.printStackTrace();
                throw e;
              }

              immediateAsyncAt(
                  continuedPlacesCount.home(),
                  () -> {
                    continuedPlacesCount.get().countDown();
                  });
            });
      } catch (final Exception e) {
        System.err.println(
            "Exception submitting the tasks to refresh lifelines on continued places");
        e.printStackTrace();
        throw e;
      }
    }

    try {
      continuedPlacesCount.get().await();
    } catch (final InterruptedException e) {
      e.printStackTrace();
    }

    long postGrowTimeGLB = System.nanoTime() - before;
    sendToScheduler("postGrowTimeGLB;" + newPlaces.size() + ";" + postGrowTimeGLB);
    System.err.println("Malleable growth completed");
  }

  /**
   * Malleable preShrink When a shrink order is received, the places chosen to be removed are cut
   * off from the lifeline network and their work is sent back to remaining places. Their
   * intermediary result also needs to be transferred back to remaining places to be merged.
   *
   * <p>In this implementation, we always choose the places with the largest IDs to be released.
   */
  @Override
  public List<Place> preShrink(int nbPlaces) {
    ConsolePrinter.getInstance().printlnAlways("preShrink called");
    // Choose the places that are going to be shut down
    final int currentNumberPlaces = places().size();
    final int numberPlacesAfterShutdown = currentNumberPlaces - nbPlaces;
    final ArrayList<Place> toStop = new ArrayList<>(nbPlaces);
    final ArrayList<Place> toContinue = new ArrayList<>(numberPlacesAfterShutdown);
    int largestIDRemaining = -1;

    int k = 0;
    for (final Place p : places()) {
      k++;
      if (k <= numberPlacesAfterShutdown) {
        // This place is kept
        toContinue.add(p);
        if (p.id > largestIDRemaining) {
          largestIDRemaining = p.id;
        }
      } else {
        // This place is removed
        toStop.add(p);
      }
    }
    return preShrink(toStop);
  }

  /**
   * Evolving preShrink When a shrink order is triggered, the places chosen to be removed are cut
   * off from the lifeline network and their work is sent back to remaining places. Their
   * intermediary result also needs to be transferred back to remaining places to be merged.
   *
   * <p>In this variant the place to shrink is known, so it is given as parameter.
   */
  @Override
  public List<Place> preShrink(ArrayList<Place> placesToShrink) {
    System.err.println("preShrink called");
    long before = System.nanoTime();

    // Choose the places that are going to be shut down
    final int currentNumberPlaces = places().size();
    final int numberPlacesAfterShutdown =
        currentNumberPlaces - placesToShrink.size(); // -1 for this version
    int largestIDRemaining = -1;

    final ArrayList<Place> toContinue = new ArrayList<>(places());
    toContinue.removeAll(placesToShrink);

    for (final Place p : toContinue) {
      if (p.id > largestIDRemaining) {
        largestIDRemaining = p.id;
      }
    }

    final int largestId = largestIDRemaining;

    final GlobalRef<CountDownLatch> lifelineCount =
        new GlobalRef<>(new CountDownLatch(toContinue.size()));

    for (final Place p : toContinue) {
      // For the places to be removed: stop stealing, send work and intermediary
      // results away to other places
      immediateAsyncAt(
          p,
          () -> {
            recalculateLifelinesBeforeShrink(largestId, toContinue, placesToShrink);
            immediateAsyncAt(
                lifelineCount.home(),
                () -> {
                  lifelineCount.get().countDown();
                });
          });
    }
    try {
      lifelineCount.get().await();
    } catch (final InterruptedException e) {
      e.printStackTrace();
    }

    final GlobalRef<CountDownLatch> toStopCount =
        new GlobalRef<>(new CountDownLatch(placesToShrink.size()));

    for (final Place p : placesToShrink) {
      // For the places that remain, remove the lifelines to places to remove
      asyncAt(
          p,
          () -> {
            transferWorkBeforeShutdown(placesToShrink, toStopCount);
          });
    }

    try {
      toStopCount.get().await();
    } catch (final InterruptedException e) {
      e.printStackTrace();
    }

    long preShrinkTimeGLB = System.nanoTime() - before;
    Constructs.sendToScheduler(
        "preShrinkTimeGLB;" + placesToShrink.size() + ";" + preShrinkTimeGLB);
    return placesToShrink;
  }

  /**
   * After the reduction in the number of running places, nothing in particular needs to be
   * performed
   */
  @Override
  public void postShrink(int nbPlaces, List<? extends Place> removedPlaces) {
    long before = System.nanoTime();
    System.err.println("postShrink called");
    long postShrinkTimeGLB = System.nanoTime() - before;
    Constructs.sendToScheduler(
        "postShrinkTimeGLB;" + removedPlaces.size() + ";" + postShrinkTimeGLB);
  }

  /**
   * Procedure used to recalculate the lifeline network when places are added and/or removed from
   * the runtime. This method is called by {@link #postGrow(int, List, List)} and {@link
   * #preShrink(int)} on each place that is continuing execution.
   *
   * @param addedPlaces places that were added as a result of the previous malleable operation (may
   *     be empty)
   */
  private void recalculateLifelinesAfterGrow(List<? extends Place> addedPlaces) {
    final List<? extends Place> allPlaces = places();
    final int nbPlaces = allPlaces.size();
    boolean startLifelineThread = false;

    synchronized (lifelineLock) {
      mallHighestPlaceID.set(allPlaces.get(nbPlaces - 1).id);
      LIFELINE = lifelineStrategy.lifeline(HOME.id, allPlaces);
      REVERSE_LIFELINE = lifelineStrategy.reverseLifeline(HOME.id, allPlaces);

      console.println("New LIFELINE=" + Arrays.toString(LIFELINE));
      console.println("New REVERSE_LIFELINE=" + Arrays.toString(REVERSE_LIFELINE));

      for (final int i : LIFELINE) {
        if (!lifelineEstablished.contains(i)) {
          lifelineEstablished.put(i, false);
        }
      }
      console.println("lifelineEstablished=" + lifelineEstablished);

      // Establish lifeline on current place instead of newly added places
      for (final int i : REVERSE_LIFELINE) {
        if (addedPlaces.contains(place(i))) {
          lifelineThieves.add(i);
          startLifelineThread = true;
        }
      }

      console.println("lifelineThieves=" + lifelineThieves);

      if (startLifelineThread) {
        lifelineAnswerLock.unblock(); // unblocking lifeline answer thread
        lifelineToAnswer = true;
      }
    }
  }

  /**
   * Routine called on the places that will remain with the runtime just before the shrink is
   * performed. These places will re-compute their lifelines and discard any lifeline connecting to
   * a place which is about to be removed as part of the shrink operation.
   *
   * @param highestID the largest place ID for the places that remain in the runtime
   * @param allRemainingPlaces the places that will still be participating in the runtime after the
   *     shrink operation
   * @param removedPlaces the list of places to be removed from the runtime
   */
  private void recalculateLifelinesBeforeShrink(
      int highestID, List<Place> allRemainingPlaces, List<Place> removedPlaces) {
    synchronized (lifelineLock) {
      mallHighestPlaceID.set(highestID);
      LIFELINE = lifelineStrategy.lifeline(HOME.id, allRemainingPlaces);
      REVERSE_LIFELINE = lifelineStrategy.reverseLifeline(HOME.id, allRemainingPlaces);

      console.println("New LIFELINE=" + Arrays.toString(LIFELINE));
      console.println("New REVERSE_LIFELINE=" + Arrays.toString(REVERSE_LIFELINE));

      console.println("before lifelineThieves=" + lifelineThieves);
      for (final Place p : removedPlaces) {
        mallRemovedPlaces.add(p.id);
        // sometimes lifelineThieves contains duplicated entries which should never happen
        while (lifelineThieves.contains(p.id)) {
          lifelineThieves.remove(p.id);
        }
        lifelineEstablished.put(
            p.id, true); // hack preventing the re-establishment of a lifeline on a place
        // about to stop, the lifeline is actually not established
      }
      console.println("after lifelineThieves=" + lifelineThieves);
      console.println("removedMallPlaces=" + mallRemovedPlaces);

      for (final int i : LIFELINE) {
        if (!lifelineEstablished.contains(i)) {
          lifelineEstablished.put(i, false);
        }
      }
      console.println("lifelineEstablished=" + lifelineEstablished);

      console.println("lifelineThieves=" + lifelineThieves);
    }
  }

  /**
   * Sets all the boolean in array {@link #feedInterQueueRequested} to {@code true}. Is called when
   * it is noticed that a member {@link #interPlaceQueue} is empty. This will make the workers send
   * part of their work this queue.
   */
  void requestInterQueueFeed() {
    for (int i = 0; i < feedInterQueueRequested.length(); i++) {
      feedInterQueueRequested.set(i, 1);
    }
  }

  /**
   * Resets the local GLBcomputer instance to a ready to compute state in preparation for the next
   * computation. An instance of the result which the computation is going to produce is kept aside
   * for later use.
   *
   * <p>
   *
   * @param resultInitSupplier supplier of empty result instance
   * @param queueInitializer supplier of empty queues for load balancing purposes
   * @param workerInitializer supplier of empty bag for workers
   * @param mall indicates if it was called as a result of a malleable change
   * @param addedPlaces list of places that were added to the runtime as part of a malleable grow
   *     operation
   * @param staticTasks indicates if initial staticTasks available or not
   */
  void reset(
      SerializableSupplier<R> resultInitSupplier,
      SerializableSupplier<B> queueInitializer,
      SerializableSupplier<B> workerInitializer,
      final boolean mall,
      final List<Integer> addedPlaces,
      final boolean staticTasks) {

    // Resetting the logger
    logger = new PlaceLogger(HOME.id);
    logsGiven = false;
    computationLog = new Logger();

    // Resetting the field used to keep the result
    result = resultInitSupplier.get();

    resultInitializer = resultInitSupplier;
    this.queueInitializer = queueInitializer;
    this.workerInitializer = workerInitializer;

    // Resetting flags
    lifelineAnswerLock.reset();
    workerLock.reset();
    interQueueEmpty = true;
    intraQueueEmpty = true;
    lifelineAnswerThreadExited = true;
    state = -2;
    shutdown = false;
    mallShutdown = new AtomicBoolean(false);
    mallHighestPlaceID = new AtomicInteger(places().get(places().size() - 1).id);

    // Removing old bags and getting some new ones
    workerBags.clear();
    for (int i = 0;
        i < GLBMultiWorkerConfiguration.GLBOPTION_MULTIWORKER_WORKERPERPLACE.get();
        i++) {
      // We put as many new bags as there are possible concurrent workers called by
      // computeDynamic: bags are empty
      // called by computeStatic: bags already contain tasks
      final WorkerBag workerBag = new WorkerBag(i, workerInitializer);
      if (staticTasks) {
        workerBag.initStaticTasks();
      }
      workerBags.add(workerBag);
      feedInterQueueRequested.set(i, 1);
    }

    // Resetting the queues
    // important: currently, this must be done after the initialization of the
    // workerbags because of computeStatic (BC)
    interPlaceQueue = queueInitializer.get();
    intraPlaceQueue = queueInitializer.get();

    // We reset the established lifelines trackers
    final boolean lifelinesOn = (HOME.id != 0) && !staticTasks;
    for (final int i : LIFELINE) {
      if (!isValidRemotePlace(i)) {
        continue;
      }
      boolean setLifeline = lifelinesOn;
      if (mall && addedPlaces != null && addedPlaces.contains(i)) {
        setLifeline = false;
      }
      lifelineEstablished.put(i, setLifeline);
    }
    console.println("lifelineEstablished=" + lifelineEstablished);

    // We do not want to open lifelines (lifelineEstablished.put(i) should always be
    // false
    if (staticTasks) {
      return;
    }

    // We establish lifelines on this place for initial work-stealing conditions
    // This is not done in this place is being newly created as part of a malleable
    // grow operation.
    if (!mall) {
      for (final int i : REVERSE_LIFELINE) {
        if (i == 0 || !isValidPlace(i)) {
          // Avoid establishing lifelines back to place 0 as this place already has all
          // the work when starting a new computation
          continue;
        }
        lifelineThieves.add(i);
      }
    }
    console.println("lifelineThieves=" + lifelineThieves);
  }

  /**
   * Resets all instances of GLBcomputer in the system.
   *
   * <p>Calls method {@link #reset(SerializableSupplier, SerializableSupplier, SerializableSupplier,
   * boolean, List, boolean)} on all places in the system. The tasks are performed asynchronously.
   * The method returns when all the instances on each place have completed their reset.
   */
  private void resetAll(final boolean staticTasks) {

    final SerializableSupplier<R> _resultInitializer = resultInitializer;
    final SerializableSupplier<B> _queueInitializer = queueInitializer;
    final SerializableSupplier<B> _workerInitializer = workerInitializer;
    try {
      finish(
          () -> {
            for (final Place p : places()) {
              asyncAt(
                  p,
                  () ->
                      reset(
                          _resultInitializer,
                          _queueInitializer,
                          _workerInitializer,
                          false,
                          null,
                          staticTasks));
            }
          });
    } catch (final Throwable t) {
      console.println("Exception caught");
      t.printStackTrace(System.out);
    }
  }

  /**
   * Main procedure of a place.
   *
   * <p>Spawns the first worker thread (which will in turn recursively spawn other worker threads).
   * When all workers run out of work, attempts to steal work from remote hosts.
   *
   * @param b the initial bag to compute.
   */
  void run(B b) {
    // Dirty fixing "tasks could be lost when shrinking"
    if (Configuration.CONFIG_APGAS_ELASTIC.get().equals(Configuration.APGAS_ELASTIC_EVOLVING)) {
      final int _myid = here().id;
      immediateAsyncAt(
          place(0),
          () -> {
            GlobalRuntimeImpl.getRuntime().EVOLVING.placeActiveRef.get().put(_myid, true);
          });
    }

    console.println("New worker starts, workerCount=" + workerCount);

    // Wait until the previous lifeline exited
    while (!lifelineAnswerThreadExited) {
      console.println("while (!lifelineAnswerThreadExited)");
      try {
        TimeUnit.MILLISECONDS.sleep(500);
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
    }
    console.println("while (!lifelineAnswerThreadExited) After");

    // Reset the flags and the locks
    lifelineAnswerThreadExited = false;
    shutdown = false;
    workerLock.reset();
    lifelineAnswerLock.reset();

    // Spawn the lifeline answer thread
    async(
        () -> {
          lifelineAnswerThread();
        });

    // Prepare the first worker to process the work given as parameter
    if (b != null) { // dynamic computation
      workerBags.peek().bag.merge(b);
    }

    final boolean[] isStaticInner = new boolean[] {(b == null)};
    do {
      do {
        try {
          finish(
              () -> {
                if (!isStaticInner[0]) { // called from computeDynamic and deal
                  // Spawn a first worker (which will spawn the others)
                  final WorkerBag workerBag = workerBags.poll();
                  async(
                      () -> {
                        workerProcess(workerBag); // Working
                      });
                } else if (b == null && isStaticInner[0]) {
                  state = 0;
                  // Spawn all workers
                  // (called from computeStatic)
                  while (!workerBags.isEmpty()) {
                    final WorkerBag wb = workerBags.poll();
                    if (wb == null) {
                      continue;
                    }
                    synchronized (workerBags) {
                      workerCount++;
                      console.println(
                          "starting new worker because of staticTasks, workerCount=" + workerCount);
                    }
                    async(() -> workerProcess(wb));
                  }
                  isStaticInner[0] = false;
                }
              });
        } catch (final Throwable t) {
          console.println("Exception caught");
          t.printStackTrace(System.out);
        }
        // All the workers have stopped, this place does not have any work and
        // will now attempt to steal some from other places
      } while (performRandomSteals());
    } while (performLifelineSteals());

    if (Configuration.CONFIG_APGAS_ELASTIC.get().equals(Configuration.APGAS_ELASTIC_EVOLVING)) {
      final int _myid = here().id;
      immediateAsyncAt(
          place(0),
          () -> {
            GlobalRuntimeImpl.getRuntime().EVOLVING.placeActiveRef.get().put(_myid, false);
          });
    }

    console.println("going to sleep, workerCount=" + workerCount);

    // Shutdown the lifelineAnswerThread and the tuner thread
    shutdown = true; // Flag used to signal to the activities they need to
    // terminate
    logger.lifelineAnswerThreadHold();
    lifelineAnswerLock.unblock(); // Unblocks the progress of the lifeline
    // answer thread
  }

  /**
   * Method called asynchronously by a thief to steal work from this place.
   *
   * @param thief the integer id of the place performing the steal, or `(-id - 1)` if this is a
   *     random steal
   * @param waitLatch
   */
  synchronized void steal(int thief, GlobalRef<CountDownLatch> waitLatch) {
    workerLock.unblock();

    final int h = HOME.id;
    final B loot = loot();
    console.println(
        "Received steal request from "
            + thief
            + ", loot().size="
            + (loot == null ? "0" : loot.getCurrentTaskCount())
            + ", workerCount="
            + workerCount);

    if (thief >= 0) {
      // A lifeline is trying to steal some work
      logger.lifelineStealsReceived.incrementAndGet();

      if (loot == null) {
        // Steal does not immediately succeed
        // The lifeline is registered to answer it later.
        lifelineThieves.offer(thief);
        notifyWaitingThief(thief, waitLatch);
      } else {
        logger.lifelineStealsSuffered.incrementAndGet();
        try {
          uncountedAsyncAt(
              place(thief),
              () -> {
                deal(h, loot, waitLatch);
              });
        } catch (final Throwable t) {
          t.printStackTrace(System.out);
        }
      }
    } else {
      // A random thief is trying to steal some work
      logger.stealsReceived.incrementAndGet();
      if (loot != null) {
        logger.stealsSuffered.incrementAndGet();
        try {
          uncountedAsyncAt(
              place(-thief - 1),
              () -> {
                deal(-1, loot, waitLatch);
              });
        } catch (final Throwable t) {
          t.printStackTrace(System.out);
        }
      } else {
        notifyWaitingThief(-thief - 1, waitLatch);
      }
    }
  }

  private void stopWorker(final WorkerBag workerBag, final int newState) {
    synchronized (workerBags) {
      workerBags.add(workerBag);
      workerCount--;
      if (workerCount == 0 && state != -3) {
        state = newState; // No more workers, we are now in stealing mode
      }
      logger.workerStopped();
      workerLock.unblock(); // A yielding worker can be unlocked.
      // As this worker is terminating, its thread
      // will be available for computation.
    }
  }

  /**
   * Routine used to cleanup the GLB runtime on a place removed from the computation as a result of
   * a malleable change in resources.
   *
   * @param toStop list of places that are stopping (and to whom the remaining work cannot be sent
   *     to)
   * @param toStopCount counter used to track the completion of this operation back on place 0. It
   *     should be decremented upon completion of this operation
   */
  private void transferWorkBeforeShutdown(
      ArrayList<Place> toStop, GlobalRef<CountDownLatch> toStopCount) {
    mallShutdown.set(true);
    shutdown = true;
    lifelineAnswerLock.unblock();
    workerLock.unblock();
    final B dealBag = queueInitializer.get();

    synchronized (workerBags) {
      state = -3;
    }

    int myWorkerCount = workerCount;
    while (myWorkerCount > 0) {
      workerLock.unblock();
      synchronized (workerBags) {
        myWorkerCount = workerCount;
      }
      console.println(
          "synchronized (workerBags): while (this.workerCount > 0): waiting, myWorkerCount="
              + myWorkerCount
              + ", workerAvailableLocks.size="
              + workerAvailableLocks.size()
              + ", lifelineAnswerThreadExited="
              + lifelineAnswerThreadExited);
      console.println("" + POOL);
      if (myWorkerCount > 0) {
        try {
          TimeUnit.MILLISECONDS.sleep(100);
        } catch (InterruptedException e) {
        }
      }
    }

    synchronized (workerBags) {
      synchronized (intraPlaceQueue) {
        console.println(
            "intraPlaceQueue.result="
                + intraPlaceQueue.getResult()
                + ", taskCount="
                + intraPlaceQueue.getCurrentTaskCount());
        console.println(
            "interPlaceQueue.result="
                + interPlaceQueue.getResult()
                + ", taskCount="
                + interPlaceQueue.getCurrentTaskCount());

        dealBag.merge(intraPlaceQueue);
        dealBag.merge(interPlaceQueue);

        final int worker = GLBMultiWorkerConfiguration.GLBOPTION_MULTIWORKER_WORKERPERPLACE.get();
        if (workerBags.size() != worker) {
          console.println(
              "Error: workerBags.size()=" + workerBags.size() + ", but should be " + worker);
        } else {
          console.println("successful waited for stop all workers");
        }

        for (final WorkerBag wb : workerBags) {
          dealBag.merge(wb.bag);
          console.println(
              "merged workbag.id="
                  + wb.workerId
                  + ", wb.bag.result="
                  + wb.bag.getResult()
                  + ", taskCount="
                  + wb.bag.getCurrentTaskCount());
        }
      }
    }
    console.println(
        "dealBag.result=" + dealBag.getResult() + ", taskCount=" + dealBag.getCurrentTaskCount());

    final PlaceLogger l = logger;

    int target = 0;
    for (final int i : REVERSE_LIFELINE) {
      if (!toStop.contains(place(i))) {
        target = i;
        break;
      }
    }
    console.println("found target for sending remaining tasks: " + target);
    asyncAt(
        place(target),
        () -> {
          console.println(
              "(in asyncAt) (before deal), dealBag.result="
                  + dealBag.getResult()
                  + ", taskCount="
                  + dealBag.getCurrentTaskCount());

          console.println("(in asyncAt) (before deal), state=" + state);
          deal(-42, dealBag, null);
          console.println("(in asyncAt) (after deal), state=" + state);
        });

    immediateAsyncAt(
        toStopCount.home(),
        () -> {
          computationLog.addPlaceLogger(l);
          toStopCount.get().countDown();
        });
  }

  /**
   * Launches a distributed warm-up on each process in the distributed cluster.
   *
   * <p>For some computations, it can be beneficial to launch a smaller problem instance to let the
   * Java Virtual Machine perform some optimizations that will yield better performance when the
   * larger "real" computation is later launched. This method launches the provided computation on
   * each host of the cluster. No load balance between the compute nodes in the cluster is performed
   * for the warm-up, only the load balance operations between the workers of each host is
   * performed.
   *
   * @param warmupBagSupplier supplier of the work sample which will be computed at each place
   *     independently as a warm-up
   * @param resultInitializer initializer for the result instance of each place
   * @param queueInitializer initializer for the two queues used at each place to perform load
   *     balancing operations
   * @param workerInitializer initializer for the bags held by each worker
   * @return logger instance containing information about the warm-up execution
   */
  public Logger warmup(
      SerializableSupplier<B> warmupBagSupplier,
      SerializableSupplier<R> resultInitializer,
      SerializableSupplier<B> queueInitializer,
      SerializableSupplier<B> workerInitializer) {
    final long reset = System.nanoTime();
    final long start = System.nanoTime();
    finish(
        () -> {
          for (final Place p : places()) {
            if (!isValidRemotePlace(p.id)) {
              continue;
            }

            asyncAt(
                p,
                () -> {
                  reset(resultInitializer, queueInitializer, workerInitializer, false, null, false);
                  lifelineThieves.clear();
                  for (final int i : LIFELINE) {
                    lifelineEstablished.put(i, true);
                  }
                  deal(-1, warmupBagSupplier.get(), null);
                });
          }
        });
    final long end = System.nanoTime();

    computationLog = new Logger();
    computationLog.setTimings(reset, start, end, end);

    return getLog();
  }

  /**
   * Main procedure of a worker thread in a place
   *
   * <p>A worker is a thread that processes the computation on a place. It has a {@link Bag}
   * instance to process and an identifier.
   *
   * <p>Each worker follows the following routine:
   *
   * <ol>
   *   <li>Spawns a new {@link #workerProcess(WorkerBag)} if the bag it is currently processing can
   *       be split and the {@link GLBMultiWorkerConfiguration#GLBOPTION_MULTIWORKER_WORKERPERPLACE}
   *       limit was not reached (meaning there are {@link Bag} instances left in member {@link
   *       #workerBags})
   *   <li>Checks if the {@link #intraPlaceQueue} bag is empty. If so and the currently held bag can
   *       be split ({@link Bag#isSplittable()}), splits its bag and merges the split content into
   *       {@link #intraPlaceQueue}.
   *   <li>Checks if feeding the {@link #interPlaceQueue} was requested. If the value for this
   *       worker in array {@link #feedInterQueueRequested} is {@code true} and this worker can
   *       split its bag, the worker sends half of the work it holds into the {@link
   *       #interPlaceQueue}.
   *   <li>Check if there are pending lifeline answers that can be answered. If so, unblocks the
   *       {@link #lifelineAnswerThread()}'s progress by unlocking the {@link #lifelineAnswerLock}.
   *   <li>If there are activities that are waiting for execution and the number of active workers
   *       has reached the number of available cores on the system, yields its execution to allow
   *       execution of other activities.
   *   <li>Processes a chunk of its bag
   *   <li>Repeat steps 1. to 6. until the {@link Bag} of which this worker is in charge becomes
   *       empty.
   *   <li>When the bag becomes empty as a result of splitting and processing it, the worker
   *       attempts to get some more work from the {@link #intraPlaceQueue} and the {@link
   *       #interPlaceQueue}. If successful in acquiring some work, resume its routine from step 1.
   *       If unsuccessful, stops operating.
   * </ol>
   *
   * @param workerBag computation to process along with an identifier for this worker process
   */
  void workerProcess(WorkerBag workerBag) {
    logger.workerStarted();
    final B bag = workerBag.bag; // Makes later accesses more compact
    final int myWorkerID = workerBag.workerId;

    for (; ; ) { // Infinite loop, not a mistake
      do {
        /*
         * 0. Should this place be shutdown?
         */
        if (mallShutdown.get()) {
          console.println("This worker (" + workerBag.workerId + ") stops now because of mall");
          // mall: dirty fix
          logger.workerStealing(); // The worker is now stealing
          stopWorker(workerBag, -3);
          return;
        }

        /*
         * 1. Checking if a new worker can be spawned
         */
        if (!workerBags.isEmpty() && bag.isSplittable()) {
          final WorkerBag wb = workerBags.poll();
          // polling of workerBags may yield null if a concurrent worker polled
          // the last bag, check is necessary.
          if (wb != null) {
            // We can spawn a new worker
            synchronized (workerBags) {
              workerCount++;
            }
            wb.bag.merge(bag.split(false));
            // important! new apgas: same as asyncAt(here(), f)
            async(() -> workerProcess(wb));
          }
        }

        /*
         * 2. Checking the status of the Bag used for intra place load balancing
         */
        if (intraQueueEmpty) {
          if (bag.isSplittable()) {
            synchronized (intraPlaceQueue) {
              intraQueueEmpty = false; // Setting the flag early will prevent
              // other workers with work to pile up on
              // the entrance of this synchronized
              // block
              intraPlaceQueue.merge(bag.split(false));
              logger.intraQueueFed.incrementAndGet();
              intraQueueEmpty = intraPlaceQueue.isEmpty();
            }
          }
        }

        /*
         * 3. Checking if interQueue feeding was requested
         */
        if (feedInterQueueRequested.get(workerBag.workerId) == 1) {
          if (bag.isSplittable()) {
            synchronized (intraPlaceQueue) {
              interPlaceQueue.merge(bag.split(false));
              logger.interQueueFed.incrementAndGet();
              interQueueEmpty = interPlaceQueue.isEmpty();
            }

            feedInterQueueRequested.set(workerBag.workerId, 0);
          }
        }

        /*
         * 4. Checking if waiting lifelines can be answered
         */
        if (!lifelineThieves.isEmpty() && !interQueueEmpty) {
          logger.lifelineAnswerThreadHold();
          lifelineAnswerLock.unblock(); // unblocking lifeline answer thread,
          lifelineToAnswer = true;
        }

        /*
         * 5. Yield if need be
         */
        if (workerCount == GLBMultiWorkerConfiguration.GLBOPTION_MULTIWORKER_WORKERPERPLACE.get()
            && (POOL.hasQueuedSubmissions() || lifelineToAnswer)) {
          final Lock l = workerAvailableLocks.poll();
          if (l != null) {
            logger.workerYieldStart();
            try {
              ForkJoinPool.managedBlock(l);
            } catch (final InterruptedException e) {
              // Should not happen in practice as the implementation Lock does
              // not throw the InterruptedException
              e.printStackTrace();
            }
            logger.workerYieldStop();

            l.reset(); // Reset the lock after usage
            workerAvailableLocks.add(l);
          }
        }

        /*
         * 6. Process its bag
         */
        final int processedTasks =
            bag.process(GLBMultiWorkerConfiguration.GLBOPTION_MULTIWORKER_N.get(), result);

        final long allProcessedTasks = logger.processedTasks.addAndGet(processedTasks);

        if (Configuration.CONFIG_APGAS_CONSOLEPRINTER.get()) {
          // print only every XX seconds
          final long now = System.nanoTime();
          if (((now - lastPrint[myWorkerID]) / 1e9) > 2) {
            lastPrint[myWorkerID] = now;
            console.println(
                "workerID="
                    + myWorkerID
                    + ", allProcessedTasks="
                    + allProcessedTasks
                    + ", bag.getCurrentTaskCount()="
                    + bag.getCurrentTaskCount()
                    + ", intraPlaceQueue="
                    + intraPlaceQueue.getCurrentTaskCount()
                    + ", interPlaceQueue="
                    + interPlaceQueue.getCurrentTaskCount()
                    + ", workerCount="
                    + workerCount);
          }
        }

      } while (!bag.isEmpty()); // 7. Repeat previous steps until the bag becomes empty.

      logger.workerStealing(); // The worker is now stealing

      /*
       * 8. Intra-place load balancing
       */
      synchronized (workerBags) { // Decision on whether this worker is going to
        // continue is made here. This decision needs
        // to be done in a synchronized block to
        // guarantee mutual exclusion with method
        // lifelineDeal.

        // Attempt to steal some work from the intra-place bag
        if (!intraQueueEmpty) {

          B loot = null;
          synchronized (intraPlaceQueue) {
            if (!intraQueueEmpty) {
              loot = intraPlaceQueue.split(true); // If only a fragment can't
              // be taken, we take the whole content of the intraPlaceQueue
              intraQueueEmpty = intraPlaceQueue.isEmpty(); // Flag update
              logger.intraQueueSplit.incrementAndGet();
            }
          }
          if (loot != null) {
            bag.merge(loot);
          }

        } else if (!interQueueEmpty) { // Couldn't steal from intraQueue, try on

          // interQueue
          B loot = null;
          synchronized (intraPlaceQueue) {
            if (!interQueueEmpty) {
              loot = interPlaceQueue.split(true); // Take from interplace
              logger.interQueueSplit.incrementAndGet();
              interQueueEmpty = interPlaceQueue.isEmpty(); // Update the flag
              /*
               * if (loot.isSplittable()) { // Put some work back into the intra queue
               * intraPlaceQueue.merge(loot.split(false));
               * logger.intraQueueFed.incrementAndGet(); intraQueueEmpty =
               * intraPlaceQueue.isEmpty(); // Update the flag }
               */
            }
          }
          if (interQueueEmpty) {
            requestInterQueueFeed();
          }
          if (loot != null) {
            bag.merge(loot);
          }

        } else { // Both queues were empty. The worker stops.
          stopWorker(workerBag, -1);
          return;
        }
      } // synchronized stealing block

      // Stealing from the queues in the place was successful. The worker goes
      // back to processing its fraction of the work.
      logger.workerResumed();
    } // Enclosing infinite for loop. Exit is done with the "return;" 7 lines
    // above.
  }

  /**
   * Utility class used to contain a bag and the id of a worker in a single instance.
   *
   * @author Patrick Finnerty
   */
  class WorkerBag {

    /** Bag held by worker */
    public B bag;

    /** Integer identifier of the worker */
    public int workerId;

    /**
     * Constructor
     *
     * <p>Initializes a {@link WorkerBag} instance which holds the given id and {@link Bag}. This is
     * used to identify the workers
     *
     * @param id identifier of the worker that holds the bag
     * @param b Bag instance associated to the given identifier
     */
    public WorkerBag(int id, SerializableSupplier<B> b) {
      workerId = id;
      bag = b.get();
    }

    public void initStaticTasks() {
      bag.initStaticTasks(workerId);
    }
  }
}
