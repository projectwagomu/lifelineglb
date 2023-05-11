package handist.glb.examples.util;

import java.io.Serializable;

/**
 * A class that emulates the recursive-matrix graph from the paper: "R-MAT: A Recursive Model for
 * Graph Mining" by Chakrabarti et al.
 *
 * <p>This code is based on the MATLAB sample code that was given along with the SSCA2 benchmarks.
 *
 * <p>The R-MAT generator takes in 6 parameters as input (via the constructor). The values passed to
 * these arguments ultimately decide the shape and the properties of the graph. For a more detailed
 * description of the parameters and their influence, please refer to Chakrabarti et al.
 */
public class Rmat implements Serializable {

  private long seed; // seed to the random number generator
  private int n; // the log of the number of verties. I.e., N = 2^n
  private int N; // the number of vertices.
  private double a; // The next 4 parameters determine the shape of
  private double b; // the graph. A detailed description of the
  private double c; // parameters is out of scope here. Briefly,
  private double d; // (a+b+c+d == 1) and typically a>=b, a>= c, a>=d.

  public Rmat(long seed, int n, double a, double b, double c, double d) {
    this.seed = seed;
    this.n = n;
    this.N = 1 << n;
    this.a = a;
    this.b = b;
    this.c = c;
    this.d = d;
  }

  /**
   * A function that mimics the MATLAB function rand (M,1). It generates a vector of M random
   * numbers. Here, numElements is M!
   */
  private void rand(double[] dest, X10Random rng) {
    for (int i = 0; i < dest.length; ++i) {
      dest[i] = rng.nextDouble();
    }
  }

  /**
   * A function that mimics the use of > operator in MATLAB when the LHS is a vector and the RHS
   * either a scalar value or a vector. Basically, the result of "V > a", where "V" is a vector and
   * "a" is a scalar is an integer-valued vector where there is a 1 if "V[i] > a" and 0 otherwise.
   */
  private void greaterThan(int[] dest, double[] lhs, double rhs) {
    for (int i = 0; i < dest.length; ++i) {
      dest[i] = (lhs[i] > rhs) ? 1 : 0;
    }
  }

  /**
   * The same function as above, only with a element-wise comparison in the RHS instead of a
   * comparison with a scalar value. So, there is a 1 in the resultant vector if "LHS[i] > RHS[i]",
   * and 0 otherwise.
   */
  private void greaterThan(int[] dest, double[] lhs, double[] rhs) {
    for (int i = 0; i < dest.length; ++i) {
      dest[i] = (lhs[i] > rhs[i]) ? 1 : 0;
    }
  }

  /**
   * Multiple a vector with a scalar. There is, however, one catch. When the flip bit is turned on,
   * the vector (LHS) is manipulated such that its either always 0 or 1. If V[i] > 0 and flip==true,
   * then LHS(i) == 0, and 1 otherwise. In other words, we flip V[i].
   */
  private void multiply(double[] dest, int[] lhs, double multiplier, Boolean flip) {
    for (int i = 0; i < dest.length; ++i) {
      dest[i] = multiplier * (flip ? ((lhs[i] > 0) ? 0 : 1) : lhs[i]);
    }
  }

  /** A straightforward vector-scalar multiplication */
  private void multiply(int[] lhs, int multiplier) {
    for (int i = 0; i < lhs.length; ++i) {
      lhs[i] *= multiplier;
    }
  }

  /** A straightforward addition of two vectors. */
  private void add(double[] lhs, double[] rhs) {
    for (int i = 0; i < lhs.length; ++i) {
      lhs[i] += rhs[i];
    }
  }

  /** Same as above, but with a different type */
  private void add(int[] lhs, int[] rhs) {
    for (int i = 0; i < lhs.length; ++i) {
      lhs[i] += rhs[i];
    }
  }

  /** This function mimics the behavior of the MATLAB function sparse(i,j,timestamps). */
  private Graph sparse(int[] row, int[] col) {
    Graph adjacencyGraph = new Graph(N);
    for (int i = 0; i < row.length; ++i) {
      adjacencyGraph.addEdge(row[i], col[i]);
    }
    return adjacencyGraph;
  }

  public Graph generate() {
    // Initialize M, and rng
    int M = 8 * N;
    X10Random rng = new X10Random(seed);

    // Create index arrays
    int[] ii = new int[M];
    int[] jj = new int[M];
    int[] iiBit = new int[M];
    double[] jjBitComparator = new double[M];
    double[] r = new double[M];

    // Loop over each order of bit
    double ab = a + b;
    double cNorm = c / (c + d);
    double aNorm = a / (a + b);

    for (int ib = 0; ib < n; ++ib) {
      int exponent = 1 << ib;
      rand(r, rng);
      greaterThan(iiBit, r, ab);
      multiply(jjBitComparator, iiBit, cNorm, false);
      multiply(r, iiBit, aNorm, true);
      multiply(iiBit, exponent);
      add(ii, iiBit);
      add(jjBitComparator, r);
      rand(r, rng);
      greaterThan(iiBit, r, jjBitComparator);
      multiply(iiBit, exponent);
      add(jj, iiBit);
    }

    return sparse(ii, jj);
  }
}
