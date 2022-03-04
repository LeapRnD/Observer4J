package com.leaprnd.observer4j;

@GenerateSubstitutableForwarder(name = "PersonPropertiesForwarder", delegate = PersonProperties.class)
interface PersonProperties {

	long id();
	long version();
	String givenName();
	String familyName();
	double heightInMeters();
	double massInKilograms();

	default ImmutablePersonProperties immutableCopy() {
		return new ImmutablePersonProperties(
			id(),
			version(),
			givenName(),
			familyName(),
			heightInMeters(),
			massInKilograms()
		);
	}

}
