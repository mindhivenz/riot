package com.redis.riot;

import java.time.Duration;
import java.util.Collection;

import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.function.FunctionItemProcessor;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.util.Assert;

import com.redis.riot.CompareStatusItemWriter.StatusCount;
import com.redis.riot.core.RiotUtils;
import com.redis.riot.core.Step;
import com.redis.riot.function.StringKeyValue;
import com.redis.riot.function.ToStringKeyValue;
import com.redis.spring.batch.item.redis.RedisItemReader;
import com.redis.spring.batch.item.redis.common.KeyValue;
import com.redis.spring.batch.item.redis.reader.DefaultKeyComparator;
import com.redis.spring.batch.item.redis.reader.KeyComparator;
import com.redis.spring.batch.item.redis.reader.KeyComparison;
import com.redis.spring.batch.item.redis.reader.KeyComparisonItemReader;
import com.redis.spring.batch.item.redis.reader.RedisScanSizeEstimator;

import io.lettuce.core.codec.ByteArrayCodec;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Option;

public abstract class AbstractCompareCommand extends AbstractReplicateCommand {

	public static final Duration DEFAULT_TTL_TOLERANCE = DefaultKeyComparator.DEFAULT_TTL_TOLERANCE;
	public static final String COMPARE_STEP_NAME = "compare";
	public static final boolean DEFAULT_COMPARE_STREAM_MESSAGE_ID = true;

	private static final String COMPARE_TASK_NAME = "Comparing";
	private static final String STATUS_DELIMITER = " | ";

	@Option(names = "--target-read-from", description = "Which target Redis cluster nodes to read from: ${COMPLETION-CANDIDATES} (default: ${DEFAULT-VALUE}).", paramLabel = "<n>")
	private ReadFrom targetReadFrom = ReadFrom.UPSTREAM;

	@Option(names = "--show-diffs", description = "Print details of key mismatches during dataset verification. Disables progress reporting.")
	private boolean showDiffs;

	@Option(names = "--ttl-tolerance", description = "Max TTL offset in millis to consider keys equal (default: ${DEFAULT-VALUE}).", paramLabel = "<ms>")
	private long ttlToleranceMillis = DEFAULT_TTL_TOLERANCE.toMillis();

	@ArgGroup(exclusive = false)
	private EvaluationContextArgs evaluationContextArgs = new EvaluationContextArgs();

	@ArgGroup(exclusive = false, heading = "Processor options%n")
	private KeyValueProcessorArgs processorArgs = new KeyValueProcessorArgs();

	protected ItemProcessor<KeyValue<byte[]>, KeyValue<byte[]>> processor() {
		return RiotUtils.processor(keyValueFilter(), keyValueProcessor());
	}

	private KeyValueFilter<byte[], KeyValue<byte[]>> keyValueFilter() {
		return new KeyValueFilter<>(ByteArrayCodec.INSTANCE, log);
	}

	protected abstract boolean isStruct();

	private ItemProcessor<KeyValue<byte[]>, KeyValue<byte[]>> keyValueProcessor() {
		if (isIgnoreStreamMessageId()) {
			Assert.isTrue(isStruct(), "--no-stream-id can only be used with --struct");
		}
		StandardEvaluationContext evaluationContext = evaluationContext();
		log.info("Creating processor with {}", processorArgs);
		ItemProcessor<KeyValue<String>, KeyValue<String>> processor = processorArgs.processor(evaluationContext);
		if (processor == null) {
			return null;
		}
		ToStringKeyValue<byte[]> code = new ToStringKeyValue<>(ByteArrayCodec.INSTANCE);
		StringKeyValue<byte[]> decode = new StringKeyValue<>(ByteArrayCodec.INSTANCE);
		return RiotUtils.processor(new FunctionItemProcessor<>(code), processor, new FunctionItemProcessor<>(decode));
	}

	private StandardEvaluationContext evaluationContext() {
		log.info("Creating SpEL evaluation context with {}", evaluationContextArgs);
		StandardEvaluationContext evaluationContext = evaluationContextArgs.evaluationContext();
		configure(evaluationContext);
		return evaluationContext;
	}

