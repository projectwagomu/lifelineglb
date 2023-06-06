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
import java.util.ArrayList;
import java.util.List;

import apgas.Place;

/**
 * Lifeline strategy implementing a hypercube lifeline strategy among places.
 *
 * <p>
 * A lifeline between two places exists if the edit distance between two place
 * ids written in a certain base <em>z<em> is 1.
 *
 * @author Patrick Finnerty
 */
public class ConfigurableHypercubeStrategy implements LifelineStrategy, Serializable {

	/** Serial Version UID */
	private static final long serialVersionUID = 5106410194659222967L;

	private int l = 2;
	private int z = 2;

	public void decrementl() {
		l--;
	}

	public void decrementZ() {
		z--;
	}

	public int getL() {
		return l;
	}

	public int getZ() {
		return z;
	}

	public void incrementL() {
		l++;
	}

	public void incrementZ() {
		z++;
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

		final int[] result = new int[z];
		int x = 1;
		int t = 0;
		for (int j = 0; j < z; j++) {
			int v = myId;
			for (int k = 1; k < l; k++) {
				v = v - v % (x * l) + (v + x * l - x) % (x * l);
				if (v < nbPlaces) {
					result[t] = placesList.get(v).id;
					t++;
					break;
				}
			}
			x *= l;
		}
		return removeMinusOnes(result);
	}

	private int[] removeMinusOnes(int[] input) {
		final List<Integer> list = new ArrayList<>();
		for (final int i : input) {
			if (i < 0) {
				continue;
			}
			list.add(i);
		}
		final int[] result = new int[list.size()];
		for (int i = 0; i < result.length; i++) {
			result[i] = list.get(i);
		}
		return result;
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

		final int nodecountPerEdge = l;

		final int[] predecessors = new int[z];
		int mathPower_nodecoutPerEdge_I = 1;
		for (int i = 0; i < z; i++) {
			final int vectorLength = (myId / mathPower_nodecoutPerEdge_I) % nodecountPerEdge;

			if (vectorLength + 1 == nodecountPerEdge
					|| (predecessors[i] = myId + mathPower_nodecoutPerEdge_I) >= nbPlaces) {

				predecessors[i] = myId - (vectorLength * mathPower_nodecoutPerEdge_I);

				if (predecessors[i] == myId) {
					predecessors[i] = -1;
				}
			}
			mathPower_nodecoutPerEdge_I *= nodecountPerEdge;
		}

		final int[] pre = new int[predecessors.length];
		for (int i = 0; i < predecessors.length; i++) {
			if (predecessors[i] < 0) {
				pre[i] = predecessors[i];
			} else {
				pre[i] = placesList.get(predecessors[i]).id;
			}
		}

		return removeMinusOnes(pre);
	}

	public void setL(int l) {
		this.l = l;
	}

	public void setZ(int z) {
		this.z = z;
	}
}
