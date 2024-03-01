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
package handist.glb.examples.uts;

import handist.glb.examples.util.LongSum;
import handist.glb.multiworker.Bag;
import java.io.PrintStream;
import java.io.Serializable;
import java.security.DigestException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Implementation of an Unbalanced Tree Search computation.
 *
 * <p>This class is an adaptation from the <a href=
 * "https://github.com/x10-lang/apgas/blob/master/apgas.examples/src/apgas/examples/UTS.java">apgas.examples.UTS</a>
 * class to fit the {@link Bag} interface for the multithreaded global load balancer. The result
 * returned by {@link MultiworkerUTS} is the total number of nodes explored, using the {@link
 * LongSum} class.
 *
 * @author Patrick Finnerty
 */
public class MultiworkerUTS implements Bag<MultiworkerUTS, LongSum>, Serializable {

  /** Branching factor: 4 */
  protected static final double den = Math.log(4.0 / (1.0 + 4.0));

  /** Serial Version UID */
  protected static final long serialVersionUID = 4654891201916215845L;

  /** Counts the number of nodes explored. */
  public long exploredNodes;

  /** Keeps track of the current position in the arrays. */
  int currentDepth;

  /** Array keeping track of the current depth of the node */
  int[] depth;

  /** Array containing the splittable hash used to generate the tree */
  byte[] hash;

  /**
   * Array containing the lower id of the next node to be explored at each level in the tree. The
   * actual number of leaves remaining to be explored at each level is given by computing the
   * difference between {@link #lower} and {@link #upper} at a given index.
   */
  int[] lower;

  /**
   * {@link MessageDigest} instance held by this {@link Bag} instance. This member is set to
   * transient so as not to be serialized when instances of this class are transfered from one place
   * to another to perform load balance.
   */
  transient MessageDigest md;

  /**
   * Array containing the upper id of the next node to be explored at each level in the tree. The
   * actual number of leaves remaining to be explored at each level is given by computing the
   * difference between {@link #lower} and {@link #upper} at a given index. When exploring the tree,
   * the node with the highest id is always chosen first. When all the recursive children of this
   * node have been explored, the value in the {@link #upper} array is decremented and the next node
   * is explored (provided {@link #lower} at that index is inferior to {@link #upper}).
   */
  int[] upper;

  /**
   * Initializes a new instance able to hold a tree exploration of depth the specified parameter
   * without needing to increase the size of the various arrays used in the implementation.
   *
   * @param initialSize depth of the tree exploration
   */
  public MultiworkerUTS(int initialSize) {
    hash = new byte[initialSize * 20 + 4];
    depth = new int[initialSize];
    lower = new int[initialSize];
    upper = new int[initialSize];

    exploredNodes = 0;
    md = getMessageDigest();
  }

  /**
   * Returns the SHA-1 {@link MessageDigest} to be used to generate the seed of the tree.
   *
   * @return a {@link MessageDigest} instance
   */
  private static MessageDigest getMessageDigest() {
    try {
      return MessageDigest.getInstance("SHA-1");
    } catch (final NoSuchAlgorithmException e) {
      e.printStackTrace();
      return null;
    }
  }

  /**
   * Generates the seed and the children nodes of node being currently explored.
   *
   * @param d maximum depth of the tree to explore
   * @param md the {@link MessageDigest} used to generate the tree seed
   * @throws DigestException if the {@link MessageDigest} throws an exception when called
   */
  private void digest(int d, MessageDigest md) throws DigestException {
    // Creates more space in the arrays if need be
    if (currentDepth >= depth.length) {
      grow();
    }
    ++exploredNodes; // We are exploring one node (expanding its child nodes)

    // Writes onto array hash on the next 20 cells (=bytes)
    final int offset = currentDepth * 20;
    md.digest(hash, offset, 20);

    // Determine the number of child nodes based on the generated seed

    // v is the pseudo-random positive integer made out of the 4 bytes in the
    // hash array generated by the message digest just above
    final int v =
        ((0x7f & hash[offset + 16]) << 24)
            | ((0xff & hash[offset + 17]) << 16)
            | ((0xff & hash[offset + 18]) << 8)
            | (0xff & hash[offset + 19]);

    final int n = (int) (Math.log(1.0 - v / 2147483648.0) / den);
    // 2.147.483.648 is written as 1 followed by 63 zeros in binary : -1.
    // v / 2.147.483.648 is then in the range (-2147483647,0]
    // n is then a positive integer, sometimes = 0, sometimes greater.
    if (n > 0) {
      if (d > 1) { // Bound for the tree depth
        // We create node size
        depth[currentDepth] = d - 1;
        lower[currentDepth] = 0;
        upper[currentDepth] = n;
        currentDepth++;
      } else {
        exploredNodes += n;
      }
    }
  }

