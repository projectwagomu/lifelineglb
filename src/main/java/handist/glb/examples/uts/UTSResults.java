package handist.glb.examples.uts;

public class UTSResults {

	/** Correct results for seed = 19, branching = 4, index == depth */
	private static final long[] results = new long[] { 0l, 6l, 65l, 254l, 944l, 3987l, 16000l, 63914l, 257042l,
			1031269l, 4130071l, 16526523l, 66106929l, 264459392l, 1057675516l, 4230646601l, 16922208327l, 67688164184l,
			270751679750l };

	public static long getResult(int index) {
		if (index > results.length - 1) {
			System.out.println("UTSResults: depth " + index + " was requested, but ist not available");
			return 0;
		}
		return results[index];
	}

	public static boolean proveCorrectness(int depth, int seed, int branching, long result) {
		if (depth > results.length - 1 || seed != 19 || branching != 4) {
			System.out.println("UTSResults: depth=" + depth + ", seed=" + seed + ", branching=" + branching
					+ " was requested, but ist not available");
			return false;
		}
		final boolean correctness = results[depth] == result;
		if (correctness) {
			System.out.println("Result is correct");
		} else {
			System.out.println("Result is NOT correct!!!!");
		}
		return correctness;
	}
}
