# reachability-blabber

## Building

```
$ mvn clean package
```

## Usage examples

Given a configuration with:
  - Depths 1, 5, and 10
  - Alphanumeric identifiers of length 4
  - Shuffled methods
  - A sycophancy prompt strategy
  - A sample size of 4 (2 positive cases and 2 negative cases at each depth)
  - 5 "padding" extra methods
  - GPT-4o as the tested model

### First step: generating the dataset
```
--make-dataset --depths 1,3,5 --shuffle --identifier-length 4 --identifier-strategy ALPHANUMERIC --prompt-strategy SYCOPHANCY --model gpt-4o --padding 5 --sample-size 4
```

### Second step: generating OpenAI's batch file `batch.jsonl`
```
--make-batch --batch-file batch.jsonl --depths 1,3,5 --shuffle --identifier-length 4 --identifier-strategy ALPHANUMERIC --prompt-strategy SYCOPHANCY --model gpt-4o --padding 5 --sample-size 4
```

### Third step: processing the batch file returned by OpenAI
```
--process-batch --batch-file batch_nh78ZbSq1M4FcogyspjnFE53_output.jsonl --depths 1,3,5 --shuffle --identifier-length 4 --identifier-strategy ALPHANUMERIC --prompt-strategy SYCOPHANCY --model gpt-4o --padding 5 --sample-size 4
```

### Second step (alternative): querying OpenAI's API
```
--run --token $OPENAI_TOKEN --depths 1,3,5 --shuffle --identifier-length 4 --identifier-strategy ALPHANUMERIC --prompt-strategy SYCOPHANCY --model gpt-4o --padding 5 --sample-size 4
```

## Detailed usage

```
Usage: reachability-blabber [--make-batch] [--make-dataset] [--process-batch]
                            [--run] [--shuffle] [--batch-file=<batchFile>]
                            [--identifier-length=<identifierLength>]
                            [--identifier-strategy=<identifierStrategy>]
                            [--model=<model>] [--padding=<padding>]
                            [--prompt-strategy=<promptStrategy>]
                            [--retries=<retries>] [--sample-size=<sampleSize>]
                            [--threads=<threads>] [--token=<token>]
                            --depths=<depths>[,<depths>...] [--depths=<depths>[,
                            <depths>...]]...
      --batch-file=<batchFile>
                            Name of the batch file to generate and/or to process
      --depths=<depths>[,<depths>...]
                            Method chain depths, e.g., 1,5,25,50,75,100
      --identifier-length=<identifierLength>
                            Length of the generated alphanumeric identifiers
      --identifier-strategy=<identifierStrategy>
                            Identifier strategy, either NATURAL (m1, m2, m3) or
                              ALPHANUMERIC
      --make-batch          Generate the .jsonl batch file
      --make-dataset        Generate the groundtruth dataset
      --model=<model>       OpenAI's model identifier
      --padding=<padding>   How many additional methods, unrelated to the
                              chain, to generate
      --process-batch       Process the .jsonl batch file returned by OpenAI
      --prompt-strategy=<promptStrategy>
                            Prompt strategy, either YES_NO, STEP_BY_STEP, or
                              SYCOPHANCY
      --retries=<retries>   When invoking OpenAI's API, how many times to ask
                              the same question
      --run                 Run the dataset against OpenAI's API
      --sample-size=<sampleSize>
                            How many times do we generate a new question for a
                              given set of parameters
      --shuffle             Whether to shuffle the method declarations or not
      --threads=<threads>   When using OpenAI's API, how many requests do we
                              run in parallel?
      --token=<token>       OpenAI token
```
