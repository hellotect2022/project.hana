package com.mpole.hana.gateway.ingestion.dbview;

import com.mpole.hana.common.dto.XnPointData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

@Service
@Slf4j
public class DbViewReadService {
    private final DatabaseClient mssqlDatabaseClient;

    private LocalDateTime lastUpdateTime = LocalDateTime.now();
    private final AtomicLong collectTime = new AtomicLong(0);

    public DbViewReadService(@Qualifier("mssqlDatabaseClient")DatabaseClient mssqlDatabaseClient) {
        this.mssqlDatabaseClient = mssqlDatabaseClient;
    }

    public Flux<XnPointData> readFromViewTable() {
        // 마지막 시간에 대한 기록
        AtomicReference<OffsetDateTime> lastDataTimeInBatch = new AtomicReference<>(null);
        collectTime.set(System.currentTimeMillis());

        return mssqlDatabaseClient.sql(
                    """
                        SELECT *
                        FROM vwDigitalTwin_01 WITH (NOLOCK)
                        -- WHERE update_datetime > :lastTime
                        ORDER BY update_datetime DESC
                    """
                )
                //.bind("lastTime", lastUpdateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")))
                .fetch()
                .all()
                .collectList()
                .doOnSuccess(a -> {
                    if (!a.isEmpty()) {
                        log.info("업데이트된 데이타 갯수 : {} 개",a.size());
                    }
                    log.info("0-1 데이타 가져오는데 걸리는 시간 : {}ms", System.currentTimeMillis() - collectTime.get());
                })
                .flatMapIterable(rows -> rows)
                .map((row) -> {
                    Object rawValue = row.get("update_datetime");
                    LocalDateTime localDateTime;

                    if (rawValue instanceof LocalDateTime ldt) {
                        localDateTime = ldt;
                    } else if (rawValue instanceof OffsetDateTime odt) {
                        // OffsetDateTime인 경우 시스템 기본 시간대(ZoneId)를 기준으로 변환
                        localDateTime = odt.toLocalDateTime();
                    } else {
                        localDateTime = null;
                    }

                    if (localDateTime == null) {
                        return Optional.<XnPointData>empty();
                    }
                    OffsetDateTime currentRowTime = localDateTime.atOffset(ZoneOffset.ofHours(9));
                    lastDataTimeInBatch.set(currentRowTime);
                    String pointCode = (String) row.get("point_code");
                    String valueRaw = (String) row.get("value");
                    String deviceCode = (String) row.get("device_code");
                    String objectType = (String) row.get("object_type");
                    String alarmYn = (String) row.get("alarm_yn");
                    return Optional.of(
                            XnPointData.builder()
                                    .alarmYn(alarmYn)
                                    .deviceCode(deviceCode)
                                    .pointCode(pointCode)
                                    .objectType(objectType)
                                    .valueRaw(valueRaw)
                                    .updateDatetime(currentRowTime)
                                    .build()
                    );
                })
                .doOnComplete(() -> log.info("0-2.SiPointData 로 변환 시간 : {}ms", System.currentTimeMillis() - collectTime.get()))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .doOnComplete(() -> {
                    //log.info("1-3.Optional 값 있는것만 가져옴 로 변환 시간 : {}ms", System.currentTimeMillis() - collectTime.get());
                    OffsetDateTime latest = lastDataTimeInBatch.get();
                    if (latest != null) {
                        this.lastUpdateTime = latest.toLocalDateTime();
                        log.info("0-3.수집 완료. 다음 조회 기준 시간: {}", latest.toLocalDateTime());
                    }
                })
                .doOnError(e -> log.error("DB 조회 중 에러 발생: ", e));
    }
}
