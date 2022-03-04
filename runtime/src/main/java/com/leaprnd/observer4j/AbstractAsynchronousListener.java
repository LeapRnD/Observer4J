package com.leaprnd.observer4j;

import java.util.concurrent.Executor;

import static com.leaprnd.observer4j.NullListener.NULL_LISTENER;

public abstract class AbstractAsynchronousListener<T> extends AbstractRefreshable implements ForwardingListener<T> {

	private volatile Listener<? super T> delegate = new RefreshingListener();

	protected AbstractAsynchronousListener(Executor executor) {
		super(executor);
	}

	private class RefreshingListener implements WeakSynchronousListener<T> {
		@Override
		public void onUpdate(T before, T after) {
			if (delegate == this) {
				requestAsynchronousRefresh();
			}
		}
	}

	@Override
	public final Listener<? super T> delegate() {
		return delegate;
	}

	@Override
	protected final void refresh(int numberOfRequests) {
		delegate = new RefreshingListener();
		onUpdate();
	}

	protected abstract void onUpdate();

	@Override
	protected void cleanup() {
		delegate = NULL_LISTENER;
	}

}
