package com.redis.riot.cli;

import com.redis.riot.core.AbstractExport;
import com.redis.riot.core.AbstractJobExecutable;
import com.redis.riot.core.RedisReaderOptions;
import com.redis.riot.core.StepBuilder;
import com.redis.spring.batch.util.BatchUtils;

import picocli.CommandLine.ArgGroup;

public abstract class AbstractExportCommand extends AbstractJobCommand {

    @ArgGroup(exclusive = false, heading = "Redis reader options%n")
    RedisReaderArgs readerArgs = new RedisReaderArgs();

    @Override
    protected AbstractJobExecutable getJobExecutable() {
        AbstractExport executable = getExportExecutable();
        executable.setReaderOptions(readerOptions());
        return executable;
    }

    protected RedisReaderOptions readerOptions() {
        RedisReaderOptions options = readerArgs.redisReaderOptions();
        options.setDatabase(redisArgs().uri().getDatabase());
        return options;
    }

    protected abstract AbstractExport getExportExecutable();

    @Override
    protected long size(StepBuilder<?, ?> step) {
        return BatchUtils.size(step.getReader());
    }

    @Override
    protected String taskName(StepBuilder<?, ?> step) {
        return "Exporting";
    }

}
