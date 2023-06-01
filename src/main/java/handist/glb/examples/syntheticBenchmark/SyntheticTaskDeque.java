package handist.glb.examples.syntheticBenchmark;

import java.io.Serializable;
import java.util.AbstractCollection;
import java.util.Arrays;
import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Queue;
import java.util.Spliterator;
import java.util.Stack;
import java.util.function.Consumer;

/**
 * Resizable-array implementation of the {@link Deque} interface. Array deques
 * have no capacity restrictions; they grow as necessary to support usage. They
 * are not thread-safe; in the absence of external synchronization, they do not
 * support concurrent access by multiple threads. Null elements are prohibited.
 * This class is likely to be faster than {@link Stack} when used as a stack,
 * and faster than {@link LinkedList} when used as a queue.
 *
 * <p>
 * Most {@code ArrayDeque} operations run in amortized constant time. Exceptions
 * include {@link #remove(Object) remove}, {@link #removeFirstOccurrence
 * removeFirstOccurrence}, {@link #removeLastOccurrence removeLastOccurrence},
 * {@link #contains contains}, {@link #iterator iterator.remove()}, and the bulk
 * operations, all of which run in linear time.
 *
 * <p>
 * The iterators returned by this class'timestamps {@code iterator} method are
 * <i>fail-fast</i>: If the deque is modified at any time after the iterator is
 * created, in any way except through the iterator'timestamps own {@code remove}
 * method, the iterator will generally throw a
 * {@link ConcurrentModificationException}. Thus, in the face of concurrent
 * modification, the iterator fails quickly and cleanly, rather than risking
 * arbitrary, non-deterministic behavior at an undetermined time in the future.
 *
 * <p>
 * Note that the fail-fast behavior of an iterator cannot be guaranteed as it
 * is, generally speaking, impossible to make any hard guarantees in the
 * presence of un concurrent modification. Fail-fast iterators throw
 * {@code ConcurrentModificationException} on a best-effort basis. Therefore, it
 * would be wrong to write a program that depended on this exception for its
 * correctness: <i>the fail-fast behavior of iterators should be used only to
 * detect bugs.</i>
 *
 * <p>
 * This class and its iterator implement all of the <em>optional</em> methods of
 * the {@link Collection} and {@link Iterator} interfaces.
 *
 * <p>
 * This class is a member of the
 * <a href="{@docRoot}/../technotes/guides/collections/index.html"> Java
 * Collections Framework</a>.
 *
 * @author Josh Bloch and Doug Lea
 * @since 1.6
 */
