package com.leaprnd.observer4j;

final class Exceptions {

	private Exceptions() {}

	@SuppressWarnings("unchecked")
	public static <T extends Throwable> RuntimeException unchecked(Throwable toThrow) throws T {
		throw (T) toThrow;
	}

}
