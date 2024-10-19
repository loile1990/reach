package blabber;

import com.theokanning.openai.utils.TikTokensUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Handles all code generation: snippets, positive and negative cases, etc.
 */
public class Blabber {
	private static final Path GROUNDTRUTH_TSV = Path.of("groundtruth.tsv");
	private static final Path RESULTS_TSV = Path.of("results.tsv");
	private static final Path BATCH_JSON = Path.of("batch.jsonl");
	private static final Path BATCH_RESULTS_TSV = Path.of("batch-results.tsv");
	private static final Path PROMPTS_DIR = Path.of("prompts");
	private static final Path RESULTS_DIR = Path.of("results");

	private final Configuration configuration;
	private final OpenAi openAi;
	private final SnippetGenerator snippetGenerator;

	private static final Logger logger = LogManager.getLogger(Blabber.class);

	Blabber(Configuration configuration, OpenAi openAi, SnippetGenerator snippetGenerator) {
		this.configuration = configuration;
		this.openAi = openAi;
		this.snippetGenerator = snippetGenerator;
	}

	/**
	 * Generates a dataset for this configuration consisting of:
	 *   - YES cases and NO case with the appropriate snippets and prompts
	 *   - a groundtruth.tsv file summarizing the expected results
	 */
	void makeDataset() {
		var datasetPath = configuration.datasetPath();

		if (datasetPath.toFile().exists()) {
			logger.error("Dataset {} exists; skipping", datasetPath);
			return;
		}

		try {
			promptsDir().toFile().mkdirs();

			var datasetLines = new ArrayList<String>();
			datasetLines.add("id\tconfiguration\tprompt\tsource\ttarget\tdepth\tgroundtruth\n");
			for (int depth : configuration.depths()) {
				logger.info("Generating snippets for depth {}", depth);
				for (int i = 0; i < configuration.sampleSize() / 2; i++) {
					datasetLines.add(makeYesCase(i, depth));
					datasetLines.add(makeNoCase(i, depth));
				}
			}

			Files.writeString(groundtruthFile(), String.join("", datasetLines));
			logger.info("Groundtruth generated at {}", groundtruthFile().toAbsolutePath());
		} catch (IOException e) {
			logger.error("Error generating dataset", e);
		}
	}

	/**
	 * Generates a YES case, i.e., a case where the model is expected to answer positively
	 */
	private String makeYesCase(int i, int depth) throws IOException {
		var nMethods = depth + configuration.padding();
		var depthDir = promptsDir().resolve(String.valueOf(depth));
		var identifierStrategy = configuration.identifierStrategy();
		var promptStrategy = configuration.promptStrategy();
		depthDir.resolve("yes").toFile().mkdirs();

		var identifiers = snippetGenerator.generateIdentifiers(nMethods, identifierStrategy,
			configuration.identifierLength());
		var snippet = snippetGenerator.makeSnippet(identifiers, configuration.shuffle());

		int rand = new Random().nextInt(nMethods - depth);
		var caseId = "%s_%d_%s_%d".formatted(configuration.id(), depth, "yes", i);
		var src = identifiers.get(rand);
		var tgt = identifiers.get(rand + depth);
		var prompt = promptStrategy.generate(snippet, src, tgt);

		var allIdentifiers = String.join(",", identifiers);
		var chainedIdentifiers = String.join(",", identifiers.subList(identifiers.indexOf(src), identifiers.indexOf(tgt)));
		var promptFile = depthDir.resolve("yes").resolve("%d.txt".formatted(i));
		var chainAllFile = depthDir.resolve("yes").resolve("%d-chain-all.txt".formatted(i));
		var chainFile = depthDir.resolve("yes").resolve("%d-chain.txt".formatted(i));

		Files.writeString(promptFile, prompt);
		Files.writeString(chainAllFile, allIdentifiers);
		Files.writeString(chainFile, chainedIdentifiers);

		return "%s\t%s\t%s\t%s\t%s\t%d\t%s%n".formatted(caseId, configuration.id(), promptFile, src, tgt, depth, "YES");
	}

