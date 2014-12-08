package rplus_tree;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

public class RPlus<T> {

	private final int maxEntries;
	private final int minEntries;
	private final int numDims;

	public Node root;

	private volatile int size;

	/**
	 * Creates a new RTree.
	 * 
	 * @param maxEntries
	 *            maximum number of entries per node
	 * @param minEntries
	 *            minimum number of entries per node (except for the root node)
	 * @param numDims
	 *            the number of dimensions of the RTree.
	 */
	public RPlus(int maxEntries, int minEntries, int numDims) {
		assert (minEntries <= (maxEntries / 2));
		this.numDims = numDims;
		this.maxEntries = maxEntries;
		this.minEntries = minEntries;
		root = buildRoot(true);
	}

	public void print() {
		this.root.print();
	}

	public RPlus(int maxEntries, int minEntries) {
		this(maxEntries, minEntries, 2);
	}

	private Node buildRoot(boolean asLeaf) {
		float[] initCoords = new float[numDims];
		float[] initDimensions = new float[numDims];
		for (int i = 0; i < this.numDims; i++) {
			initCoords[i] = (float) Math.sqrt(Float.MAX_VALUE);
			initDimensions[i] = -2.0f * (float) Math.sqrt(Float.MAX_VALUE);
		}
		return new Node(initCoords, initDimensions, asLeaf);
	}

	/**
	 * Builds a new RTree using default parameters: maximum 50 entries per node
	 * minimum 2 entries per node 2 dimensions
	 */
	public RPlus() {
		this(100, 2, 2);
	}

	/**
	 * @return the maximum number of entries per node
	 */
	public int getMaxEntries() {
		return maxEntries;
	}

	/**
	 * @return the minimum number of entries per node for all nodes except the
	 *         root.
	 */
	public int getMinEntries() {
		return minEntries;
	}

	/**
	 * @return the number of dimensions of the tree
	 */
	public int getNumDims() {
		return numDims;
	}

	/**
	 * @return the number of items in this tree.
	 */
	public int size() {
		return size;
	}

	/**
	 * Searches the RTree for objects overlapping with the given rectangle.
	 * 
	 * @param coords
	 *            the corner of the rectangle that is the lower bound of every
	 *            dimension (eg. the top-left corner)
	 * @param dimensions
	 *            the dimensions of the rectangle.
	 * @return a list of objects whose rectangles overlap with the given
	 *         rectangle.
	 */
	public List<T> search(float[] coords, float[] dimensions) {
		assert (coords.length == numDims);
		assert (dimensions.length == numDims);
		LinkedList<T> results = new LinkedList<T>();
		search(coords, dimensions, root, results);
		return results;
	}

	private void search(float[] coords, float[] dimensions, Node n,
			LinkedList<T> results) {
		if (n.leaf) {
			for (Node e : n.children) {

				if (isOverlap(coords, dimensions, e.coords, e.dimensions)) {
					if (!results.contains(((Entry) e).entry)) {
						// System.out.println(e.toString());
						results.add(((Entry) e).entry);
					}
				}
			}
		} else {
			for (Node c : n.children) {
				if (isOverlap(coords, dimensions, c.coords, c.dimensions)) {
					search(coords, dimensions, c, results);
				}
			}
		}
	}

	/**
	 * Inserts the given entry into the RTree, associated with the given
	 * rectangle.
	 * 
	 * @param coords
	 *            the corner of the rectangle that is the lower bound in every
	 *            dimension
	 * @param dimensions
	 *            the dimensions of the rectangle
	 * @param entry
	 *            the entry to insert
	 */
	public void insert(float[] coords, float[] dimensions, T entry) {
		assert (coords.length == numDims);
		assert (dimensions.length == numDims);

		Entry e = new Entry(coords, dimensions, entry);

		LinkedList<Node> leaves = new LinkedList<Node>();
		chooseLeaves(root, e, leaves);
		for (Node leaf : leaves) {
			leaf.children.add(e);
			e.parent = leaf;
			if (leaf.children.size() > maxEntries) {
				Node[] splits = splitNode(leaf);
				adjustTree(splits[0], splits[1]);
			} else {
				adjustTree(leaf, null);
			}
		}
		size++;
	}

