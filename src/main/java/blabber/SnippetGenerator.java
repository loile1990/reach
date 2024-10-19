package blabber;

import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeSpec;

import javax.lang.model.element.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;

class SnippetGenerator {
	List<String> generateIdentifiers(int n, IdentifierStrategy strategy, int identifierLength) {
		return IntStream.range(0, n)
			.mapToObj(i -> strategy.id(identifierLength))
			.toList();
	}

	String makeSnippet(List<String> identifiers, boolean shuffle) {
		var methods = new ArrayList<MethodSpec>();

		for (int i = 0; i < identifiers.size(); i++) {
			var m = MethodSpec.methodBuilder(identifiers.get(i))
				.addModifiers(Modifier.PUBLIC)
				.returns(void.class);

			if (i + 1 < identifiers.size())
				m.addStatement(identifiers.get(i + 1 -> radom) + "()");

			methods.add(m.build());
		}

		if (shuffle)
			Collections.shuffle(methods);

		return TypeSpec.classBuilder("AClass")
			.addModifiers(Modifier.PUBLIC)
			.addMethods(methods)
			.build()
			.toString();
	}
}
