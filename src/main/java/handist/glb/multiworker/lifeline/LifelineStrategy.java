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
package handist.glb.multiworker.lifeline;

import apgas.Place;
import java.util.List;

/**
 * Work stealing preferred channel for a GLBProcessor. When a place runs out of work, the {@link
 * LifelineStrategy} implementation determines which places the thief passively steals work from.
 * Those places are the called the 'lifelines'.
 *
 * <p>To be valid, a {@link LifelineStrategy} needs to satisfy several properties that are easily
 * explained in terms of graphs.
 *
 * <p>Consider the oriented graph whose vertices are the places of the system and where an edge from
 * vertex {@code A} to {@code B} means that {@code A} has a lifeline on {@code B} (A will steal from
 * B). A valid {@link LifelineStrategy} consists in a connected graph, i.e. there must be a path (in
 * one or several jumps) from each place to every other place. If this is not the case, some of the
 * places could starve since they could enter a state in which they will not able to steal any work,
 * defeating the purpose of the load balancer.
 *
 * <p>One implementation of this interface is provided in the library and used as the default:
 * {@link LifelineStrategy}.
 *
 * @author Patrick Finnerty
 */
public interface LifelineStrategy {

  /**
   * Needed for malleability for determine the temporary inside the placesList
   *
   * @param home place id
   * @param placesList list of the place participating in the computation
   * @return the index in the list of places of the "home" place
   */
  default int findMyIdInList(final int home, final List<? extends Place> placesList) {
    for (int i = 0; i < placesList.size(); i++) {
      if (placesList.get(i).id == home) {
        return i;
      }
    }
    return -1; // should never happen
  }

  /**
   * Gives the list of nodes that place {@code thief} can steal work from.
   *
   * @param thief id of the place stealing work
   * @param placesList list of places in the system
   * @return array containing the ids of the places place {@code thief} should steal from
   */
  int[] lifeline(int thief, List<? extends Place> placesList);

  /**
   * Gives the list of places that can steal work from place {@code target}.
   *
   * @param target id of the place victim of steals
   * @param placesList list of places in the system
   * @return array containing the ids of the places that can steal work from place {@code target}
   */
  int[] reverseLifeline(int target, List<? extends Place> placesList);
}