	/**
	 * Generates a NO case, i.e., a case where the model is expected to answer negatively
	 */
	private String makeNoCase(int i, int depth) throws IOException {
		var nMethods = depth + configuration.padding();
		var depthDir = promptsDir().resolve(String.valueOf(depth));
		var identifierStrategy = configuration.identifierStrategy();
		var promptStrategy = configuration.promptStrategy();
		depthDir.resolve("no").toFile().mkdirs();

		var identifiers = snippetGenerator.generateIdentifiers(nMethods, identifierStrategy,
			configuration.identifierLength());
		var snippet = snippetGenerator.makeSnippet(identifiers, configuration.shuffle());

		// NO: there's a single chain, so the only NO case with depth d and N methods is [N - d, N]
		var caseId = "%s_%d_%s_%d".formatted(configuration.id(), depth, "no", i);
		var src = identifiers.get(nMethods - depth);
		var tgt = identifiers.get(new Random().nextInt(0, nMethods - depth));
		var prompt = promptStrategy.generate(snippet, src, tgt);

		var allIdentifiers = String.join(",", identifiers);
		var chainedIdentifiers = String.join(",", identifiers.subList(identifiers.indexOf(tgt), identifiers.indexOf(src)));
		var promptFile = depthDir.resolve("no").resolve("%d.txt".formatted(i));
		var chainAllFile = depthDir.resolve("no").resolve("%d-chain-all.txt".formatted(i));
		var chainFile = depthDir.resolve("no").resolve("%d-chain.txt".formatted(i));

		Files.writeString(promptFile, prompt);
		Files.writeString(chainAllFile, allIdentifiers);
		Files.writeString(chainFile, chainedIdentifiers);

		return "%s\t%s\t%s\t%s\t%s\t%d\t%s%n".formatted(caseId, configuration.id(), promptFile, src, tgt, depth, "NO");
	}

	/**
	 * Generates a batch file for the current configuration that can supplied to OpenAI's platform
	 */
	void makeBatch(Path batchFile) {
		var batchJson = batchFile != null
			? configuration.datasetPath().resolve(batchFile)
			: configuration.datasetPath().resolve(BATCH_JSON);

		try {
			var allReqs = Files.readAllLines(groundtruthFile()).stream().skip(1).map(line -> {
				var fields = line.split("\t");
				var id = fields[0];
				var promptFile = Path.of(fields[2]);
				var prompt = "";
				try {
					prompt = Files.readString(promptFile);
				} catch (IOException e) {
					logger.error(e);
				}

				var messageObject = new JSONObject();
				messageObject.put("role", "user");
				messageObject.put("content", prompt);

				var messagesArray = new JSONArray();
				messagesArray.put(messageObject);

				var bodyObject = new JSONObject();
				bodyObject.put("model", configuration.model());
				bodyObject.put("messages", messagesArray);

				var reqObject = new JSONObject();
				reqObject.put("custom_id", id);
				reqObject.put("method", "POST");
				reqObject.put("url", "/v1/chat/completions");
				reqObject.put("body", bodyObject);

				return reqObject.toString();
			}).toList();

			Files.writeString(batchJson, String.join("\n", allReqs));
			logger.info("Batch generated at {}", batchJson.toAbsolutePath());
		} catch (IOException e) {
			logger.error(e);
		}
	}

	/**
	 * Process the given batch file, returned by OpenAI, to extract the results
	 */
	void processBatch(Path batchFile) {
		if (batchFile == null || !configuration.datasetPath().resolve(batchFile).toFile().exists())
			throw new IllegalArgumentException("Batch file does not exist: " + batchFile.toAbsolutePath());

		var batchJson = configuration.datasetPath().resolve(batchFile);

		try {
			var groundtruth = Files.readAllLines(groundtruthFile()).stream().collect(Collectors.toMap(
				line -> line.split("\t")[0],
				line -> line
			));

			var results = Files.readAllLines(batchJson).stream().map(line -> {
				var json = new JSONObject(line);
				var body = json.getJSONObject("response").getJSONObject("body");
				var id = json.getString("custom_id");

				var groundtruthLine = groundtruth.get(id);
				var fields = groundtruthLine.split("\t");
				var strategy = fields[1];
				var promptFile = Path.of(fields[2]);
				var src = fields[3];
				var tgt = fields[4];
				var depth = Integer.valueOf(fields[5]);
				var expected = fields[6].equals("YES");

				var res = body
					.getJSONArray("choices")
					.getJSONObject(0)
					.getJSONObject("message")
					.getString("content");

				var usage = body.getJSONObject("usage");
				var inTokens = usage.getInt("prompt_tokens");
				var outTokens = usage.getInt("completion_tokens");

				var answerDir = resultsDir().resolve(String.valueOf(depth)).resolve(expected ? "yes" : "no");
				answerDir.toFile().mkdirs();
				var answerFile = answerDir.resolve(promptFile.getFileName());
				var promptStrategy = configuration.promptStrategy();
				var answer = promptStrategy.evaluate(res);
				var isCorrect = switch (answer) {
					case YES -> expected == true;
					case NO -> expected == false;
					case NA -> false;
				};

				try {
					var prompt = Files.readString(promptFile);
					var sb = new StringBuilder();
					sb.append("PROMPT:\n");
					sb.append(prompt + "\n\n");
					sb.append("ANSWER:\n");
					sb.append(res + "\n\n");
					sb.append("INTERPRETED AS:\n");
					sb.append(answer);

					Files.writeString(answerFile, sb.toString());

					return "%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s".formatted(
						strategy, promptFile, answerFile, src, tgt, depth, answer, isCorrect, inTokens, outTokens);
				} catch (IOException e) {
					logger.error(e);
					return "error";
				}
			}).toList();

			Files.writeString(batchResultsFile(), String.join("\n", results));
			logger.info("Batch results generated at {}", batchResultsFile().toAbsolutePath());
		} catch (IOException e) {
			logger.error(e);
		}
	}

