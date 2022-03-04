package com.leaprnd.observer4j;

/**
 * For implementations of this interface, every time you invoke
 * {@link #listenTo} you must later invoke {@link #unlistenTo} with the same
 * {@link Listenable}. Failure to call {@link #unlistenTo} will result in a
 * memory leak.
 */
public non-sealed interface StrongSynchronousListener<T> extends SynchronousListener<T> {
	@Override
	default <X extends T> X listenTo(Listenable<X> listenable) {
		return listenable.listenWith(this);
	}
}
