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
package handist.glb.multiworker.lifeline;

import apgas.Place;
import java.io.Serializable;
import java.util.List;

/**
 * Lifeline strategy implementing an hypercube lifeline strategy among places.
 *
 * <p>A lifeline between two places exists if the edit distance between two place's id's written in
 * binary is 1.
 *
 * @author Patrick Finnerty
 */
public class MyHypercubeStrategy implements LifelineStrategy, Serializable {

  /** Serial Version UID */
  private static final long serialVersionUID = 5106410194659222967L;

  /*
   * (non-Javadoc)
   *
   * @see apgas.glb.LifelineStrategy#lifeline(int, int)
   */
  @Override
  public int[] lifeline(final int home, List<? extends Place> placesList) {
    final int nbPlaces = placesList.size();
    final ConfigurableHypercubeStrategy chs = new ConfigurableHypercubeStrategy();
    chs.setL(computeL(nbPlaces));
    chs.setZ(computeZ(chs.getL(), nbPlaces));
    return chs.lifeline(home, placesList);
  }

  /*
   * (non-Javadoc)
   *
   * @see apgas.glb.LifelineStrategy#reverseLifeline(int, int)
   */
  @Override
  public int[] reverseLifeline(int target, List<? extends Place> placesList) {
    final int nbPlaces = placesList.size();
    final ConfigurableHypercubeStrategy chs = new ConfigurableHypercubeStrategy();
    chs.setL(computeL(nbPlaces));
    chs.setZ(computeZ(chs.getL(), nbPlaces));
    return chs.reverseLifeline(target, placesList);
  }

  public int computeL(int numPlaces) {
    int l = 1;
    while (Math.pow(l, l) < numPlaces) {
      l++;
    }
    return l;
  }

  public int computeZ(int l, int numPlaces) {
    int z0 = 1;
    int zz = l;
    while (zz < numPlaces) {
      z0++;
      zz *= l;
    }
    return z0;
  }
}
