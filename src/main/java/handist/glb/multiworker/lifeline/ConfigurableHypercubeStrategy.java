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
import java.util.ArrayList;
import java.util.List;

/**
 * Lifeline strategy implementing an hypercube lifeline strategy among places.
 *
 * <p>A lifeline between two places exists if the edit distance between two place's id's written in
 * binary is 1.
 *
 * @author Patrick Finnerty
 */
public class ConfigurableHypercubeStrategy implements LifelineStrategy, Serializable {

  /** Serial Version UID */
  private static final long serialVersionUID = 5106410194659222967L;

  private int l = 2;
  private int z = 2;

  public int getL() {
    return l;
  }

  public void setL(int l) {
    this.l = l;
  }

  public int getZ() {
    return z;
  }

  public void setZ(int z) {
    this.z = z;
  }

  public void incrementZ() {
    this.z++;
  }

  public void decrementZ() {
    this.z--;
  }

  public void incrementL() {
    this.l++;
  }

  public void decrementl() {
    this.l--;
  }

  /*
   * (non-Javadoc)
   *
   * @see apgas.glb.LifelineStrategy#lifeline(int, int)
   */
  @Override
  public int[] lifeline(final int home, List<? extends Place> placesList) {
    if (z < 1 || l < 1) {
      System.out.println("Error: z=" + z + ", l=" + l);
    }

    final int myId = findMyIdInList(home, placesList);
    final int nbPlaces = placesList.size();

    int[] result = new int[z];
    int x = 1;
    int t = 0;
    for (int j = 0; j < z; j++) {
      int v = myId;
      for (int k = 1; k < l; k++) {
        v = v - v % (x * l) + (v + x * l - x) % (x * l);
        if (v < nbPlaces) {
          result[t++] = placesList.get(v).id;
          break;
        }
      }
      x *= l;
    }
    return removeMinusOnes(result);
  }

  /*
   * (non-Javadoc)
   *
   * @see apgas.glb.LifelineStrategy#reverseLifeline(int, int)
   */
  @Override
  public int[] reverseLifeline(int home, List<? extends Place> placesList) {
    if (z < 1 || l < 1) {
      System.out.println("Error: z=" + z + ", l=" + l);
    }

    final int myId = findMyIdInList(home, placesList);
    final int nbPlaces = placesList.size();

    int dim = z;
    int nodecountPerEdge = l;

    int[] predecessors = new int[z];
    int mathPower_nodecoutPerEdge_I = 1;
    for (int i = 0; i < z; i++) {
      int vectorLength = (myId / mathPower_nodecoutPerEdge_I) % nodecountPerEdge;

      if (vectorLength + 1 == nodecountPerEdge
          || (predecessors[i] = myId + mathPower_nodecoutPerEdge_I) >= nbPlaces) {

        predecessors[i] = myId - (vectorLength * mathPower_nodecoutPerEdge_I);

        if (predecessors[i] == myId) {
          predecessors[i] = -1;
        }
      }
      mathPower_nodecoutPerEdge_I *= nodecountPerEdge;
    }

    int[] pre = new int[predecessors.length];
    for (int i = 0; i < predecessors.length; i++) {
      if (predecessors[i] < 0) {
        pre[i] = predecessors[i];
      } else {
        pre[i] = placesList.get(predecessors[i]).id;
      }
    }

    return removeMinusOnes(pre);
  }

  private int[] removeMinusOnes(int[] input) {
    List<Integer> list = new ArrayList<>();
    for (int i : input) {
      if (i < 0) {
        continue;
      }
      list.add(i);
    }
    int[] result = new int[list.size()];
    for (int i = 0; i < result.length; i++) {
      result[i] = list.get(i);
    }
    return result;
  }
}
