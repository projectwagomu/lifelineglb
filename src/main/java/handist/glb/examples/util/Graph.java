package handist.glb.examples.util;

import java.io.Serializable;
import java.util.ArrayList;

public class Graph implements Serializable {

  private final int N; // number of vertices
  private int M; // number of edges
  private int[] inDegreeMap; // in-degree for each vertex

  /** The edgelist of a vertex is stored as offsetMap[i] to offsetmap[i+1] */
  private final int[] offsetMap;

  private final int[] reverseOffsetMap;

  /** This just contains a series of edges that are indexed by offsetMap */
  private int[] adjacencyMap;

  private ArrayList<Integer>[] adjacencyList;

  @SuppressWarnings("unchecked")
  public Graph(int N) {
    this.N = N;
    this.M = 0;
    this.inDegreeMap = new int[N];
    this.offsetMap = new int[N + 1];
    this.reverseOffsetMap = new int[N];
    this.adjacencyMap = null;
    this.adjacencyList = new ArrayList[N];
    for (int i = 0; i < N; i++) {
      this.adjacencyList[i] = new ArrayList<>();
    }
  }

  /** Get the adjacent node from index */
  public int getAdjacentVertexFromIndex(int wIndex) {
    assert (adjacencyMap != null);
    return adjacencyMap[wIndex];
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
   * Similar to above, but gives the location of one past the current vertex'timestamps neighbor
   * list end. The iteration space is therefore [begin, end).
   */
  public int end(int v) {
    assert (adjacencyMap != null);
    assert (v < N);
    return offsetMap[v + 1];
  }

  public int rev(int v) {
    assert (adjacencyMap != null);
    assert (v < N);
    return reverseOffsetMap[v];
  }

  /** Return the number of vertices in the graph */
  public int numVertices() {
    return N;
  }

  /** Return the number of edges in the graph */
  public int numEdges() {
    return M;
  }

  /** Get a vertex'timestamps inDegree */
  public int getInDegree(int v) {
    return inDegreeMap[v];
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
      ArrayList<Integer> list = adjacencyList[v];
      for (int i = 0; i < list.size(); ++i) {
        adjacencyMap[currentOffset++] = list.get(i);
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

  /** Add an edge. We do not check if the edge exists! If an edge exists, its overwritten. */
  public void addEdge(int v, int w) {
    assert (adjacencyMap == null);
    adjacencyList[v].add(w);
    inDegreeMap[w] += 1;
    M += 1;
  }

  /** Print out the graph as a string */
  public String toString() {
    StringBuilder outString = new StringBuilder();

    for (int v = 0; v < N; ++v) {
      for (int w : adjacencyList[v]) {
        outString.append("(" + v + ", " + w + ")" + "\n");
      }
    }
    return outString.toString();
  }
}
