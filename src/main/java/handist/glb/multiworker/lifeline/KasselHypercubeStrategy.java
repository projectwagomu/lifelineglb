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
 * Lifeline strategy implementing a hypercube lifeline strategy among places.
 *
 * <p>
 * A lifeline between two places exists if the edit distance between two places
 * ids written in binary is 1.
 *
 * @author Patrick Finnerty
 */
public class KasselHypercubeStrategy implements LifelineStrategy, Serializable {

	/** Serial Version UID */
	private static final long serialVersionUID = 5106410194659222967L;

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
}
