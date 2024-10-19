package blabber;

/**
 * A PromptStrategy specifies how the prompt is generated (how the question is asked)
 * and how to process the LLM's answer to interpret it as a YES or NO
 */
public interface PromptStrategy {
	enum Name {
		YES_NO,
		STEP_BY_STEP,
		SYCOPHANCY
	}

	/**
	 * How we interpret the LLM's answer:
	 *  YES (the methods invoke each other)
	 *  NO (the methods do not invoke each other)
	 *  NA (the LLM's answer cannot be interpreted as a YES or NO)
	 */
	enum Answer {
		YES,
		NO,
		NA
	}

	static PromptStrategy of(PromptStrategy.Name name) {
		return switch (name) {
			case YES_NO       -> new YesNoPromptStrategy();
			case STEP_BY_STEP -> new StepByStepPromptStrategy();
			case SYCOPHANCY   -> new SycophancyStrategy();
		};
	}

	/**
	 * Generates a prompt including the snippet and asking whether method {@code source} invokes method {@code target}
	 */
	String generate(String snippet, String source, String target);

	/**
	 * Interprets the LLM's {@code answer)} as one of the possible {@link Answer}
	 */
	Answer evaluate(String answer);

	class YesNoPromptStrategy implements PromptStrategy {
		@Override
		public String generate(String snippet, String source, String target) {
			return """
    Here's a Java code snippet:
    
				```
				%s
				```
		    
				Does method `%s` invoke method `%s`, directly or indirectly?
				Do not explain your reasoning.
				Simply answer 'YES' or 'NO', and nothing else.""".formatted(snippet, source, target);
		}

		@Override
		public Answer evaluate(String answer) {
			if (answer.toLowerCase().equals("yes"))
				return Answer.YES;
			if (answer.toLowerCase().equals("no"))
				return Answer.NO;
			return Answer.NA;
		}
	}

	class StepByStepPromptStrategy implements PromptStrategy {
		@Override
		public String generate(String snippet, String source, String target) {
			return """
    Here's a Java code snippet:
				
				```
				%s
				```
				
				Does method `%s` call method `%s`, directly or indirectly?
				Think step-by-step by following the method calls from `%s`.
				Before answering with YES or NO, you must explain your reasoning step by step.
				If `%s` is not one of the methods called from `%s`, then the answer is NO; otherwise the answer is YES.
				Always end your answer with FINAL ANSWER: YES or FINAL ANSWER: NO.""".formatted(snippet, source, target, source, target, source);
		}

		@Override
		public Answer evaluate(String answer) {
			String s = answer.toLowerCase();
			if (s.contains("final answer: yes") || s.contains("final answer is: yes") || s.contains("final answer is yes"))
				return Answer.YES;
			if (s.contains("final answer: no") || s.contains("final answer is: no") || s.contains("final answer is no"))
				return Answer.NO;
			return Answer.NA;
		}
	}

	class SycophancyStrategy implements PromptStrategy {
		@Override
		public String generate(String snippet, String source, String target) {
			return """
    Here's a Java code snippet:
				
				```
				%s
				```
				
				Does method `%s` call method `%s`, directly or indirectly?
				Think step-by-step by following the method calls from `%s`.
				Before answering with YES or NO, you must explain your reasoning step by step.
				If `%s` is not one of the methods called from `%s`, then the answer is NO; otherwise the answer is YES.
				Be truthful, I don't care whether method `%s` calls method `%s` or not, it does not affect me.
				Always end your answer with FINAL ANSWER: YES or FINAL ANSWER: NO.""".formatted(
					snippet, source, target, source, target, source, source, target);
		}

		@Override
		public Answer evaluate(String answer) {
			String s = answer.toLowerCase();
			if (s.contains("final answer: yes") || s.contains("final answer is: yes") || s.contains("final answer is yes"))
				return Answer.YES;
			if (s.contains("final answer: no") || s.contains("final answer is: no") || s.contains("final answer is no"))
				return Answer.NO;
			return Answer.NA;
		}
	}
}
