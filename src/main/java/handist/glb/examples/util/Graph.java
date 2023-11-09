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
package handist.glb.examples.util;

import java.io.Serializable;
import java.util.ArrayList;

public class Graph implements Serializable {

  private static final long serialVersionUID = -3841886646849773183L;
  private final int[] inDegreeMap; // in-degree for each vertex
  private final int N; // number of vertices

  /** The edgelist of a vertex is stored as offsetMap[i] to offsetmap[i+1] */
  private final int[] offsetMap;

  private final int[] reverseOffsetMap;
  private ArrayList<Integer>[] adjacencyList;

  /** This just contains a series of edges that are indexed by offsetMap */
  private int[] adjacencyMap;

  private int M; // number of edges

  @SuppressWarnings("unchecked")
  public Graph(int N) {
    this.N = N;
    M = 0;
    inDegreeMap = new int[N];
    offsetMap = new int[N + 1];
    reverseOffsetMap = new int[N];
    adjacencyMap = null;
    adjacencyList = new ArrayList[N];
    for (int i = 0; i < N; i++) {
      adjacencyList[i] = new ArrayList<>();
    }
  }

  /** Add an edge. We do not check if the edge exists! If an edge exists, its overwritten. */
  public void addEdge(int v, int w) {
    assert (adjacencyMap == null);
    adjacencyList[v].add(w);
    inDegreeMap[w] += 1;
    M += 1;
  }

  /**
   * Give the position of the beginning of my neighbors in the compressed notation of the graph.
   * This means that the compression must represent the graph in the CSR format.
   */
  public int begin(int v) {
    assert (adjacencyMap != null);
    assert (v < N);
    return offsetMap[v];
  }

  /**
   * Create the compressed representation. To be called only after all the edges have been added to
   * the vertex list. All we are doing here is iterating over all the edges over all the vertices
   * and populating the offsetMap and the adjacencyMap.
   */
  public void compress() {
    // The graph may be compressed already --- don't do anything in this case.
    if (adjacencyMap != null) {
      return;
    }

    // Create as many elements as edges.
    adjacencyMap = new int[M];

    // Start copying over from the first vertex onwards.
    int currentOffset = 0;
    for (int v = 0; v < N; ++v) {
      // Put in the starting offset for this vertex.
      offsetMap[v] = currentOffset;

      // Iterate over all the edges.
      final ArrayList<Integer> list = adjacencyList[v];
      for (final Integer element : list) {
        adjacencyMap[currentOffset] = element;
        currentOffset++;
      }
    }

    int offset = 0;
    for (int v = 0; v < N; ++v) {
      reverseOffsetMap[v] = offset;
      offset += inDegreeMap[v];
    }

    // assert that we have included every edge.
    assert (currentOffset == M);
    assert (offset == M);

    // set the offset of the sentinel
    offsetMap[N] = currentOffset;

    adjacencyList = null;
  }

  /**
   * Similar to above, but gives the location of one past the current vertex'timestamps neighbor
   * list end. The iteration space is therefore [begin, end).
   */
  public int end(int v) {
    assert (adjacencyMap != null);
    assert (v < N);
    return offsetMap[v + 1];
  }

  /** Get the adjacent node from index */
  public int getAdjacentVertexFromIndex(int wIndex) {
    assert (adjacencyMap != null);
    return adjacencyMap[wIndex];
  }

  /** Get a vertex'timestamps inDegree */
  public int getInDegree(int v) {
    return inDegreeMap[v];
  }

  /** Return the number of edges in the graph */
  public int numEdges() {
    return M;
  }

  /** Return the number of vertices in the graph */
  public int numVertices() {
    return N;
  }

  public int rev(int v) {
    assert (adjacencyMap != null);
    assert (v < N);
    return reverseOffsetMap[v];
  }

  /** Print out the graph as a string */
  @Override
  public String toString() {
    final StringBuilder outString = new StringBuilder();

    for (int v = 0; v < N; ++v) {
      for (final int w : adjacencyList[v]) {
        outString.append("(" + v + ", " + w + ")" + "\n");
      }
    }
    return outString.toString();
  }
}