	private String compareMessage(Collection<StatusCount> counts) {
		StringBuilder builder = new StringBuilder();
		counts.stream().map(CompareStepListener::toString).forEach(s -> builder.append(STATUS_DELIMITER).append(s));
		return builder.toString();
	}

	protected Step<KeyComparison<byte[]>, KeyComparison<byte[]>> compareStep() {
		KeyComparisonItemReader<byte[], byte[]> reader = compareReader();
		CompareStatusItemWriter<byte[]> writer = new CompareStatusItemWriter<>();
		Step<KeyComparison<byte[]>, KeyComparison<byte[]>> step = new Step<>(COMPARE_STEP_NAME, reader, writer);
		step.taskName(COMPARE_TASK_NAME);
		step.statusMessageSupplier(() -> compareMessage(writer.getMismatches()));
		step.maxItemCountSupplier(RedisScanSizeEstimator.from(reader.getSourceReader()));
		if (showDiffs) {
			log.info("Adding key diff logger");
			step.writeListener(new CompareLoggingWriteListener<>(ByteArrayCodec.INSTANCE));
		}
		step.executionListener(new CompareStepListener(writer));
		return step;
	}

	private RedisItemReader<byte[], byte[]> compareRedisReader() {
		if (isQuickCompare()) {
			log.info("Creating Redis quick compare reader");
			return RedisItemReader.type(ByteArrayCodec.INSTANCE);
		}
		log.info("Creating Redis full compare reader");
		return RedisItemReader.struct(ByteArrayCodec.INSTANCE);
	}

	protected abstract boolean isQuickCompare();

	protected KeyComparisonItemReader<byte[], byte[]> compareReader() {
		RedisItemReader<byte[], byte[]> source = compareSourceReader();
		RedisItemReader<byte[], byte[]> target = compareTargetReader();
		KeyComparisonItemReader<byte[], byte[]> reader = new KeyComparisonItemReader<>(source, target);
		reader.setComparator(keyComparator());
		reader.setProcessor(processor());
		return reader;
	}

	private KeyComparator<byte[]> keyComparator() {
		boolean ignoreStreamId = isIgnoreStreamMessageId();
		Duration ttlTolerance = Duration.ofMillis(ttlToleranceMillis);
		log.info("Creating KeyComparator with ttlTolerance={} ignoreStreamMessageId={}", ttlTolerance, ignoreStreamId);
		DefaultKeyComparator<byte[], byte[]> comparator = new DefaultKeyComparator<>(ByteArrayCodec.INSTANCE);
		comparator.setIgnoreStreamMessageId(ignoreStreamId);
		comparator.setTtlTolerance(ttlTolerance);
		return comparator;
	}

	protected boolean isIgnoreStreamMessageId() {
		return !processorArgs.isPropagateIds();
	}

	private RedisItemReader<byte[], byte[]> compareSourceReader() {
		RedisItemReader<byte[], byte[]> reader = compareRedisReader();
		configureSourceRedisReader(reader);
		return reader;
	}

	private RedisItemReader<byte[], byte[]> compareTargetReader() {
		RedisItemReader<byte[], byte[]> reader = compareRedisReader();
		configureTargetRedisReader(reader);
		return reader;
	}

	@Override
	protected void configureTargetRedisReader(RedisItemReader<?, ?> reader) {
		super.configureTargetRedisReader(reader);
		log.info("Configuring target Redis reader with read-from {}", targetReadFrom);
		reader.setReadFrom(targetReadFrom.getReadFrom());
	}

	public ReadFrom getTargetReadFrom() {
		return targetReadFrom;
	}

	public void setTargetReadFrom(ReadFrom readFrom) {
		this.targetReadFrom = readFrom;
	}

	public boolean isShowDiffs() {
		return showDiffs;
	}

	public void setShowDiffs(boolean showDiffs) {
		this.showDiffs = showDiffs;
	}

	public long getTtlToleranceMillis() {
		return ttlToleranceMillis;
	}

	public void setTtlToleranceMillis(long tolerance) {
		this.ttlToleranceMillis = tolerance;
	}

	public KeyValueProcessorArgs getProcessorArgs() {
		return processorArgs;
	}

	public void setProcessorArgs(KeyValueProcessorArgs args) {
		this.processorArgs = args;
	}

}
