package com.redis.riot.file;

import com.redis.testcontainers.RedisServer;
import com.redis.testcontainers.RedisStackContainer;

class StackFileTests extends FileTests {

    private static final RedisStackContainer redis = new RedisStackContainer(
            RedisStackContainer.DEFAULT_IMAGE_NAME.withTag(RedisStackContainer.DEFAULT_TAG));

    @Override
    protected RedisServer getRedisServer() {
        return redis;
    }

}
