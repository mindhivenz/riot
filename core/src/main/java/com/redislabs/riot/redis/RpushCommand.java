package com.redislabs.riot.redis;

import java.util.Map;

import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.redis.ListItemWriter;
import org.springframework.batch.item.redis.ListItemWriter.ListItemWriterBuilder.PushDirection;

import com.redislabs.riot.TransferContext;

import picocli.CommandLine.Command;

@Command(name = "rpush", aliases = "r", description = "Insert values at the tail of lists")
public class RpushCommand extends AbstractCollectionCommand {

	@Override
	public ItemWriter<Map<String, Object>> writer(TransferContext context) throws Exception {
		return configure(ListItemWriter.<Map<String, Object>>builder(context.getClient())
				.poolConfig(context.getRedisOptions().poolConfig()).direction(PushDirection.RIGHT)).build();
	}

}