	private void adjustTree(Node n, Node nn) {
		if (n == root) {
			if (nn != null) {
				// build new root and add children.
				root = buildRoot(false);
				root.children.add(n);
				n.parent = root;
				root.children.add(nn);
				nn.parent = root;
			}
			tighten(root);
			return;
		}
		tighten(n);
		if (nn != null) {
			tighten(nn);
			if (n.parent.children.size() > maxEntries) {
				Node[] splits = splitNode(n.parent);
				adjustTree(splits[0], splits[1]);
			}
		}
		if (n.parent != null) {
			adjustTree(n.parent, null);
		}
	}

	public LinkedList<Node> getAllLeaves() {
		LinkedList<Node> results = new LinkedList<Node>();
		searchLeaf(root, results);
		return results;
	}

	private void searchLeaf(Node n, LinkedList<Node> results) {
		if (n.leaf) {
			for (Node e : n.children) {
				results.add(e);
			}

		} else {
			for (Node c : n.children) {
				searchLeaf(c, results);
			}
		}
	}

	public Node[] splitNode(Node n) {
		@SuppressWarnings("unchecked")
		Node[] nn = new RPlus.Node[] { n,
				new Node(n.coords, n.dimensions, n.leaf) };
		nn[1].parent = n.parent;
		if (nn[1].parent != null) {
			nn[1].parent.children.add(nn[1]);
		}

		float[] cutInfo = evaluate(n.children);

		System.out.println("Total: " + n.children.size());
		LinkedList<Node> cc = new LinkedList<Node>(n.children);
		n.children.clear();

		System.out.println("Children: " + cc.size());

		for (Node c : cc) {
			Node[] result = assign(c, cutInfo);

			if (result[0] != null) {
				nn[0].children.add(result[0]);
				result[0].parent = nn[0];
			}
			if (result[1] != null) {
				nn[1].children.add(result[1]);
				result[1].parent = nn[1];
			}
		}

		System.out.println("Node_1: " + nn[0].children.size());
		System.out.println("Node_2: " + nn[1].children.size());

		tighten(nn);
		return nn;
	}

	public Node[] assign(Node n, float[] cutInfo) {
		int result = needCut(n, cutInfo);

		if (result == 1) {
			return new RPlus.Node[] { n, null };
		} else if (result == 2) {
			return new RPlus.Node[] { null, n };
		} else {

			Node[] nn = partition(n, cutInfo);

			return new RPlus.Node[] { nn[0], nn[1] };
		}

	}

	public int needCut(Node n, float[] cutInfo) {
		// 1: assign to the left part
		// 2: assign to the right part
		// 0: need to be cut
		int axis = (int) cutInfo[0];
		float cutLine = cutInfo[1];

		if (n.coords[axis] < cutLine
				&& n.coords[axis] + n.dimensions[axis] <= cutLine) {
			return 1;
		} else if (n.coords[axis] >= cutLine) {
			return 2;
		} else {
			return 0;
		}

	}

	public Node[] partition(Node n, float[] cutInfo) {
		int axis = (int) cutInfo[0];
		float cutLine = cutInfo[1];

		if (!(n instanceof RPlus.Entry)) {
			@SuppressWarnings("unchecked")
			Node[] nn = new RPlus.Node[] {
					new Node(n.coords, n.dimensions, n.leaf),
					new Node(n.coords, n.dimensions, n.leaf) };
			nn[0].dimensions[axis] = cutLine - nn[0].coords[axis];
			nn[1].coords[axis] = cutLine;
			nn[1].dimensions[axis] = n.dimensions[axis]
					- nn[0].dimensions[axis];

			for (Node c : n.children) {
				int result = needCut(c, cutInfo);
				if (result == 1) {
					nn[0].children.add(c);
					c.parent = nn[0];
				} else if (result == 2) {
					nn[1].children.add(c);
					c.parent = nn[1];
				} else {
					Node[] splits = partition(c, cutInfo);
					nn[0].children.add(splits[0]);
					splits[0].parent = nn[0];
					nn[1].children.add(splits[1]);
					splits[1].parent = nn[1];
				}
			}

			return nn;
		} else {
			@SuppressWarnings("unchecked")
			Entry[] ee = new RPlus.Entry[] {
					new RPlus.Entry(n.coords, n.dimensions,
							((RPlus.Entry) n).entry),
					new RPlus.Entry(n.coords, n.dimensions,
							((RPlus.Entry) n).entry) };
			ee[0].dimensions[axis] = cutLine - ee[0].coords[axis];
			ee[1].coords[axis] = cutLine;
			ee[1].dimensions[axis] = n.dimensions[axis]
					- ee[0].dimensions[axis];

			return ee;
		}
	}

