package com.leaprnd.observer4j;

/**
 * An instances of this interface will only be weakly referenced by the
 * {@link Listenable}(s) it is listening to. If there are no other references to
 * the listener, the {@link Listenable} will automatically remove it.
 */
public non-sealed interface WeakSynchronousListener<T> extends SynchronousListener<T> {
	@Override
	default <X extends T> X listenTo(Listenable<X> listenable) {
		return listenable.listenWith(this);
	}
}
