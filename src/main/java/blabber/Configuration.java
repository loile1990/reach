package blabber;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

record Configuration(
	List<Integer> depths,
	boolean shuffle,
	IdentifierStrategy.Name identifierStrategyName,
	int identifierLength,
	PromptStrategy.Name promptStrategyName,
	int sampleSize,
	int padding,
	String model
) {
	String id() {
		return "%s-%s-%s-%s-%s-%d-%d".formatted(model, Collections.max(depths), shuffle, identifierStrategyName,
			promptStrategyName, padding, sampleSize);
	}

	Path datasetPath() {
		return Path.of("dataset", id());
	}

	IdentifierStrategy identifierStrategy() {
		return IdentifierStrategy.of(identifierStrategyName);
	}

	PromptStrategy promptStrategy() {
		return PromptStrategy.of(promptStrategyName);
	}
}
