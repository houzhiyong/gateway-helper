package io.choerodon.gateway.helper.infra.cache.l2.redis;

import io.choerodon.gateway.helper.infra.cache.l2.L2Cache;
import io.choerodon.gateway.helper.infra.cache.l2.L2CacheManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RedisL2CacheManager extends RedisCacheManager implements L2CacheManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(RedisL2CacheManager.class);
    private static final String CACHE_TYPE_REDIS = "redis";
    private static final String SPEC_EXPIRATION = "expiration";
    private static final String SPLIT_OPTIONS = ",";
    private static final String SPLIT_KEY_VALUE = "=";

    private static final Map<String, Long> expires = new ConcurrentHashMap<>();

    public RedisL2CacheManager(RedisOperations redisOperations) {
        super(redisOperations);
    }

    public static String  type() {
        return CACHE_TYPE_REDIS;
    }

    @Override
    public L2Cache getL2Cache(String name, String spec) {
        synchronized (this) {
            if (spec != null && StringUtils.hasText(spec)) {
                this.setCacheSpecification(name, spec);
            }
            Cache cache = this.getCache(name);
            if (cache != null) {
                return new RedisL2Cache(cache);
            }
            return null;
        }
    }

    private void setCacheSpecification(final String name, final String spec) {
        for (String option : spec.split(SPLIT_OPTIONS)) {
            parseOption(name, option);
        }
        this.setExpires(expires);
    }

    private void parseOption(final String name, final String option) {
        if (option.isEmpty()) {
            return;
        }
        String[] keyAndValue = option.split(SPLIT_KEY_VALUE);
        String key = keyAndValue[0].trim();
        if (SPEC_EXPIRATION.equals(key)) {
            String value = (keyAndValue.length == 1) ? null : keyAndValue[1].trim();
            if (value != null) {
                long expiredTime = Long.parseLong(value);
                LOGGER.debug("Redis cache set {} expiredTime is {}", name, expiredTime);
                expires.put(name, expiredTime);
            }
        }
    }

}
