package com.leaprnd.observer4j;

import java.util.concurrent.Executor;

public abstract class AbstractListenableAdapter<T> extends AbstractListenable<T> {

	private final Listener<Object> listener;

	protected AbstractListenableAdapter() {
		this(DIRECT_EXECUTOR);
	}

	protected AbstractListenableAdapter(Executor executor) {
		super(executor);
		listener = new AbstractAsynchronousListener<>(executor) {
			@Override
			protected void onUpdate() {
				update(build(this));
			}
		};
	}

	protected final void initialize() {
		executor.execute(() -> initialize(build(listener)));
	}

	protected abstract T build(Listener<Object> listener);

}