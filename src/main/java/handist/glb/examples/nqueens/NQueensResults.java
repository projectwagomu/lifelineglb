package handist.glb.examples.nqueens;

public class NQueensResults {

  /** Correct results for seed = 19 branching = 4 index == depth */
  private static final long[] results =
      new long[] {
        0,
        1,
        0,
        0,
        2,
        10, /* 5 */
        4,
        40,
        92,
        352,
        724, /* 10 */
        2680,
        14200,
        73712,
        365596,
        2279184, /* 15 */
        14772512,
        95815104,
        666090624,
        4968057848L,
        39029188884L, /* 20 */
      };

  public static long getResult(int index) {
    if (index > results.length - 1) {
      System.out.println("UTSResults: depth " + index + " was requested, but ist not available");
      return 0;
    }
    return results[index];
  }

  public static boolean proveCorrectness(int index, long result) {
    if (index > results.length - 1) {
      System.out.println("UTSResults: depth " + index + " was requested, but ist not available");
      return false;
    }
    boolean correctness = results[index] == result;
    if (correctness) {
      System.out.println("Result is correct");
    } else {
      System.out.println("Result is NOT correct!!!!");
    }
    return correctness;
  }
}
