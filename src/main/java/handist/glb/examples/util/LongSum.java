/*
 *  This file is part of the Handy Tools for Distributed Computing project
 *  HanDist (https://github.com/handist)
 *
 *  This file is licensed to You under the Eclipse Public License (EPL);
 *  You may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *      http://www.opensource.org/licenses/eclipse-1.0.php
 *
 *  (C) copyright CS29 Fine 2018-2019.
 */
package handist.glb.examples.util;

import handist.glb.multiworker.Fold;
import java.io.Serializable;

/**
 * Implementation of the {@link Fold} interface that performs the addition on {@code long} integers.
 * The class also implements interface {@link Serializable} in order to be used by the GLB library.
 *
 * @author Patrick Finnerty
 */
public class LongSum implements Fold<LongSum>, Serializable {

  /** Serial Version UID */
  private static final long serialVersionUID = 3582168956043482749L;

  @Override
  public String toString() {
    return sum + "";
  }

  /** Long in which the sum is performed */
  public long sum;

  /*
   * (non-Javadoc)
   *
   * @see apgas.glb.Fold#fold(apgas.glb.Fold)
   */
  @Override
  public void fold(LongSum f) {
    sum += f.sum;
  }

  /**
   * Constructor
   *
   * @param s initial value for the sum
   */
  public LongSum(long s) {
    sum = s;
  }
}