	/**
	 * Iterates over the dataset for this configuration and queries OpenAI for results
	 */
	void runDataset(int retries, int threads) {
		try {
			var executor = Executors.newFixedThreadPool(threads);
			var futures = new ArrayList<CompletableFuture<String>>();
			var continuing = resultsFile().toFile().exists();

			Files.readAllLines(groundtruthFile()).stream().skip(1).forEach(line -> {
				var fields = line.split("\t");
				var strategy = fields[1];
				var promptFile = Path.of(fields[2]);
				var src = fields[3];
				var tgt = fields[4];
				var depth = Integer.valueOf(fields[5]);
				var expected = fields[6].equals("YES");

				var answerDir = resultsDir().resolve(String.valueOf(depth)).resolve(expected ? "yes" : "no");
				answerDir.toFile().mkdirs();
				var answerFile = answerDir.resolve(promptFile.getFileName());

				if (!answerFile.toFile().exists()) {
					for (int i = 0; i < retries; i++) {
						futures.add(CompletableFuture.supplyAsync(() ->
								runCase(strategy, promptFile, answerFile, src, tgt, depth, expected), executor));
					}
				}
			});

			executor.shutdown();
			executor.awaitTermination(1, TimeUnit.DAYS);

			var sb = new StringBuilder();
			if (!continuing)
				sb.append("configuration\tpromptFile\tanswerFile\tsource\ttarget\tdepth\tanswer\tcorrect\tinTokens\toutTokens\n");

			futures.forEach(f -> {
				try {
					sb.append(f.get());
				} catch (Exception e) {
					logger.error(e);
				}
			});

			Files.writeString(resultsFile(), sb.toString(), continuing ? StandardOpenOption.APPEND : StandardOpenOption.CREATE);
			logger.info("Results written at {}", resultsFile().toAbsolutePath());
		} catch (IOException | InterruptedException e) {
			logger.error(e);
		}
	}

	/**
	 * Runs a given case of the present configuration's dataset
	 */
	String runCase(String strategy, Path promptFile, Path answerFile, String src, String tgt, int depth, boolean expected) {
		try {
			var prompt = Files.readString(promptFile);
			var promptStrategy = configuration.promptStrategy();

			logger.info("Submitting {}", promptFile);
			var res = openAi.submit(prompt);
			var answer = promptStrategy.evaluate(res);
			logger.info("Interpretation: {}", answer);
			var correct = switch (answer) {
				case YES -> expected == true;
				case NO -> expected == false;
				case NA -> false;
			};
			logger.info("Correct: {}", correct);
			var inTokens = TikTokensUtil.tokens("gpt-3.5-turbo-0301", prompt);
			var outTokens = TikTokensUtil.tokens("gpt-3.5-turbo-0301", res);
			logger.info("Tokens IN: {} Tokens OUT: {}", inTokens, outTokens);

			var sb = new StringBuilder();
			sb.append("PROMPT:\n");
			sb.append(prompt + "\n\n");
			sb.append("ANSWER:\n");
			sb.append(res + "\n\n");
			sb.append("INTERPRETED AS:\n");
			sb.append(answer);

			Files.writeString(answerFile, sb.toString());

			return "%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s%n".formatted(
				strategy, promptFile, answerFile, src, tgt, depth, answer, correct, inTokens, outTokens);
		} catch (Exception e) {
			e.printStackTrace();
			return "";
		}
	}

	Path groundtruthFile() {
		return configuration.datasetPath().resolve(GROUNDTRUTH_TSV);
	}

	Path resultsFile() {
		return configuration.datasetPath().resolve(RESULTS_TSV);
	}

	Path batchResultsFile() {
		return configuration.datasetPath().resolve(BATCH_RESULTS_TSV);
	}

	Path promptsDir() {
		return configuration.datasetPath().resolve(PROMPTS_DIR);
	}

	Path resultsDir() {
		return configuration.datasetPath().resolve(RESULTS_DIR);
	}
}
