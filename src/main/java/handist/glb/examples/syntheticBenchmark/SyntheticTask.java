package handist.glb.examples.syntheticBenchmark;

import java.io.Serializable;

public class SyntheticTask implements Serializable {

	private static final long serialVersionUID = 2282792464012580417L;

	byte[] ballast;
	long depth;
	long duration;
	long seed;

	public SyntheticTask(long ballastInBytes, long duration) {
		ballast = new byte[(int) ballastInBytes];
		this.duration = duration;
	}

	public SyntheticTask(long ballastInBytes, long seed, long depth, long duration) {
		this(ballastInBytes, duration);
		this.seed = seed;
		this.depth = depth;
	}
}
