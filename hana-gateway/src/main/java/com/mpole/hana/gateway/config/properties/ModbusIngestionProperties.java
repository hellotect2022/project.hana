package com.mpole.hana.gateway.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "hdt.ingestion.modbus")
public record ModbusIngestionProperties(
        boolean enabled,
        int workerThreads,
        Pool pool,
        List<ModbusEndpoint> endpoints
) {
    public ModbusIngestionProperties {
        workerThreads = workerThreads > 0 ? workerThreads : 4;
        pool = pool != null ? pool : new Pool(4, 16, 0);
        endpoints = endpoints != null ? List.copyOf(endpoints) : List.of();
    }
    public record Pool(int coreSize, int maxSize, int queueCapacity) {
        public Pool {
            coreSize = coreSize > 0 ? coreSize : 4;
            maxSize = maxSize > 0 ? maxSize : 16;
            queueCapacity = Math.max(queueCapacity, 0);
        }
    }
    /** @param slaveId Modbus unit id (slave) */
    public record ModbusEndpoint(String host, int port, int slaveId, String deviceCode) {
    }
}
