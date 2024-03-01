package handist.glb.multiworker;

import apgas.impl.elastic.GetLoad;
import java.io.Serializable;

/**
 * Class in charge of obtaining the task-based load on a place.
 *
 * @author Ashatar
 */
public class GetTaskLoad implements GetLoad, Serializable {
  /** Max duration in seconds for a place to have zero tasks. */
  private final long timeTillRemove = 10;

  /** The number of workers per place */
  private final int worker = GLBMultiWorkerConfiguration.GLBOPTION_MULTIWORKER_WORKERPERPLACE.get();

  /** GLBcomputer object to collect task numbers on a place. */
  GLBcomputer glbComputer;

  /** The number of tasks in the workerBags of this place at the moment. */
  private int workerBags;

  /** The number of tasks in the intra-queue of this place at the moment. */
  private long intraQ;

  /** The number of tasks in the inter-queue of this place at the moment. */
  private long interQ;

  /** Total task number from bags and queues of this place. */
  private long totalTasks;

  /**
   * Elapsed time since this place has zero tasks. Defaults to -1 if place has tasks. Gets reset to
   * -1 if a place only had zero tasks for a short time but gets new tasks then.
   */
  private long noTasksSince = -1;

  /** Current time for comparison to noTasksSince. */
  private long now;

  /** The time this place has been inactive. */
  private long inactive;

  /** The current tasks in relation to the number of workers. */
  private double tasksPerWorker;

  public void setGLBcomputer(GLBcomputer glbComputer) {
    this.glbComputer = glbComputer;
  }

  @Override
  public double getLoad() {
    workerBags = glbComputer.getTaskCountOfWorkerBags();
    intraQ = glbComputer.intraPlaceQueue.getCurrentTaskCount();
    interQ = glbComputer.interPlaceQueue.getCurrentTaskCount();
    totalTasks = interQ + intraQ + workerBags + glbComputer.workerCount;
    tasksPerWorker = (double) totalTasks / worker;
    now = System.nanoTime();

    // Shrinking
    if (totalTasks == 0) {
      if (noTasksSince == -1) {
        noTasksSince = System.nanoTime();
      }
      inactive = now - noTasksSince;
      inactive /= 1e9;
      if (inactive > timeTillRemove) {
        return 0;
      } else {
        return 0.01;
      }
    }

    // Reset inactivity
    noTasksSince = -1;

    // Medium Load / Growing
    if (tasksPerWorker < 1) {
      return tasksPerWorker * 100;
    } else {
      return 100;
    }
  }
}