	public float[] evaluate(LinkedList<Node> children) {
		float cutLine_x = sweep(children, 0);
		float cutLine_y = sweep(children, 1);
		float[] result;

		if (cutLine_x == Float.MIN_VALUE || cutLine_y == Float.MIN_VALUE) {
			result = cutLine_x == Float.MIN_VALUE ? new float[] { 1, cutLine_y }
					: new float[] { 0, cutLine_x };
			return result;
		}

		// evaluate x-axis
		int num_cut_x = 0;
		for (Node c : children) {
			if (c.coords[0] < cutLine_x
					&& c.coords[0] + c.dimensions[0] > cutLine_x) {
				num_cut_x++;
			}
		}

		// evaluate y-axis
		int num_cut_y = 0;
		for (Node c : children) {
			if (c.coords[1] < cutLine_y
					&& c.coords[1] + c.dimensions[1] > cutLine_y) {
				num_cut_y++;
			}
		}

		result = (num_cut_x < num_cut_y) ? new float[] { 0, cutLine_x }
				: new float[] { 1, cutLine_y };

		return result;
	}

	public float sweep(LinkedList<Node> children, int axis) {
		float[] temp_left = new float[children.size()];
		float[] temp_right = new float[children.size()];
		float cutLine;

		for (int i = 0; i < children.size(); i++) {
			temp_left[i] = children.get(i).coords[axis];
			temp_right[i] = children.get(i).coords[axis]
					+ children.get(i).dimensions[axis];
		}
		Arrays.sort(temp_left);
		Arrays.sort(temp_right);

		// cases which can not cut
		if (temp_left[0] == temp_left[children.size() - 1]
				|| temp_right[0] == temp_right[children.size() - 1]) {
			cutLine = Float.MIN_VALUE;
			return cutLine;
		}

		cutLine = temp_left[children.size() / 2];

		// revise bad cut-line
		if (temp_left[0] == cutLine) {
			// find the first distinct element
			for (int i = children.size() / 2 + 1; i < children.size(); i++) {
				if (temp_left[i] != cutLine) {
					cutLine = temp_left[i];
					break;
				}
			}
		}

		return cutLine;
	}

	private void tighten(Node... nodes) {
		assert (nodes.length >= 1) : "Pass some nodes to tighten!";
		for (Node n : nodes) {
			//assert (n.children.size() > 0) : "tighten() called on empty node!";
			//assert (n.children.size() <= this.maxEntries);
			float[] minCoords = new float[numDims];
			float[] maxCoords = new float[numDims];
			for (int i = 0; i < numDims; i++) {
				minCoords[i] = Float.MAX_VALUE;
				maxCoords[i] = Float.MIN_VALUE;

				for (Node c : n.children) {
					// we may have bulk-added a bunch of children to a node (eg.
					// in
					// splitNode)
					// so here we just enforce the child->parent relationship.
					c.parent = n;
					if (c.coords[i] < minCoords[i]) {
						minCoords[i] = c.coords[i];
					}
					if ((c.coords[i] + c.dimensions[i]) > maxCoords[i]) {
						maxCoords[i] = (c.coords[i] + c.dimensions[i]);
					}
				}
			}
			for (int i = 0; i < numDims; i++) {
				// Convert max coords to dimensions
				maxCoords[i] -= minCoords[i];
			}
			System.arraycopy(minCoords, 0, n.coords, 0, numDims);
			System.arraycopy(maxCoords, 0, n.dimensions, 0, numDims);
		}
	}

	private void chooseLeaves(RPlus<T>.Node n, RPlus<T>.Entry e,
			LinkedList<Node> result) {
		if (n.leaf || n.children.size() == 0) {
			result.add(n);
			return;
		}

		boolean overlap = false;

		for (Node c : n.children) {
			if (this.isOverlap(c.coords, c.dimensions, e.coords, e.dimensions)) {
				chooseLeaves(c, e, result);
				overlap = true;
			}
		}

		if (!overlap) {
			float minInc = Float.MAX_VALUE;
			Node next = null;
			for (RPlus<T>.Node c : n.children) {
				float inc = getRequiredExpansion(c.coords, c.dimensions, e);
				if (inc < minInc) {
					minInc = inc;
					next = c;
				} else if (inc == minInc) {
					float curArea = 1.0f;
					float thisArea = 1.0f;
					for (int i = 0; i < c.dimensions.length; i++) {
						curArea *= next.dimensions[i];
						thisArea *= c.dimensions[i];
					}
					if (thisArea < curArea) {
						next = c;
					}
				}
			}
			chooseLeaves(next, e, result);
		}

	}