public class SyntheticTaskDeque extends AbstractCollection<SyntheticTask>
		implements Deque<SyntheticTask>, Cloneable, Serializable {

	private class DeqIterator implements Iterator<SyntheticTask> {

		/** Index of element to be returned by subsequent call to next. */
		private int cursor = head;

		/**
		 * Tail recorded at construction (also in remove), to stop iterator and also to
		 * check for comodification.
		 */
		private int fence = tail;

		/**
		 * Index of element returned by most recent call to next. Reset to -1 if element
		 * is deleted by a call to remove.
		 */
		private int lastRet = -1;

		@Override
		public void forEachRemaining(Consumer<? super SyntheticTask> action) {
			Objects.requireNonNull(action);
			final Object[] a = elements;
			final int m = a.length - 1, f = fence;
			int i = cursor;
			cursor = f;
			while (i != f) {
				final SyntheticTask e = (SyntheticTask) a[i];
				i = (i + 1) & m;
				if (e == null) {
					throw new ConcurrentModificationException();
				}
				action.accept(e);
			}
		}

		@Override
		public boolean hasNext() {
			return cursor != fence;
		}

		@Override
		public SyntheticTask next() {
			if (cursor == fence) {
				throw new NoSuchElementException();
			}
			final SyntheticTask result = elements[cursor];
			// This check doesn't catch all possible comodifications,
			// but does catch the ones that corrupt traversal
			if (tail != fence || result == null) {
				throw new ConcurrentModificationException();
			}
			lastRet = cursor;
			cursor = (cursor + 1) & (elements.length - 1);
			return result;
		}

		@Override
		public void remove() {
			if (lastRet < 0) {
				throw new IllegalStateException();
			}
			if (delete(lastRet)) { // if left-shifted, undo increment in next()
				cursor = (cursor - 1) & (elements.length - 1);
				fence = tail;
			}
			lastRet = -1;
		}
	}

	@SuppressWarnings("hiding")
	static final class DeqSpliterator<SyntheticTask> implements Spliterator<SyntheticTask> {

		private final SyntheticTaskDeque deq;
		private int fence; // -1 until first use
		private int index; // current index, modified on traverse/split

		/** Creates new spliterator covering the given array and range */
		DeqSpliterator(SyntheticTaskDeque deq, int origin, int fence) {
			this.deq = deq;
			index = origin;
			this.fence = fence;
		}

		@Override
		public int characteristics() {
			return Spliterator.ORDERED | Spliterator.SIZED | Spliterator.NONNULL | Spliterator.SUBSIZED;
		}

		@Override
		public long estimateSize() {
			int n = getFence() - index;
			if (n < 0) {
				n += deq.elements.length;
			}
			return n;
		}

		@Override
		public void forEachRemaining(Consumer<? super SyntheticTask> consumer) {
			if (consumer == null) {
				throw new NullPointerException();
			}
			final Object[] a = deq.elements;
			final int m = a.length - 1, f = getFence();
			int i = index;
			index = f;
			while (i != f) {
				@SuppressWarnings("unchecked")
				final SyntheticTask e = (SyntheticTask) a[i];
				i = (i + 1) & m;
				if (e == null) {
					throw new ConcurrentModificationException();
				}
				consumer.accept(e);
			}
		}

		private int getFence() { // force initialization
			int t = fence;
			if (t < 0) {
				t = fence = deq.tail;
				index = deq.head;
			}
			return t;
		}

		@Override
		public boolean tryAdvance(Consumer<? super SyntheticTask> consumer) {
			if (consumer == null) {
				throw new NullPointerException();
			}
			final Object[] a = deq.elements;
			@SuppressWarnings("unused")
			final int m = a.length - 1, f = getFence(), i = index;
			if (i != fence) {
				@SuppressWarnings("unchecked")
				final SyntheticTask e = (SyntheticTask) a[i];
				index = (i + 1) & m;
				if (e == null) {
					throw new ConcurrentModificationException();
				}
				consumer.accept(e);
				return true;
			}
			return false;
		}

		@Override
		public DeqSpliterator<SyntheticTask> trySplit() {
			int t = getFence();
			final int h = index, n = deq.elements.length;
			if (h != t && ((h + 1) & (n - 1)) != t) {
				if (h > t) {
					t += n;
				}
				final int m = ((h + t) >>> 1) & (n - 1);
				return new DeqSpliterator<>(deq, h, index = m);
			}
			return null;
		}
	}

	private class DescendingIterator implements Iterator<SyntheticTask> {

		/*
		 * This class is nearly a mirror-image of DeqIterator, using tail instead of
		 * head for initial cursor, and head instead of tail for fence.
		 */
		private int cursor = tail;
		private int fence = head;
		private int lastRet = -1;

		@Override
		public boolean hasNext() {
			return cursor != fence;
		}

		@Override
		public SyntheticTask next() {
			if (cursor == fence) {
				throw new NoSuchElementException();
			}
			cursor = (cursor - 1) & (elements.length - 1);
			final SyntheticTask result = elements[cursor];
			if (head != fence || result == null) {
				throw new ConcurrentModificationException();
			}
			lastRet = cursor;
			return result;
		}

		@Override
		public void remove() {
			if (lastRet < 0) {
				throw new IllegalStateException();
			}
			if (!delete(lastRet)) {
				cursor = (cursor + 1) & (elements.length - 1);
				fence = head;
			}
			lastRet = -1;
		}
	}

	/**
	 * The minimum capacity that we'll use for a newly created deque. Must be a
	 * power of 2.
	 */
	private static final int MIN_INITIAL_CAPACITY = 64;

	private static final long serialVersionUID = 2340985798034038923L;

	/**
	 * The array in which the elements of the deque are stored. The capacity of the
	 * deque is the length of this array, which is always a power of two. The array
	 * is never allowed to become full, except transiently within an addX method
	 * where it is resized (see doubleCapacity) immediately upon becoming full, thus
	 * avoiding head and tail wrapping around to equal each other. We also guarantee
	 * that all array cells not holding deque elements are always null.
	 */
	transient SyntheticTask[] elements; // non-private to simplify nested class access

	/**
	 * The index of the element at the head of the deque (which is the element that
	 * would be removed by remove() or pop()); or an arbitrary number equal to tail
	 * if the deque is empty.
	 */
	transient int head;

	// ****** Array allocation and resizing utilities ******
	/**
	 * The index at which the next element would be added to the tail of the deque
	 * (via addLast(E), add(E), or push(E)).
	 */
	transient int tail;

	/**
	 * Constructs an empty array deque with an initial capacity sufficient to hold
	 * 16 elements.
	 */
	public SyntheticTaskDeque() {
		elements = new SyntheticTask[MIN_INITIAL_CAPACITY * 2];
	}

	/**
	 * Constructs a deque containing the elements of the specified collection, in
	 * the order they are returned by the collection'timestamps iterator. (The first
	 * element returned by the collection'timestamps iterator becomes the first
	 * element, or <i>front</i> of the deque.)
	 *
	 * @param c the collection whose elements are to be placed into the deque
	 * @throws NullPointerException if the specified collection is null
	 */
	public SyntheticTaskDeque(Collection<? extends SyntheticTask> c) {
		allocateElements(c.size());
		addAll(c);
	}

	// The main insertion and extraction methods are addFirst,
	// addLast, pollFirst, pollLast. The other methods are defined in
	// terms of these.

	/**
	 * Constructs an empty array deque with an initial capacity sufficient to hold
	 * the specified number of elements.
	 *
	 * @param numElements lower bound on initial capacity of the deque
	 */
	public SyntheticTaskDeque(int numElements) {
		allocateElements(numElements);
	}

	/**
	 * Inserts the specified element at the front of this deque.
	 *
	 * @param e the element to add
	 * @throws NullPointerException if the specified element is null
	 */
	@Override
	public void addFirst(SyntheticTask e) {
		if (e == null) {
			throw new NullPointerException();
		}
		elements[head = (head - 1) & (elements.length - 1)] = e;
		if (head == tail) {
			doubleCapacity();
		}
	}

	/**
	 * Inserts the specified element at the end of this deque.
	 *
	 * <p>
	 * This method is equivalent to {@link #add}.
	 *
	 * @param e the element to add
	 * @throws NullPointerException if the specified element is null
	 */
	@Override
	public void addLast(SyntheticTask e) {
		if (e == null) {
			throw new NullPointerException();
		}
		elements[tail] = e;
		tail = (tail + 1) & (elements.length - 1);
		if (tail == head) {
			doubleCapacity();
		}
	}

	/**
	 * Allocates empty array to hold the given number of elements.
	 *
	 * @param numElements the number of elements to hold
	 */
	private void allocateElements(int numElements) {
		int initialCapacity = MIN_INITIAL_CAPACITY;
		// Find the best power of two to hold elements.
		// Tests "<=" because arrays aren't kept full.
		if (numElements >= initialCapacity) {
			initialCapacity = numElements;
			initialCapacity |= (initialCapacity >>> 1);
			initialCapacity |= (initialCapacity >>> 2);
			initialCapacity |= (initialCapacity >>> 4);
			initialCapacity |= (initialCapacity >>> 8);
			initialCapacity |= (initialCapacity >>> 16);
			initialCapacity++;

			if (initialCapacity < 0) // Too many elements, must back off
			{
				initialCapacity >>>= 1; // Good luck allocating 2 ^ 30 elements
			}
		}
		elements = new SyntheticTask[initialCapacity];
	}

	private void checkInvariants() {
		assert elements[tail] == null;
		assert head == tail ? elements[head] == null
				: (elements[head] != null && elements[(tail - 1) & (elements.length - 1)] != null);
		assert elements[(head - 1) & (elements.length - 1)] == null;
	}

	/**
	 * Removes all of the elements from this deque. The deque will be empty after
	 * this call returns.
	 */
	@Override
	public void clear() {
		final int h = head;
		final int t = tail;
		if (h != t) { // clear all cells
			head = tail = 0;
			int i = h;
			final int mask = elements.length - 1;
			do {
				elements[i] = null;
				i = (i + 1) & mask;
			} while (i != t);
		}
	}

	/**
	 * Returns a copy of this deque.
	 *
	 * @return a copy of this deque
	 */
	@Override
	public SyntheticTaskDeque clone() {
		try {
			final SyntheticTaskDeque result = (SyntheticTaskDeque) super.clone();
			result.elements = Arrays.copyOf(elements, elements.length);
			return result;
		} catch (final CloneNotSupportedException e) {
			throw new AssertionError();
		}
	}

	/**
	 * Returns {@code true} if this deque contains the specified element. More
	 * formally, returns {@code true} if and only if this deque contains at least
	 * one element {@code e} such that {@code
	 * o.equals(e)}.
	 *
	 * @param o object to be checked for containment in this deque
	 * @return {@code true} if this deque contains the specified element
	 */
	public boolean contains(SyntheticTask o) {
		if (o == null) {
			return false;
		}
		final int mask = elements.length - 1;
		int i = head;
		SyntheticTask x;
		while ((x = elements[i]) != null) {
			if (o.equals(x)) {
				return true;
			}
			i = (i + 1) & mask;
		}
		return false;
	}

	/**
	 * Copies the elements from our element array into the specified array, in order
	 * (from first to last element in the deque). It is assumed that the array is
	 * large enough to hold all elements in the deque.
	 *
	 * @return its argument
	 */
	private SyntheticTask[] copyElements(SyntheticTask[] a) {
		if (head < tail) {
			System.arraycopy(elements, head, a, 0, size());
		} else if (head > tail) {
			final int headPortionLen = elements.length - head;
			System.arraycopy(elements, head, a, 0, headPortionLen);
			System.arraycopy(elements, 0, a, headPortionLen, tail);
		}
		return a;
	}

	/**
	 * Removes the element at the specified position in the elements array,
	 * adjusting head and tail as necessary. This can result in motion of elements
	 * backwards or forwards in the array.
	 *
	 * <p>
	 * This method is called delete rather than remove to emphasize that its
	 * semantics differ from those of {@link List#remove(int)}.
	 *
	 * @return true if elements moved backwards
	 */
	private boolean delete(int i) {
		checkInvariants();
		final SyntheticTask[] elements = this.elements;
		final int mask = elements.length - 1;
		final int h = head;
		final int t = tail;
		final int front = (i - h) & mask;
		final int back = (t - i) & mask;

		// Invariant: head <= i < tail mod circularity
		if (front >= ((t - h) & mask)) {
			throw new ConcurrentModificationException();
		}

		// Optimize for least element motion
		if (front < back) {
			if (h <= i) {
				System.arraycopy(elements, h, elements, h + 1, front);
			} else { // Wrap around
				System.arraycopy(elements, 0, elements, 1, i);
				elements[0] = elements[mask];
				System.arraycopy(elements, h, elements, h + 1, mask - h);
			}
			elements[h] = null;
			head = (h + 1) & mask;
			return false;
		}
		if (i < t) { // Copy the null tail as well
			System.arraycopy(elements, i + 1, elements, i, back);
			tail = t - 1;
		} else { // Wrap around
			System.arraycopy(elements, i + 1, elements, i, mask - i);
			elements[mask] = elements[0];
			System.arraycopy(elements, 1, elements, 0, t);
			tail = (t - 1) & mask;
		}
		return true;
	}

	@Override
	public Iterator<SyntheticTask> descendingIterator() {
		return new DescendingIterator();
	}

	/**
	 * Doubles the capacity of this deque. Call only when full, i.e., when head and
	 * tail have wrapped around to become equal.
	 */
	private void doubleCapacity() {
		assert head == tail;
		final int p = head;
		final int n = elements.length;
		final int r = n - p; // number of elements to the right of p
		final int newCapacity = n << 1;
		if (newCapacity < 0) {
			throw new IllegalStateException("Sorry, deque too big");
		}
		final SyntheticTask[] a = new SyntheticTask[newCapacity];
		System.arraycopy(elements, p, a, 0, r);
		System.arraycopy(elements, 0, a, r, p);
		elements = a;
		head = 0;
		tail = n;
	}

	/**
	 * Retrieves, but does not remove, the head of the queue represented by this
	 * deque. This method differs from {@link #peek peek} only in that it throws an
	 * exception if this deque is empty.
	 *
	 * <p>
	 * This method is equivalent to {@link #getFirst}.
	 *
	 * @return the head of the queue represented by this deque
	 * @throws NoSuchElementException {@inheritDoc}
	 */
	@Override
	public SyntheticTask element() {
		throw new UnsupportedOperationException();
		// return getFirst();
	}

	/** @throws NoSuchElementException {@inheritDoc} */
	@Override
	public SyntheticTask getFirst() {
		final SyntheticTask result = elements[head];
		if (result == null) {
			throw new NoSuchElementException();
		}
		return result;
	}

	// *** Queue methods ***

	/**
	 * Inserts the specified element at the end of this deque.
	 *
	 * <p>
	 * This method is equivalent to {@link #addLast}.
	 *
	 * @param e the element to add
	 * @return {@code true} (as specified by {@link Collection#add})
	 * @throws NullPointerException if the specified element is null
	 */
	// public boolean add(SyntheticTask e) {
	// addLast(e);
	// return true;
	// }

	public SyntheticTask[] getFromFirst(int n) {
		final int toCopy = Math.min(n, size());
		if (0 == toCopy) {
			return new SyntheticTask[0];
		}
		final SyntheticTask[] result = this.peekFromFirst(toCopy);
		if (toCopy == size()) {
			// this.size = 0;
			head = 0;
			tail = 0;
			return result;
		}
		head += toCopy;
		head %= elements.length;
		// this.size -= toCopy;

		return result;
	}

	public SyntheticTask[] getFromLast(int n) {
		final int toCopy = Math.min(n, elements.length);
		if (0 == toCopy) {
			return new SyntheticTask[0];
		}
		final SyntheticTask[] result = peekFromLast(toCopy);
		if (toCopy == size()) {
			head = 0;
			tail = 0;
		} else {
			tail -= toCopy;
			tail += elements.length;
			tail %= elements.length;
		}
		return result;
	}

	/** @throws NoSuchElementException {@inheritDoc} */
	@Override
	public SyntheticTask getLast() {
		final SyntheticTask result = elements[(tail - 1) & (elements.length - 1)];
		if (result == null) {
			throw new NoSuchElementException();
		}
		return result;
	}

	/**
	 * Returns {@code true} if this deque contains no elements.
	 *
	 * @return {@code true} if this deque contains no elements
	 */
	@Override
	public boolean isEmpty() {
		return head == tail;
	}

	/**
	 * Returns an iterator over the elements in this deque. The elements will be
	 * ordered from first (head) to last (tail). This is the same order that
	 * elements would be dequeued (via successive calls to {@link #remove} or popped
	 * (via successive calls to {@link #pop}).
	 *
	 * @return an iterator over the elements in this deque
	 */
	@Override
	public Iterator<SyntheticTask> iterator() {
		return new DeqIterator();
	}

	// *** Stack methods ***

	private void normalize() {
		if (head == 0) {
			return;
		}
		final int n = elements.length;
		final SyntheticTask[] newElements = new SyntheticTask[n];
		final int firstCopy = n - head;
		System.arraycopy(elements, head, newElements, 0, firstCopy);
		final int secondCopy = n - firstCopy;
		System.arraycopy(elements, 0, newElements, firstCopy, secondCopy);
		tail = size();
		head = 0;
		elements = newElements;
	}

	/**
	 * Inserts the specified element at the end of this deque.
	 *
	 * <p>
	 * This method is equivalent to {@link #offerLast}.
	 *
	 * @param e the element to add
	 * @return {@code true} (as specified by {@link Queue#offer})
	 * @throws NullPointerException if the specified element is null
	 */
	@Override
	public boolean offer(SyntheticTask e) {
		// return offerLast(e);
		throw new UnsupportedOperationException();
	}

	/**
	 * Inserts the specified element at the front of this deque.
	 *
	 * @param e the element to add
	 * @return {@code true} (as specified by {@link Deque#offerFirst})
	 * @throws NullPointerException if the specified element is null
	 */
	@Override
	public boolean offerFirst(SyntheticTask e) {
		addFirst(e);
		return true;
	}

	/**
	 * Inserts the specified element at the end of this deque.
	 *
	 * @param e the element to add
	 * @return {@code true} (as specified by {@link Deque#offerLast})
	 * @throws NullPointerException if the specified element is null
	 */
	@Override
	public boolean offerLast(SyntheticTask e) {
		addLast(e);
		return true;
	}

	// *** Collection Methods ***

	/**
	 * Retrieves, but does not remove, the head of the queue represented by this
	 * deque, or returns {@code null} if this deque is empty.
	 *
	 * <p>
	 * This method is equivalent to {@link #peekFirst}.
	 *
	 * @return the head of the queue represented by this deque, or {@code null} if
	 *         this deque is empty
	 */
	@Override
	public SyntheticTask peek() {
		throw new UnsupportedOperationException();
		// return peekFirst();
	}

	@Override
	public SyntheticTask peekFirst() {
		// elements[head] is null if deque empty
		return elements[head];
	}

	public SyntheticTask[] peekFromFirst(int n) {
		return peekFromFirst(n, 0);
	}

	public SyntheticTask[] peekFromFirst(int n, int offset) {
		final int toCopy = Math.min(n, size() - offset);
		final SyntheticTask[] result = new SyntheticTask[toCopy];
		int remaining = toCopy;
		final int firstCopyFrom = head + offset;
		int firstCopy = Math.min(remaining, elements.length - firstCopyFrom);
		if (firstCopyFrom >= elements.length) {
			offset -= (elements.length - head);
			firstCopy = 0;
		} else {
			remaining -= firstCopy;
			offset = 0;
			System.arraycopy(elements, firstCopyFrom, result, 0, firstCopy);
		}
		if (remaining > 0) {
			System.arraycopy(elements, offset, result, firstCopy, remaining);
		}
		return result;
	}

	public SyntheticTask[] peekFromLast(int n) {
		final int toCopy = Math.min(n, elements.length);
		final SyntheticTask[] result = new SyntheticTask[toCopy];
		final int firstCopy = Math.min(toCopy, tail);
		final int secondCopy = toCopy - firstCopy;
		System.arraycopy(elements, tail - firstCopy, result, secondCopy, firstCopy);
		if (0 < secondCopy) {
			System.arraycopy(elements, elements.length - secondCopy, result, 0, secondCopy);
		}
		return result;
	}

	@Override
	public SyntheticTask peekLast() {
		return elements[(tail - 1) & (elements.length - 1)];
	}

	/**
	 * Retrieves and removes the head of the queue represented by this deque (in
	 * other words, the first element of this deque), or returns {@code null} if
	 * this deque is empty.
	 *
	 * <p>
	 * This method is equivalent to {@link #pollFirst}.
	 *
	 * @return the head of the queue represented by this deque, or {@code null} if
	 *         this deque is empty
	 */
	@Override
	public SyntheticTask poll() {
		throw new UnsupportedOperationException();
		// return pollFirst();
	}

	@Override
	public SyntheticTask pollFirst() {
		final int h = head;
		final SyntheticTask result = elements[h];
		// Element is null if deque empty
		if (result == null) {
			return null;
		}
		elements[h] = null; // Must null out slot
		head = (h + 1) & (elements.length - 1);
		return result;
	}

	@Override
	public SyntheticTask pollLast() {
		final int t = (tail - 1) & (elements.length - 1);
		final SyntheticTask result = elements[t];
		if (result == null) {
			return null;
		}
		elements[t] = null;
		tail = t;
		return result;
	}

	/**
	 * Pops an element from the stack represented by this deque. In other words,
	 * removes and returns the first element of this deque.
	 *
	 * <p>
	 * This method is equivalent to {@link #removeFirst()}.
	 *
	 * @return the element at the front of this deque (which is the top of the stack
	 *         represented by this deque)
	 * @throws NoSuchElementException {@inheritDoc}
	 */
	@Override
	public SyntheticTask pop() {
		throw new UnsupportedOperationException();
		// return removeFirst();
	}

	/**
	 * Pushes an element onto the stack represented by this deque. In other words,
	 * inserts the element at the front of this deque.
	 *
	 * <p>
	 * This method is equivalent to {@link #addFirst}.
	 *
	 * @param e the element to push
	 * @throws NullPointerException if the specified element is null
	 */
	@Override
	public void push(SyntheticTask e) {
		throw new UnsupportedOperationException();
		// addFirst(e);
	}

	// *** Object methods ***

	public void pushArrayFirst(SyntheticTask[] elements) {
		normalize();
		final SyntheticTask[] oldElements = this.elements;
		final int oldSize = size();
		final int newSize = oldSize + elements.length;
		if (this.elements.length < (newSize + 1)) {
			allocateElements(newSize + 1);
		}
		System.arraycopy(oldElements, 0, this.elements, elements.length, oldSize);
		System.arraycopy(elements, 0, this.elements, 0, elements.length);
		tail = newSize;
		head = 0;
	}

	public void pushArrayLast(SyntheticTask[] elements) {
		normalize();
		final SyntheticTask[] oldElements = this.elements;
		final int oldSize = size();
		final int newSize = oldSize + elements.length;
		if (this.elements.length < (newSize + 1)) {
			allocateElements(newSize + 1);
			System.arraycopy(oldElements, 0, this.elements, 0, oldSize);
		}
		System.arraycopy(elements, 0, this.elements, oldSize, elements.length);
		tail = newSize;
		head = 0;
	}

	/** Reconstitutes this deque from a stream (that is, deserializes it). */
	private void readObject(java.io.ObjectInputStream s) throws java.io.IOException, ClassNotFoundException {
		s.defaultReadObject();

		// Read in size and allocate array
		final int size = s.readInt();
		allocateElements(size);
		head = 0;
		tail = size;

		// Read in all elements in the proper order.
		for (int i = 0; i < size; i++) {
			elements[i] = (SyntheticTask) s.readObject();
		}
	}

	/**
	 * Retrieves and removes the head of the queue represented by this deque. This
	 * method differs from {@link #poll poll} only in that it throws an exception if
	 * this deque is empty.
	 *
	 * <p>
	 * This method is equivalent to {@link #removeFirst}.
	 *
	 * @return the head of the queue represented by this deque
	 * @throws NoSuchElementException {@inheritDoc}
	 */
	@Override
	public SyntheticTask remove() {
		throw new UnsupportedOperationException();
		// return removeFirst();
	}

	/**
	 * Removes a single instance of the specified element from this deque. If the
	 * deque does not contain the element, it is unchanged. More formally, removes
	 * the first element {@code e} such that {@code o.equals(e)} (if such an element
	 * exists). Returns {@code true} if this deque contained the specified element
	 * (or equivalently, if this deque changed as a result of the call).
	 *
	 * <p>
	 * This method is equivalent to {@link #removeFirstOccurrence(Object)}.
	 *
	 * @param o element to be removed from this deque, if present
	 * @return {@code true} if this deque contained the specified element
	 */
	public boolean remove(SyntheticTask o) {
		return removeFirstOccurrence(o);
	}

	/** @throws NoSuchElementException {@inheritDoc} */
	@Override
	public SyntheticTask removeFirst() {
		final SyntheticTask x = pollFirst();
		if (x == null) {
			throw new NoSuchElementException();
		}
		return x;
	}

	/**
	 * Removes the first occurrence of the specified element in this deque (when
	 * traversing the deque from head to tail). If the deque does not contain the
	 * element, it is unchanged. More formally, removes the first element {@code e}
	 * such that {@code o.equals(e)} (if such an element exists). Returns
	 * {@code true} if this deque contained the specified element (or equivalently,
	 * if this deque changed as a result of the call).
	 *
	 * @param o element to be removed from this deque, if present
	 * @return {@code true} if the deque contained the specified element
	 */
	@Override
	public boolean removeFirstOccurrence(Object o) {
		if (o == null) {
			return false;
		}
		final int mask = elements.length - 1;
		int i = head;
		Object x;
		while ((x = elements[i]) != null) {
			if (o.equals(x)) {
				delete(i);
				return true;
			}
			i = (i + 1) & mask;
		}
		return false;
	}

	/** @throws NoSuchElementException {@inheritDoc} */
	@Override
	public SyntheticTask removeLast() {
		final SyntheticTask x = pollLast();
		if (x == null) {
			throw new NoSuchElementException();
		}
		return x;
	}

	/**
	 * Removes the last occurrence of the specified element in this deque (when
	 * traversing the deque from head to tail). If the deque does not contain the
	 * element, it is unchanged. More formally, removes the last element {@code e}
	 * such that {@code o.equals(e)} (if such an element exists). Returns
	 * {@code true} if this deque contained the specified element (or equivalently,
	 * if this deque changed as a result of the call).
	 *
	 * @param o element to be removed from this deque, if present
	 * @return {@code true} if the deque contained the specified element
	 */
	@Override
	public boolean removeLastOccurrence(Object o) {
		if (o == null) {
			return false;
		}
		final int mask = elements.length - 1;
		int i = (tail - 1) & mask;
		Object x;
		while ((x = elements[i]) != null) {
			if (o.equals(x)) {
				delete(i);
				return true;
			}
			i = (i - 1) & mask;
		}
		return false;
	}

	/**
	 * Returns the number of elements in this deque.
	 *
	 * @return the number of elements in this deque
	 */
	@Override
	public int size() {
		return (tail - head) & (elements.length - 1);
	}

	/**
	 * Creates a <em><a href="Spliterator.html#binding">late-binding</a></em> and
	 * <em>fail-fast</em> {@link Spliterator} over the elements in this deque.
	 *
	 * <p>
	 * The {@code Spliterator} reports {@link Spliterator#SIZED},
	 * {@link Spliterator#SUBSIZED}, {@link Spliterator#ORDERED}, and
	 * {@link Spliterator#NONNULL}. Overriding implementations should document the
	 * reporting of additional characteristic values.
	 *
	 * @return a {@code Spliterator} over the elements in this deque
	 * @since 1.8
	 */
	@Override
	public Spliterator<SyntheticTask> spliterator() {
		return new DeqSpliterator<>(this, -1, -1);
	}

	/**
	 * Returns an array containing all of the elements in this deque in proper
	 * sequence (from first to last element).
	 *
	 * <p>
	 * The returned array will be "safe" in that no references to it are maintained
	 * by this deque. (In other words, this method must allocate a new array). The
	 * caller is thus free to modify the returned array.
	 *
	 * <p>
	 * This method acts as bridge between array-based and collection-based APIs.
	 *
	 * @return an array containing all of the elements in this deque
	 */
	@Override
	public SyntheticTask[] toArray() {
		return copyElements(new SyntheticTask[size()]);
	}

	/**
	 * Returns an array containing all of the elements in this deque in proper
	 * sequence (from first to last element); the runtime type of the returned array
	 * is that of the specified array. If the deque fits in the specified array, it
	 * is returned therein. Otherwise, a new array is allocated with the runtime
	 * type of the specified array and the size of this deque.
	 *
	 * <p>
	 * If this deque fits in the specified array with room to spare (i.e., the array
	 * has more elements than this deque), the element in the array immediately
	 * following the end of the deque is set to {@code null}.
	 *
	 * <p>
	 * Like the {@link #toArray()} method, this method acts as bridge between
	 * array-based and collection-based APIs. Further, this method allows precise
	 * control over the runtime type of the output array, and may, under certain
	 * circumstances, be used to save allocation costs.
	 *
	 * <p>
	 * Suppose {@code x} is a deque known to contain only strings. The following
	 * code can be used to dump the deque into a newly allocated array of
	 * {@code String}:
	 *
	 * <pre> {@code
	 * String[] y = x.toArray(new String[0]);
	 * }</pre>
	 *
	 * Note that {@code toArray(new Object[0])} is identical in function to
	 * {@code toArray()}.
	 *
	 * @param a the array into which the elements of the deque are to be stored, if
	 *          it is big enough; otherwise, a new array of the same runtime type is
	 *          allocated for this purpose
	 * @return an array containing all of the elements in this deque
	 * @throws ArrayStoreException  if the runtime type of the specified array is
	 *                              not a supertype of the runtime type of every
	 *                              element in this deque
	 * @throws NullPointerException if the specified array is null
	 */
	public SyntheticTask[] toArray(SyntheticTask[] a) {
		final int size = size();
		if (a.length < size) {
			a = new SyntheticTask[size];
		}
		copyElements(a);
		if (a.length > size) {
			a[size] = null;
		}
		return a;
	}

	/**
	 * Saves this deque to a stream (that is, serializes it).
	 *
	 * @serialData The current size ({@code int}) of the deque, followed by all of
	 *             its elements (each an object reference) in first-to-last order.
	 */
	private void writeObject(java.io.ObjectOutputStream s) throws java.io.IOException {
		s.defaultWriteObject();

		// Write out size
		s.writeInt(size());

		// Write out elements in order.
		final int mask = elements.length - 1;
		for (int i = head; i != tail; i = (i + 1) & mask) {
			s.writeObject(elements[i]);
		}
	}
}
