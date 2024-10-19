package blabber;

import org.apache.commons.lang.RandomStringUtils;

/**
 * An IdentifierStrategy specifies how method identifiers are generated
 */
public interface IdentifierStrategy {
	enum Name {
		NATURAL,
		ALPHANUMERIC
	}

	static IdentifierStrategy of(Name name) {
		return switch (name) {
			case NATURAL      -> new NaturalIdentifierStrategy();
			case ALPHANUMERIC -> new AlphanumericIdentifierStrategy();
		};
	}

	String id(int length);

	/**
	 * The ALPHANUMERIC strategy generates random alphanumeric identifiers of length {@code length}
	 */
	class AlphanumericIdentifierStrategy implements IdentifierStrategy {
		@Override
		public String id(int length) {
			return RandomStringUtils.randomAlphabetic(length).toLowerCase();
		}
	}

	/**
	 * The NATURAL strategy generates identifiers of the form {m1(), m2(), m3()...}
	 */
	class NaturalIdentifierStrategy implements IdentifierStrategy {
		int i = 0;

		@Override
		public String id(int length) {
			return String.format("m%d", ++i);
		}
	}
}
