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
package handist.glb.multiworker;

import java.io.Serializable;
import java.util.List;

import apgas.Place;
import apgas.util.PlaceLocalObject;

/**
 * Factory class used to provide computation service instances to the
 * programmer.
 *
 * <p>
 * Some specific preparations need to be made for the distributed computation.
 * This class handles this process, providing the factory method that will make
 * these preparations and return a computation service ready for use.
 *
 * @author Patrick Finnerty
 */
public final class GLBFactory<R extends Fold<R> & Serializable, B extends Bag<B, R> & Serializable> {

	public GLBcomputer<R, B> setupGLB(List<? extends Place> listPlaces) {

		return PlaceLocalObject.make(listPlaces, GLBcomputer::new);
	}
}
