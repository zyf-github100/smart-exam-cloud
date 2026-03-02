package com.smart.exam.common.core.id;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@EnableConfigurationProperties(SnowflakeProperties.class)
public class SnowflakeIdGenerator {

    private static final long START_STAMP = 1704067200000L; // 2024-01-01T00:00:00Z
    private static final long SEQUENCE_BIT = 12L;
    private static final long MACHINE_BIT = 5L;
    private static final long DATACENTER_BIT = 5L;
    private static final long MAX_SEQUENCE = ~(-1L << SEQUENCE_BIT);
    private static final long MAX_MACHINE_NUM = ~(-1L << MACHINE_BIT);
    private static final long MAX_DATACENTER_NUM = ~(-1L << DATACENTER_BIT);
    private static final long MACHINE_LEFT = SEQUENCE_BIT;
    private static final long DATACENTER_LEFT = SEQUENCE_BIT + MACHINE_BIT;
    private static final long TIMESTAMP_LEFT = DATACENTER_LEFT + DATACENTER_BIT;

    private final SnowflakeProperties properties;
    private long sequence = 0L;
    private long lastTimestamp = -1L;

    public SnowflakeIdGenerator(SnowflakeProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void validate() {
        if (properties.getWorkerId() > MAX_MACHINE_NUM || properties.getWorkerId() < 0) {
            throw new IllegalArgumentException("snowflake.worker-id out of range");
        }
        if (properties.getDatacenterId() > MAX_DATACENTER_NUM || properties.getDatacenterId() < 0) {
            throw new IllegalArgumentException("snowflake.datacenter-id out of range");
        }
    }

    public synchronized long nextId() {
        long currStamp = currentTimeMillis();
        if (currStamp < lastTimestamp) {
            throw new IllegalStateException("Clock moved backwards. Refusing to generate id");
        }

        if (currStamp == lastTimestamp) {
            sequence = (sequence + 1) & MAX_SEQUENCE;
            if (sequence == 0L) {
                currStamp = nextMillis();
            }
        } else {
            sequence = 0L;
        }

        lastTimestamp = currStamp;

        return (currStamp - START_STAMP) << TIMESTAMP_LEFT
                | properties.getDatacenterId() << DATACENTER_LEFT
                | properties.getWorkerId() << MACHINE_LEFT
                | sequence;
    }

    private long nextMillis() {
        long mill = currentTimeMillis();
        while (mill <= lastTimestamp) {
            mill = currentTimeMillis();
        }
        return mill;
    }

    private long currentTimeMillis() {
        return System.currentTimeMillis();
    }
}

