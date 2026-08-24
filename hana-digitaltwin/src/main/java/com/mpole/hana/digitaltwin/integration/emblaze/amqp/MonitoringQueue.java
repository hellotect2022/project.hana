package com.mpole.hana.digitaltwin.integration.emblaze.amqp;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Map;

@RequiredArgsConstructor
@Getter
public enum MonitoringQueue {
    DATETIME(Names.DATETIME, Map.class),
    ENERGY(Names.ENERGY, Map.class),
    LIGHT(Names.LIGHT, Map.class),
    SENSOR(Names.SENSOR, Map.class),
    SPACE(Names.SPACE, Map.class),
    ENOCEAN(Names.ENOCEAN, Map.class),
    STATUS(Names.STATUS, Map.class),
    SYSTEM(Names.SYSTEM, Map.class);

    private final String queueName;
    private final Class<?> targetClass;

    public static class Names {
        public static final String DATETIME = "datetime";
        public static final String ENERGY = "energy";
        public static final String LIGHT = "light";
        public static final String SENSOR = "sensor";
        public static final String SPACE = "space";
        public static final String ENOCEAN = "enocean";
        public static final String STATUS = "status";
        public static final String SYSTEM = "system";
    }
}
