package com.redislabs.riot;

import com.redislabs.riot.processor.KeyValueProcessor;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.redis.support.KeyValue;
import org.springframework.expression.Expression;
import org.springframework.expression.common.TemplateParserContext;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

@SuppressWarnings("ConstantConditions")
public class ProcessorsTest {

    @Test
    public void testKeyValueProcessor() {
        SpelExpressionParser parser = new SpelExpressionParser();
        Expression expression = parser.parseExpression("'pre:'.concat(key).concat(':post')");
        KeyValueProcessor<KeyValue<String>> processor = new KeyValueProcessor<>(expression, new StandardEvaluationContext());
        KeyValue<String> keyValue = processor.process(new KeyValue<>("123"));
        Assertions.assertEquals("pre:123:post", keyValue.getKey());
    }

    @Test
    public void testKeyValueProcessorOptions() throws Exception {
        KeyValueProcessorOptions options = new KeyValueProcessorOptions();
        SpelExpressionParser parser = new SpelExpressionParser();
        Expression expression = parser.parseExpression("#{#src.database}:#{key}:#{#src.host}", new TemplateParserContext());
        options.setExpression(expression);
        RedisOptions src = new RedisOptions();
        src.setDatabase(14);
        src.setHost("srchost");
        RedisOptions dest = new RedisOptions();
        dest.setDatabase(15);
        dest.setHost("desthost");
        ItemProcessor<KeyValue<String>, KeyValue<String>> processor = options.processor(src, dest);
        KeyValue<String> keyValue = processor.process(new KeyValue<>("123"));
        Assertions.assertEquals("14:123:srchost", keyValue.getKey());
    }
}
