package com.leaprnd.observer4j;

import org.jetbrains.annotations.NonBlocking;

import java.util.Optional;

/**
 * Implementations of this interface will be notified of changes to a
 * {@link Listenable} immediately after they occur.
 */
public sealed interface SynchronousListener<
	T
> extends Listener<T>permits StrongSynchronousListener,WeakSynchronousListener {

	@Override
	default <X extends T> Optional<? extends X> relistenTo(Listenable<X> listenable) {
		return listenable.relistenWith(this);
	}

	@Override
	default <X extends T> boolean unlistenTo(Listenable<X> listenable) {
		return listenable.unlistenWith(this);
	}

	/**
	 * This method will be called sequentially on all the
	 * {@link SynchronousListener} of a {@link Listenable} each time a relevant
	 * change occurs. It is therefore important that this implementations of this
	 * method do not block.
	 *
	 * @param before The old value. The first time a {@link Listenable} invokes this
	 *               method, this parameter will be the exact same object that was
	 *               returned by {@link Listenable#listenWith(Listener<? super T>)}
	 *               when the listener was registered. On subsequent invocations,
	 *               the value of this parameter will be the exact same object as
	 *               the after parameter of the previous invocation.
	 * @param after  The new value.
	 */
	@NonBlocking
	void onUpdate(T before, T after);

}