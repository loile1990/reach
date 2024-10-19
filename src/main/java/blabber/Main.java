package blabber;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Path;
import java.util.List;

@Command(name = "reachability-blabber")
public class Main implements Runnable {
	@Option(names = "--depths", required = true,
		description = "Method chain depths, e.g., 1,5,25,50,75,100", split = ",")
	private List<Integer> depths;
	@Option(names = "--shuffle",
		description = "Whether to shuffle the method declarations or not")
	private boolean shuffle;
	@Option(names = "--identifier-strategy",
		description = "Identifier strategy, either NATURAL (m1, m2, m3) or ALPHANUMERIC")
	private IdentifierStrategy.Name identifierStrategy;
	@Option(names = "--identifier-length",
		description = "Length of the generated alphanumeric identifiers")
	private int identifierLength;
	@Option(names = "--prompt-strategy",
		description = "Prompt strategy, either YES_NO, STEP_BY_STEP, or SYCOPHANCY")
	private PromptStrategy.Name promptStrategy;
	@Option(names = "--retries", defaultValue = "1",
		description = "When invoking OpenAI's API, how many times to ask the same question")
	private int retries;
	@Option(names = "--sample-size",
		description = "How many times do we generate a new question for a given set of parameters")
	private int sampleSize;
	@Option(names = "--padding",
		description = "How many additional methods, unrelated to the chain, to generate")
	private int padding;
	@Option(names = "--model",
		description = "OpenAI's model identifier")
	private String model;
	@Option(names = "--token",
		description = "OpenAI token")
	private String token;
	@Option(names = "--threads", defaultValue = "4",
		description = "When using OpenAI's API, how many requests do we run in parallel?")
	private int threads;
	@Option(names = "--make-dataset",
		description = "Generate the groundtruth dataset")
	private boolean makeDataset;
	@Option(names = "--make-batch",
		description = "Generate the .jsonl batch file")
	private boolean makeBatch;
	@Option(names = "--run",
		description = "Run the dataset against OpenAI's API")
	private boolean run;
	@Option(names = "--process-batch",
		description = "Process the .jsonl batch file returned by OpenAI")
	private boolean processBatch;
	@Option(names = "--batch-file",
		description = "Name of the batch file to generate and/or to process")
	private Path batchFile;

	private static final Logger logger = LogManager.getLogger(Main.class);

	public void run() {
		// Building our configuration
		var configuration = new Configuration(depths, shuffle, identifierStrategy, identifierLength,
			promptStrategy, sampleSize, padding, model);

		// Setting up our dependencies
		var openAi = new OpenAi(model, token);
		var snippetGenerator = new SnippetGenerator();
		var blabber = new Blabber(configuration, openAi, snippetGenerator);

		try {
			if (makeDataset) {
					blabber.makeDataset();
			}

			if (makeBatch) {
				blabber.makeBatch(batchFile);
			} else if (processBatch) {
				blabber.processBatch(batchFile);
			} else if (run) {
				blabber.runDataset(retries, threads);
			}
		} catch (Exception e) {
			logger.error(e);
		}
	}

	public static void main(String[] args) {
		var exitCode = new CommandLine(new Main()).execute(args);
		System.exit(exitCode);
	}
}
