package blabber;

import com.theokanning.openai.OpenAiHttpException;
import com.theokanning.openai.completion.chat.ChatCompletionChoice;
import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.completion.chat.ChatMessageRole;
import com.theokanning.openai.service.OpenAiService;

import java.time.Duration;
import java.util.List;

/**
 * Handles communication with OpenAI's API
 */
public class OpenAi {
	private String model;
	private String token;

	public OpenAi(String model, String token) {
		this.model = model;
		this.token = token;
	}

	/**
	 * Invokes the completion API with the given {@code prompt} and returns the LLM's answer
	 */
	public String submit(String prompt) {
		try {
			OpenAiService service = new OpenAiService(token, Duration.ofSeconds(120));

			ChatCompletionRequest completionRequest = ChatCompletionRequest.builder()
				.messages(List.of(new ChatMessage(ChatMessageRole.USER.value(), prompt)))
				.model(model)
				.build();

			List<ChatCompletionChoice> choices = service.createChatCompletion(completionRequest).getChoices();
			return choices.get(0).getMessage().getContent();
		} catch (OpenAiHttpException e) {
			System.err.println("[%s] %s: %s; waiting 2 minutes".formatted(e.code, e.code, e.type));
			if (e.statusCode == 429) {
				try {
					Thread.sleep(60 * 1_000);
					return submit(prompt);
				} catch (InterruptedException ee) {
					ee.printStackTrace();
					return e.getMessage();
				}
			}
			throw e;
		}
	}

	public String getModel() {
		return model;
	}
}
