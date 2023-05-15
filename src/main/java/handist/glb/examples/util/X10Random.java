package handist.glb.examples.util;

import java.util.concurrent.atomic.AtomicLong;

public class X10Random {

  private static final AtomicLong defaultGen = new AtomicLong(System.nanoTime());
  private static final long GOLDEN_GAMMA = 0x9e3779b97f4a7c15L;
  private static final float FLOAT_ULP = 1.0f / (1L << 24);
  private static final double DOUBLE_ULP = 1.0 / (1L << 53);

  private long seed;
  private long gamma;

  private X10Random(long seed, long gamma) {
    this.seed = seed;
    this.gamma = gamma;
  }

  public X10Random(long seed) {
    this(seed, GOLDEN_GAMMA);
  }

  public X10Random() {
    long s = defaultGen.getAndAdd(2 * GOLDEN_GAMMA);
    seed = mix64(s);
    gamma = mixGamma(s + GOLDEN_GAMMA);
  }

  private static long mix64(long z) {
    z = (z ^ (z >>> 33)) * 0xff51afd7ed558ccdL;
    z = (z ^ (z >>> 33)) * 0xc4ceb9fe1a85ec53L;
    return z ^ (z >>> 33);
  }

  private static int mix32(long z) {
    z = (z ^ (z >>> 33)) * 0xff51afd7ed558ccdL;
    z = (z ^ (z >>> 33)) * 0xc4ceb9fe1a85ec53L;
    return ((int) (z >>> 32));
  }

  private static long mix64variant13(long z) {
    z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
    z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
    return z ^ (z >>> 31);
  }

  private static long mixGamma(long z) {
    z = mix64variant13(z) | 1;
    long n = Long.bitCount(z ^ (z >>> 1));
    if (n >= 24) {
      z ^= 0xaaaaaaaaaaaaaaaaL;
    }
    return z;
  }

  /** Split and return a new Random instance derived from this one */
  public X10Random split() {
    return (new X10Random(mix64(nextSeed()), mixGamma(nextSeed())));
  }

  /** Return a 32-bit random integer */
  private int nextInt() {
    return (mix32(nextSeed()));
  }

  /**
   * Return a 32-bit random integer in the range 0 to maxPlus1-1 when maxPlus1 > 0. Return 0 if
   * maxPlus1 <= 0 instead of throwing an IllegalArgumentException, to simplify user code.
   */
  public int nextInt(int n) {
    if (n <= 0) {
      return 0;
    }

    if ((n & -n) == n) {
      // If a power of 2, just mask nextInt
      return nextInt() & (n - 1);
    }

    int mask = 1;
    while ((n & ~mask) != 0) {
      mask <<= 1;
      mask |= 1;
    }

    // Keep generating numbers of the right size until we get
    // one in range.  The expected number of iterations is 2.
    int x;

    do {
      x = nextInt() & mask;
    } while (x >= n);

    return x;
  }

  public void nextBytes(byte[] buf) {
    int i = 0;
    while (true) {
      long x = nextLong();
      for (int idx = 0; idx < 8; ++idx) {
        if (i >= buf.length) {
          return;
        }
        buf[i] = ((byte) (x & 0xff));
        i++;
        x >>= 8;
      }
    }
  }

  /** Return a 64-bit random (Long) integer */
  private long nextLong() {
    return (mix64(nextSeed()));
  }

  public long nextLong(long n) {
    if (n <= 0) {
      return 0;
    }

    if ((n & -n) == n) {
      // If a power of 2, just mask nextInt
      return nextLong() & (n - 1);
    }

    long mask = 1;
    while ((n & ~mask) != 0) {
      mask <<= 1;
      mask |= 1;
    }

    // Keep generating numbers of the right size until we get
    // one in range.  The expected number of iterations is 2.
    long x = 0;

    do {
      x = nextLong() & mask;
    } while (x >= n);

    return x;
  }

  /** Return a random boolean. */
  public boolean nextBoolean() {
    return (nextInt() < 0);
  }

  /** Return a random float between 0.0f and 1.0f. */
  public float nextFloat() {
    return ((nextInt() >>> 8) * FLOAT_ULP);
  }

  /** Return a random double between 0.0 and 1.0. */
  public double nextDouble() {
    return ((nextLong() >>> 11) * DOUBLE_ULP);
  }

  private long nextSeed() {
    seed += gamma;
    return seed;
  }
}