  /**
   * Explores one node on the tree and returns.
   *
   * @param md the {@link MessageDigest} instance to be used to generate the tree
   * @throws DigestException if the provided {@link MessageDigest} throws an exception
   */
  public void expand(MessageDigest md) throws DigestException {
    final int top = currentDepth - 1;

    final int d = depth[top];
    final int l = lower[top];
    final int u = upper[top] - 1;
    if (u == l) {
      currentDepth = top; // We go back to the top node, we have explored all
      // nodes on the top + 1 level
    } else {
      upper[top] = u; // We decrement the child nodes of top (the current node's
      // parent node) : we have finished exploring all the child
      // nodes of the current node
    }

    // Setting up a new 'seed' to explore the current node's child nodes
    final int offset = top * 20;
    hash[offset + 20] = (byte) (u >> 24);
    hash[offset + 21] = (byte) (u >> 16);
    hash[offset + 22] = (byte) (u >> 8);
    hash[offset + 23] = (byte) u;
    md.update(hash, offset, 24); // seed takes into account both the parent seed
    // and 'u'
    digest(d, md);
  }

  @Override
  public long getCurrentTaskCount() {
    int s = 0;
    int t = 0;
    for (int i = 0; i < currentDepth; ++i) {
      final int nodesRemaining = upper[i] - lower[i];
      if (nodesRemaining >= 1) {
        if (nodesRemaining >= 2) {
          ++s;
        }
        ++t;
      }
    }
    return s + t;
  }

  @Override
  public LongSum getResult() {
    return new LongSum(exploredNodes);
  }

  /** Increases the size of the arrays used in the implementation. */
  private void grow() {
    final int n = depth.length * 2;
    final byte[] h = new byte[n * 20 + 4];
    final int[] d = new int[n];
    final int[] l = new int[n];
    final int[] u = new int[n];
    System.arraycopy(hash, 0, h, 0, currentDepth * 20);
    System.arraycopy(depth, 0, d, 0, currentDepth);
    System.arraycopy(lower, 0, l, 0, currentDepth);
    System.arraycopy(upper, 0, u, 0, currentDepth);
    hash = h;
    depth = d;
    lower = l;
    upper = u;
  }

  @Override
  public void initStaticTasks(int workerId) {
    // Never called because computeDynamic is used
  }

  /*
   * (non-Javadoc)
   *
   * @see apgas.glbm.Bag#isEmpty()
   */
  @Override
  public boolean isEmpty() {
    return currentDepth < 1;
  }

  /**
   * Indicates if the DepthFirstSearch exploration of the tree can be split. This criteria is deemed
   * satisfactorily met when at a certain point in the current branch exploration, there remains at
   * least 2 leaves, that is is the difference between {@link #lower} and {@link #upper} at a
   * certain index is greater or equal to 2.
   */
  @Override
  public boolean isSplittable() {
    for (int i = 0; i < currentDepth; ++i) {
      if ((upper[i] - lower[i]) >= 2) {
        return true;
      }
    }
    return false;
  }

  /**
   * Stitches the given tree exploration to the current state. The arrays used to contain the tree
   * exploration information will increase in size to accommodate the given tree if necessary.
   */
  @Override
  public void merge(MultiworkerUTS b) {
    final int s = currentDepth + b.currentDepth;
    while (s > depth.length) {
      grow();
    }
    System.arraycopy(b.hash, 0, hash, currentDepth * 20, b.currentDepth * 20);
    System.arraycopy(b.depth, 0, depth, currentDepth, b.currentDepth);
    System.arraycopy(b.lower, 0, lower, currentDepth, b.currentDepth);
    System.arraycopy(b.upper, 0, upper, currentDepth, b.currentDepth);
    currentDepth = s;
    exploredNodes += b.exploredNodes;
  }

