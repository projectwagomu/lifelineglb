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

import java.io.Serializable;
import java.util.List;

import apgas.Place;

/**
 * Lifeline strategy implementing an hypercube lifeline strategy among places.
 *
 * <p>
 * A lifeline between two places exists if the edit distance between two place's
 * id's written in binary is 1.
 *
 * @author Patrick Finnerty
 */
public class KobeHypercubeStrategy implements LifelineStrategy, Serializable {

	/** Serial Version UID */
	private static final long serialVersionUID = 5106410194659222967L;

	/*
	 * (non-Javadoc)
	 *
	 * @see apgas.glb.LifelineStrategy#lifeline(int, int)
	 */
	@Override
	public int[] lifeline(final int home, List<? extends Place> placesList) {
		final int myId = findMyIdInList(home, placesList);
		final int nbPlaces = placesList.size();

		int count = 0;
		int mask = 1;
		int l;
		while ((l = myId ^ mask) < nbPlaces) {
			count++;
			mask *= 2;
		}

		final int toReturn[] = new int[count];

		mask = 1;
		int index = 0;
		while ((l = myId ^ mask) < nbPlaces) {
			toReturn[index] = placesList.get(l).id;
			index++;
			mask *= 2;
		}

		return toReturn;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see apgas.glb.LifelineStrategy#reverseLifeline(int, int)
	 */
	@Override
	public int[] reverseLifeline(int target, List<? extends Place> placesList) {
		return lifeline(target, placesList);
	}
}
