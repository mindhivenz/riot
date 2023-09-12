package com.redis.riot.core.function;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import io.lettuce.core.StreamMessage;

public class StreamToMapFunction implements Function<List<StreamMessage<String, String>>, Map<String, String>> {

    public static final String DEFAULT_KEY_FORMAT = "%s.%s";

    private String keyFormat = DEFAULT_KEY_FORMAT;

    public void setKeyFormat(String keyFormat) {
        this.keyFormat = keyFormat;
    }

    @Override
    public Map<String, String> apply(List<StreamMessage<String, String>> source) {
        Map<String, String> result = new HashMap<>();
        for (StreamMessage<String, String> message : source) {
            for (Map.Entry<String, String> entry : message.getBody().entrySet()) {
                result.put(String.format(keyFormat, message.getId(), entry.getKey()), entry.getValue());
            }
        }
        return result;
    }

}
