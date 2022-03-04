package com.leaprnd.observer4j;

record ImmutablePersonProperties(
	long id,
	long version,
	String givenName,
	String familyName,
	double heightInMeters,
	double massInKilograms
) implements PersonProperties {
	public ImmutablePersonProperties immutableCopy() {
		return this;
	}
}
