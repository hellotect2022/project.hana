package com.mpole.hana.gateway.ingestion.dbview.view01;

import com.mpole.hana.common.dto.XnPointData;
import com.mpole.hana.gateway.config.properties.View01Properties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * <b>외부 뷰 #01 읽기 — 관제점 현재값.</b>
 *
 * <p>SI 측 {@code vwDigitalTwin_01}(설정: {@code hdt.dbview.view01.table})에서 장비 관제점의
 * 값/알람 플래그/갱신시각을 읽어 {@link XnPointData} 로 변환한다.</p>
 *
 * <p><b>왜 매 사이클 풀리드인가</b> — 뷰의 {@code update_datetime} 은 "뷰 행이 갱신된 시각"이 아니라
 * 각 물리 장비(소스)의 자기 시각이라 전체로 보면 단조증가하지 않는다. 워터마크 증분쿼리
 * ({@code WHERE update_datetime > :lastTime})는 시계가 뒤처진 포인트를 상시 누락시키므로
 * 읽기는 풀리드로 유지하고, 하류 부하는 {@link DeviceSnapshotStore}(값이 바뀐 장비만 dirty 표시)로 줄인다.</p>
 */
@Service
@Slf4j
public class View01ReadService {

    private final DatabaseClient mssqlDatabaseClient;
    private final View01Properties properties;

    private LocalDateTime lastUpdateTime = LocalDateTime.now();
    private final AtomicLong collectTime = new AtomicLong(0);
    AtomicInteger count = new AtomicInteger(0);

    // @Qualifier 는 필드가 아니라 생성자 파라미터에 둔다(lombok.config 없이 @RequiredArgsConstructor 를 쓰면
    // @Qualifier 가 생성자로 복사되지 않아, DatabaseClient 빈이 2개인 이 모듈에서 주입 대상이 모호해진다).
    public View01ReadService(@Qualifier("mssqlDatabaseClient") DatabaseClient mssqlDatabaseClient,
                             View01Properties properties) {
        this.mssqlDatabaseClient = mssqlDatabaseClient;
        this.properties = properties;
    }

    public Flux<XnPointData> readFromViewTable() {
        collectTime.set(System.currentTimeMillis());

        // 테이블명은 식별자라 바인딩 파라미터로 넘길 수 없다 — yml 로만 관리되는 신뢰 입력.
        String sql = """
                SELECT 
                *,
                CASE
                    -- 💡 숫자로 변환이 가능한 경우 ➡️ 소수점 한 자리 반올림 후 문자열로 변환
                    WHEN TRY_CAST(value AS NUMERIC(18, 4)) IS NOT NULL
                    THEN FORMAT(ROUND(CAST(value AS NUMERIC(18, 4)), 1), '0.0')
                   
                    -- 💡 NULL이거나 진짜 문자열인 경우 ➡️ 원본 그대로 출력 (NULL이면 빈값 처리 가능)
                    ELSE ISNULL(VALUE, '')
                END AS processed_value
                FROM %s WITH (NOLOCK)
                WHERE UPDATE_DATETIME IS NOT NULL
                """.formatted(properties.table());

        return mssqlDatabaseClient.sql(sql)
                .map((row,meta)->{
                    String pointCode = row.get("point_code",String.class);
                    String valueRaw = row.get("processed_value",String.class);
                    String deviceCode = row.get("device_code",String.class);
                    String objectType = row.get("object_type", String.class);
                    String alarmYn = row.get("alarm_yn",String.class);
                    OffsetDateTime updateDatetime = Optional.ofNullable(row.get("update_datetime", LocalDateTime.class))
                            .map(ldt -> ldt.atOffset(ZoneOffset.ofHours(9)))
                            .orElse(null); // 또는 기본값 지정

                    return XnPointData.builder()
                                    .alarmYn(alarmYn)
                                    .deviceCode(deviceCode)
                                    .pointCode(pointCode)
                                    .objectType(objectType)
                                    .valueRaw(valueRaw)
                                    .updateDatetime(updateDatetime)
                                    .build();

                }).all()
                //.doOnComplete(() -> log.info("조회 완료! 총 개수: {} 개", count.getAndSet(0)))
                //.doOnComplete(() -> log.info("0-2.SiPointData 로 변환 시간 : {}ms", System.currentTimeMillis() - collectTime.get()))
                .doOnError(e -> log.error("DB 조회 중 에러 발생: ", e));
    }
}
