package com.redis.riot.cli;

import com.redis.riot.core.RedisWriterOptions;
import com.redis.spring.batch.RedisItemWriter.MergePolicy;
import com.redis.spring.batch.RedisItemWriter.StreamIdPolicy;
import com.redis.spring.batch.RedisItemWriter.TtlPolicy;

import picocli.CommandLine.Option;

public class RedisWriterArgs extends RedisOperationArgs {

    @Option(names = "--no-ttl", description = "Disables key expiry.")
    private boolean noTtl;

    @Option(names = "--merge", description = "Merge collection data structures.")
    private boolean merge;

    @Option(names = "--no-stream-id", description = "Disables propagation of stream message IDs.")
    private boolean noStreamId;

    public RedisWriterOptions writerOptions() {
        RedisWriterOptions options = new RedisWriterOptions();
        configure(options);
        options.setMergePolicy(mergePolicy());
        options.setStreamIdPolicy(streamPolicy());
        options.setTtlPolicy(ttlPolicy());
        return options;
    }

    private TtlPolicy ttlPolicy() {
        return noTtl ? TtlPolicy.DROP : TtlPolicy.PROPAGATE;
    }

    private StreamIdPolicy streamPolicy() {
        return noStreamId ? StreamIdPolicy.DROP : StreamIdPolicy.PROPAGATE;
    }

    private MergePolicy mergePolicy() {
        return merge ? MergePolicy.MERGE : MergePolicy.OVERWRITE;
    }

}