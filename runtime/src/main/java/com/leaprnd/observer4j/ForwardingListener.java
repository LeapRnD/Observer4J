package com.leaprnd.observer4j;

import java.util.Optional;

public interface ForwardingListener<T> extends Listener<T> {

	Listener<? super T> delegate();

	@Override
	default <X extends T> X listenTo(Listenable<X> listenable) {
		return delegate().listenTo(listenable);
	}

	@Override
	default <X extends T> Optional<? extends X> relistenTo(Listenable<X> listenable) {
		return delegate().relistenTo(listenable);
	}

	@Override
	default <X extends T> boolean unlistenTo(Listenable<X> listenable) {
		return delegate().unlistenTo(listenable);
	}

}
