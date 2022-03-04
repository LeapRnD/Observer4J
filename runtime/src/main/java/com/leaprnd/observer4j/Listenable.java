package com.leaprnd.observer4j;

import java.util.Optional;

/**
 * All implementations of this interface must guarantee that instances are
 * ineligible for garbage collection so long as there is one or more listeners.
 */
public interface Listenable<T> {
	T listenWith(Listener<? super T> listener);
	T listenWith(StrongSynchronousListener<? super T> listener);
	T listenWith(WeakSynchronousListener<? super T> listener);
	Optional<? extends T> relistenWith(Listener<? super T> listener);
	Optional<? extends T> relistenWith(SynchronousListener<? super T> listener);
	boolean unlistenWith(Listener<? super T> listener);
	boolean unlistenWith(SynchronousListener<? super T> listener);
	T takeSnapshot();
}