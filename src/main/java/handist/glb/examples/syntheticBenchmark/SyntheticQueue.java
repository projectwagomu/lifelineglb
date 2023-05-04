package handist.glb.examples.syntheticBenchmark;

import static apgas.Constructs.here;
import static apgas.Constructs.places;

import handist.glb.examples.util.LongSum;
import handist.glb.multiworker.Bag;
import handist.glb.multiworker.GLBMultiWorkerConfiguration;

public class SyntheticQueue extends Synthetic implements Bag<SyntheticQueue, LongSum> {

	/** Serial Version UID */
	private static final long serialVersionUID = -7797377631042311713L;

	public SyntheticQueue(final long durationVariance, final long maxChildren, boolean isStatic) {
		super(durationVariance, maxChildren, isStatic);
	}

	@Override
	public boolean isEmpty() {
		return tasks.isEmpty();
	}

	@Override
	public boolean isSplittable() {
		if (this.tasks.size() >= 2) {
			return true;
		} else {
			return false;
		}
	}

	@Override
	public void merge(SyntheticQueue syntheticQueue) {
		this.tasks.pushArrayFirst(syntheticQueue.tasks.toArray());
		this.diff += syntheticQueue.diff;
		this.result += syntheticQueue.result;
	}

	@Override
	public int process(int workAmount, LongSum sharedObject) {
		int i = 0;
		for (; i < workAmount && this.tasks.size() > 0; ++i) {
			calculate();
		}
		count += i;
		result += i;
		return i;
	}

	@Override
	public SyntheticQueue split(boolean takeAll) {
		int nStolen = Math.max(tasks.size() / 2, 1);
		if (tasks.size() < 2 && !takeAll) {
			return new SyntheticQueue(this.durationVariance, this.maxChildren, this.isStatic);
		}

		// StaticSyn performs better if all tasks are taken out
		// DynamicSyn performs better if it follows the steal half scheme from the original KobeGLB doku
		if (this.isStatic) {
			if (takeAll) {
				nStolen = tasks.size();
			}
		}

		SyntheticQueue syntheticQueue =
				new SyntheticQueue(this.durationVariance, this.maxChildren, this.isStatic);
		SyntheticTask[] fromFirst = tasks.getFromFirst(nStolen);
		for (final SyntheticTask t : fromFirst) {
			syntheticQueue.tasks.addFirst(t);
		}
		if (takeAll) {
			syntheticQueue.diff = diff;
			diff = 0;
		}

		return syntheticQueue;
	}

	@Override
	public void submit(LongSum longSum) {
		longSum.sum += this.result;
	}

	@Override
	public LongSum getResult() {
		return new LongSum(this.result);
	}

	@Override
	public long getCurrentTaskCount() {
		return this.tasks.size();
	}

	@Override
	public void initStaticTasks(int workerId) {
		//    final boolean addPlaces =
		// GLBMultiWorkerConfiguration.GLB_MULTIWORKER_MALLEABILITY_ADD.get();
		//    final int mallPlaces =
		//        GLBMultiWorkerConfiguration.GLB_MULTIWORKER_MALLEABILITY_MALLPLACES.get();
		//    final boolean mallEnabled =
		// GLBMultiWorkerConfiguration.GLB_MULTIWORKER_MALLEABILITY.get();
		final int workerPerPlace = GLBMultiWorkerConfiguration.GLB_MULTIWORKER_WORKERPERPLACE.get();

		final int totalWorkers;
		//    if (addPlaces && mallEnabled && mallPlaces > 0) {
		//      // We increase the amount of work to InitPlaces+MallPlaces
		//      totalWorkers = workerPerPlace * (places().size() + mallPlaces);
		//    } else {
		totalWorkers = workerPerPlace * places().size();
		//    }

		final long taskCount = tasksPerWorker * totalWorkers;
		long taskDuration = (1000L * 1000L * totalDuration * totalWorkers) / taskCount;
		randGen.setSeed(42);
		double w[] = new double[totalWorkers];
		double s = 0.0;
		variance = durationVariance / 100.0f;
		for (int i = 0; i < w.length; ++i) {
			w[i] = (1 - variance) + randGen.nextFloat() * 2 * variance;
			s += w[i];
		}

		final int myWorkerID = (here().id * workerPerPlace) + workerId;
		taskDuration = (long) (totalWorkers * taskDuration * w[myWorkerID] / s);

		final long localTasksPerWorker;
		//    if (addPlaces && mallEnabled && mallPlaces > 0) {
		//      // We increase the count of localTasksPerWorker
		//      localTasksPerWorker = taskCount / (workerPerPlace * places().size());
		//    } else {
		localTasksPerWorker = tasksPerWorker;
		//    }

		for (long i = 0; i < localTasksPerWorker; ++i) {
			tasks.addLast(new SyntheticTask(taskBallast, taskDuration));
		}
		System.out.println(
				here()
				+ " worker="
				+ workerId
				+ " created static tasks: taskDuration="
				+ taskDuration
				+ ", taskCount="
				+ taskCount
				+ ", tasksPerWorker="
				+ tasksPerWorker
				+ ", localTasksPerWorker="
				+ localTasksPerWorker);
	}
}
