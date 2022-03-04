package com.leaprnd.observer4j;

import java.util.Optional;

public interface Listener<T> {

	/**
	 * Adds this {@link Listener} to the provided {@link Listenable} so that it will
	 * be notified when there is a new state.
	 *
	 * At a minimum, this {@link Listener} must be notified of any new state where
	 * at least one property has changed that was previously _observed_ via this
	 * {@link Listener}.
	 *
	 * @return The most recent state this {@link Listener} knows about if this
	 *         {@link Listener} was already listening the provided
	 *         {@link Listenable}. If this {@link Listener} was *not* already
	 *         listening the provided {@link Listenable}, then the current state
	 *         will be returned.
	 */
	<X extends T> X listenTo(Listenable<X> listenable);

	/**
	 * @return The most recent state, from the perspective of this {@link Listener}
	 *         if this {@link Listener} is listening the provided {@link Listenable}
	 *         or {@link Optional#empty()} if this {@link Listener} is *not*
	 *         listening the provided {@link Listenable}.
	 */
	<X extends T> Optional<? extends X> relistenTo(Listenable<X> listenable);

	/**
	 * Removes this {@link Listener} from the provided {@link Listenable} so that it
	 * will not be notified any future updates. However, this {@link Listener} will
	 * still be notified of any updates that occurred before this method was
	 * invoked.
	 *
	 * @return True if and only if this {@link Listener} was previously listening to
	 *         the provided {@link Listenable}.
	 */
	<X extends T> boolean unlistenTo(Listenable<X> listenable);

}