	/**
	 * Returns the increase in area necessary for the given rectangle to cover
	 * the given entry.
	 */
	private float getRequiredExpansion(float[] coords, float[] dimensions,
			Node e) {
		float area = getArea(dimensions);
		float[] deltas = new float[dimensions.length];
		for (int i = 0; i < deltas.length; i++) {
			if (coords[i] + dimensions[i] < e.coords[i] + e.dimensions[i]) {
				deltas[i] = e.coords[i] + e.dimensions[i] - coords[i]
						- dimensions[i];
			} else if (coords[i] + dimensions[i] > e.coords[i]
					+ e.dimensions[i]) {
				deltas[i] = coords[i] - e.coords[i];
			}
		}
		float expanded = 1.0f;
		for (int i = 0; i < dimensions.length; i++) {
			expanded *= dimensions[i] + deltas[i];
		}
		return (expanded - area);
	}

	private float getArea(float[] dimensions) {
		float area = 1.0f;
		for (int i = 0; i < dimensions.length; i++) {
			area *= dimensions[i];
		}
		return area;
	}

	private boolean isOverlap(float[] scoords, float[] sdimensions,
			float[] coords, float[] dimensions) {
		final float FUDGE_FACTOR = 1.001f;
		for (int i = 0; i < scoords.length; i++) {
			boolean overlapInThisDimension = false;
			if (scoords[i] == coords[i]) {
				overlapInThisDimension = true;
			} else if (scoords[i] < coords[i]) {
				if (scoords[i] + FUDGE_FACTOR * sdimensions[i] >= coords[i]) {
					overlapInThisDimension = true;
				}
			} else if (scoords[i] > coords[i]) {
				if (coords[i] + FUDGE_FACTOR * dimensions[i] >= scoords[i]) {
					overlapInThisDimension = true;
				}
			}
			if (!overlapInThisDimension) {
				return false;
			}
		}
		return true;
	}

	// SUB_CLASSES
	//
	//
	public class Node {
		float[] coords;
		float[] dimensions;
		LinkedList<Node> children;
		boolean leaf;

		Node parent;

		private Node(float[] coords, float[] dimensions, boolean leaf) {
			this.coords = new float[coords.length];
			this.dimensions = new float[dimensions.length];
			System.arraycopy(coords, 0, this.coords, 0, coords.length);
			System.arraycopy(dimensions, 0, this.dimensions, 0,
					dimensions.length);
			this.leaf = leaf;
			children = new LinkedList<Node>();
		}

		public void printAll(int depth) {
			int num_cell = children.size();
			if (num_cell == 0)
				return;
			System.out.println("Depth " + depth + " (" + num_cell + " cell:)");
			for (int i = 0; i < num_cell; i++) {
				Node temp = children.get(i);
				System.out.print((i + 1) + ". mbr: " + "(" + temp.coords[0]
						+ "," + temp.coords[1] + ") " + "("
						+ (temp.coords[0] + temp.dimensions[0]) + ","
						+ (temp.coords[1] + temp.dimensions[1] + ")"));
				System.out.print("      ");
			}
			System.out.println();
			for (int i = 0; i < num_cell; i++) {
				Node temp = children.get(i);
				temp.printAll(depth + 1);
			}
		}

		public void print() {
			this.printAll(0);
		}

		public float getArea() {
			float area = 1.0f;
			for (int i = 0; i < dimensions.length; i++) {
				area *= dimensions[i];
			}
			return area;
		}

	}

	private class Entry extends Node {
		final T entry;

		public Entry(float[] coords, float[] dimensions, T entry) {
			// an entry isn't actually a leaf (its parent is a leaf)
			// but all the algorithms should stop at the first leaf they
			// encounter,
			// so this little hack shouldn't be a problem.
			super(coords, dimensions, true);
			this.entry = entry;
		}

		public String toString() {
			return "Entry: " + entry;
		}
	}

}