  /**
   * Prints the current status of this instance to the provided output stream.
   *
   * @param out output to which the state of the tree needs to be written to
   */
  public void print(PrintStream out) {
    out.printf("Index :  %1$2d%n", currentDepth);

    out.print("Hash  : ");
    for (int i = 0; i < currentDepth; i++) {
      for (int j = 0; j < 10; j++) {
        out.printf("%1$h", hash[i * 20 + j]);
      }
      out.print(" ");
    }
    out.println();

    out.print("Depth : ");
    for (int i = 0; i < currentDepth; i++) {
      out.printf(" %1$2d", depth[i]);
    }
    out.println();

    out.print("Upper : ");
    for (int i = 0; i < currentDepth; i++) {
      out.printf(" %1$2d", upper[i]);
    }
    out.println();

    out.print("Lower : ");
    for (int i = 0; i < currentDepth; i++) {
      out.printf(" %1$2d", lower[i]);
    }
    out.println();
  }

  /**
   * Performs node exploration until either the "work-amount" of nodes is explored or the tree
   * exploration is finished. The second parameter is unused. There is no requirement for workers to
   * share information during the tree exploration. The parameter is present to match the
   * computation abstraction of the multi-worker GLB design which allows this possibility
   */
  @Override
  public int process(int workAmount, LongSum shared) {
    int i = 0;
    while (!isEmpty() && workAmount > 0) {
      try {
        expand(md);
        i++;
      } catch (final DigestException e) {
        e.printStackTrace();
      }
      workAmount--;
    }
    return i;
  }

  /**
   * Plants the seed of the tree. Needs to be called before the tree exploration is started.
   *
   * @param seed an integer used as seed
   * @param depth maximum depth of the intended exploration
   */
  public void seed(int seed, int depth) {
    try {
      for (int i = 0; i < 16; ++i) {
        hash[i] = 0;
      }
      hash[16] = (byte) (seed >> 24);
      hash[17] = (byte) (seed >> 16);
      hash[18] = (byte) (seed >> 8);
      hash[19] = (byte) seed;
      md.update(hash, 0, 20);
      digest(depth, md);
    } catch (final DigestException e) {
    }
  }

  /**
   * Splits the tree exploration by giving half of the leaves remaining to explore to an instance
   * which is then returned.
   */
  @Override
  public MultiworkerUTS split(boolean takeAll) {
    int s = 0;
    int t = 0;
    for (int i = 0; i < currentDepth; ++i) {
      final int nodesRemaining = upper[i] - lower[i];
      if (nodesRemaining >= 1) {
        if (nodesRemaining >= 2) {
          ++s;
        }
        ++t;
      }
    }
    final MultiworkerUTS split;
    if (takeAll && s == 0) {
      // Special case where the bag cannot be split. The whole content of this
      // bag is given away as a result.
      split = new MultiworkerUTS(t);
      for (int i = 0; i < currentDepth; ++i) {
        final int p = upper[i] - lower[i];
        if (p >= 1) { // Copy only the nodes available for exploration
          System.arraycopy(hash, i * 20, split.hash, split.currentDepth * 20, 20);
          split.depth[split.currentDepth] = depth[i];
          split.upper[split.currentDepth] = upper[i];
          split.lower[split.currentDepth++] = lower[i];
        }
      }
      currentDepth = 0; // This bag is now empty
    } else {
      // Split the bag as per usual
      split = new MultiworkerUTS(s);
      for (int i = 0; i < currentDepth; ++i) {
        final int p = upper[i] - lower[i];
        if (p >= 2) {
          System.arraycopy(hash, i * 20, split.hash, split.currentDepth * 20, 20);
          split.depth[split.currentDepth] = depth[i];
          split.upper[split.currentDepth] = upper[i];
          split.lower[split.currentDepth++] = upper[i] -= p / 2;
        }
      }
    }
    return split;
  }

  /**
   * Adds the number of nodes explored as a result of the {@link #process(int, LongSum)} method by
   * this instance into the given {@link LongSum}.
   */
  @Override
  public void submit(LongSum r) {
    r.sum += exploredNodes;
  }
}
