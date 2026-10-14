package com.mpole.hana.gateway.ingestion.parking;

import com.mpole.hana.gateway.config.properties.ParkingProperties;
import io.r2dbc.spi.Readable;
import io.r2dbc.spi.ReadableMetadata;
import io.r2dbc.spi.RowMetadata;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 주차 입차·출차 적재 (2026-10-13).
 *
 * <pre>
 *   주차 MSSQL(DRPKDBV5)                              우리 PostgreSQL(core)
 *     EXEC UP_SearchInCarinfo  'yyyyMMdd'   ──행──▶   tbl_parking_in   UPSERT (parking_seq)
 *     EXEC UP_SearchoutCarinfo 'yyyyMMdd'   ──행──▶   tbl_parking_out  UPSERT (parking_seq)
 * </pre>
 *
 * <h3>부하를 낮추는 방식</h3>
 * <ul>
 *   <li>프로시저당 한 번 호출, 결과(수백 행)를 메모리로 받아 <b>INSERT 한 문장</b>으로 넣는다 — 열마다 배열 하나를 바인딩하고
 *       {@code unnest} 로 펼친다. SQL 문자열이 행 수와 무관하게 고정이라 드라이버의 SQL 파싱 캐시도 커지지 않는다
 *       (event 서버 OOM 원인이 행 수만큼 다른 SQL 문자열이었다).</li>
 *   <li>{@code ON CONFLICT … DO UPDATE … WHERE 값이 다를 때만} — 같은 날을 5분마다 다시 받아도 바뀐 행(출차여부 등)만 실제로 쓴다.</li>
 *   <li>같은 회차 안에 같은 일련번호가 두 번 오면 마지막 것만 남긴다(한 문장에서 같은 행을 두 번 고치면 PostgreSQL 이 오류를 낸다).</li>
 * </ul>
 *
 * <h3>원본 열</h3>
 * 프로시저 결과 열 이름이 한글이다(입차일자·입차시각·차량번호·주차일련번호 …). 일자·시각이 문자열이든 날짜형이든
 * 모두 받도록 변환한다. 없는 열은 null.
 */
@Slf4j
@Service
public class ParkingSyncService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter PROC_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter HMS = DateTimeFormatter.ofPattern("HH:mm:ss");
    /** 프로시저 이름은 yml 에서 오지만 SQL 에 그대로 들어가므로 이름 형태만 허용 */
    private static final Pattern PROC_NAME = Pattern.compile("^[A-Za-z0-9_.\\[\\]]+$");

    /** 결과 1행에서 뽑는 열(입차·출차 공통 순서). 이름만 입차/출차로 다르다 */
    private record Columns(String date, String time, String card, String type, String customerType) {
    }

    private static final Columns IN_COLUMNS = new Columns("입차일자", "입차시각", "입차카드번호", "입차구분", "입차고객구분");
    private static final Columns OUT_COLUMNS = new Columns("출차일자", "출차시각", "출차카드번호", "출차구분", "출차고객구분");

    /** 변환된 1행 */
    private record ParkingRow(String seq, String date, String time, String carNo, String cardNo,
                              String deviceName, String type, String customerType, String exitStatus, String carKind) {
    }

    /** 입차 UPSERT — 열마다 배열 1개를 unnest 로 펼친다. 값이 바뀐 행만 UPDATE */
    private static final String IN_UPSERT = """
            INSERT INTO tbl_parking_in AS t
                   (parking_seq, in_date, in_time, in_at, car_no, card_no, device_name, in_type, customer_type, exit_status, car_kind)
            SELECT u.seq, u.d::date, u.tm::time, ((u.d || ' ' || u.tm)::timestamp AT TIME ZONE 'Asia/Seoul'),
                   u.car, u.card, u.dev, u.typ, u.cust, u.ex, u.kind
              FROM unnest(CAST(:seq AS text[]), CAST(:d AS text[]), CAST(:tm AS text[]), CAST(:car AS text[]), CAST(:card AS text[]),
                          CAST(:dev AS text[]), CAST(:typ AS text[]), CAST(:cust AS text[]), CAST(:ex AS text[]), CAST(:kind AS text[]))
                   AS u(seq, d, tm, car, card, dev, typ, cust, ex, kind)
            ON CONFLICT (parking_seq) DO UPDATE SET
                   in_date = EXCLUDED.in_date, in_time = EXCLUDED.in_time, in_at = EXCLUDED.in_at,
                   car_no = EXCLUDED.car_no, card_no = EXCLUDED.card_no, device_name = EXCLUDED.device_name,
                   in_type = EXCLUDED.in_type, customer_type = EXCLUDED.customer_type,
                   exit_status = EXCLUDED.exit_status, car_kind = EXCLUDED.car_kind,
                   updated_at = now()
             WHERE (t.in_at, t.car_no, t.card_no, t.device_name, t.in_type, t.customer_type, t.exit_status, t.car_kind)
                   IS DISTINCT FROM
                   (EXCLUDED.in_at, EXCLUDED.car_no, EXCLUDED.card_no, EXCLUDED.device_name, EXCLUDED.in_type,
                    EXCLUDED.customer_type, EXCLUDED.exit_status, EXCLUDED.car_kind)
            """;

    /** 출차 UPSERT — 입차와 같은 모양, 열 이름만 out_* */
    private static final String OUT_UPSERT = """
            INSERT INTO tbl_parking_out AS t
                   (parking_seq, out_date, out_time, out_at, car_no, card_no, device_name, out_type, customer_type, exit_status, car_kind)
            SELECT u.seq, u.d::date, u.tm::time, ((u.d || ' ' || u.tm)::timestamp AT TIME ZONE 'Asia/Seoul'),
                   u.car, u.card, u.dev, u.typ, u.cust, u.ex, u.kind
              FROM unnest(CAST(:seq AS text[]), CAST(:d AS text[]), CAST(:tm AS text[]), CAST(:car AS text[]), CAST(:card AS text[]),
                          CAST(:dev AS text[]), CAST(:typ AS text[]), CAST(:cust AS text[]), CAST(:ex AS text[]), CAST(:kind AS text[]))
                   AS u(seq, d, tm, car, card, dev, typ, cust, ex, kind)
            ON CONFLICT (parking_seq) DO UPDATE SET
                   out_date = EXCLUDED.out_date, out_time = EXCLUDED.out_time, out_at = EXCLUDED.out_at,
                   car_no = EXCLUDED.car_no, card_no = EXCLUDED.card_no, device_name = EXCLUDED.device_name,
                   out_type = EXCLUDED.out_type, customer_type = EXCLUDED.customer_type,
                   exit_status = EXCLUDED.exit_status, car_kind = EXCLUDED.car_kind,
                   updated_at = now()
             WHERE (t.out_at, t.car_no, t.card_no, t.device_name, t.out_type, t.customer_type, t.exit_status, t.car_kind)
                   IS DISTINCT FROM
                   (EXCLUDED.out_at, EXCLUDED.car_no, EXCLUDED.card_no, EXCLUDED.device_name, EXCLUDED.out_type,
                    EXCLUDED.customer_type, EXCLUDED.exit_status, EXCLUDED.car_kind)
            """;

    private final DatabaseClient parkingMssql;
    private final DatabaseClient postgres;
    private final ParkingProperties properties;

    // 이 모듈엔 DatabaseClient 빈이 셋(수집 MSSQL / 주차 MSSQL / PostgreSQL) — 생성자에서 이름으로 고른다
    public ParkingSyncService(@Qualifier("parkingMssqlDatabaseClient") DatabaseClient parkingMssql,
                              @Qualifier("postgresDatabaseClient") DatabaseClient postgres,
                              ParkingProperties properties) {
        this.parkingMssql = parkingMssql;
        this.postgres = postgres;
        this.properties = properties;
        requireProcName(properties.inProcedure());
        requireProcName(properties.outProcedure());
    }

    /** 한 회차. 오늘 날짜, 자정 직후 창 안이면 어제 날짜도. 결과는 로그로. */
    public Mono<Void> runOneCycle() {
        ZonedDateTime now = ZonedDateTime.now(KST);
        LocalDate today = now.toLocalDate();
        List<LocalDate> dates = new ArrayList<>(2);
        if (!properties.previousDayWindow().isZero()
                && now.isBefore(today.atStartOfDay(KST).plus(properties.previousDayWindow()))) {
            dates.add(today.minusDays(1));
        }
        dates.add(today);

        // 날짜끼리·입출차끼리 순서대로(동시에 MSSQL 에 여러 요청을 던지지 않는다)
        return Flux.fromIterable(dates)
                .concatMap(this::syncDate)
                .then();
    }

    /** 스케줄러에서 호출 — 사이클 실패가 스케줄을 죽이지 않도록 로깅 후 흡수한다(다음 주기 재시도). */
    public Mono<Void> runOneCycleSafely() {
        return runOneCycle()
                .doOnError(e -> log.error("[자동차 주차] 사이클 실패(다음 주기 재시도): {}", e.getMessage(),e))
                .onErrorResume(e -> Mono.empty());
    }

    private Mono<Void> syncDate(LocalDate date) {
        String arg = PROC_DATE.format(date);
        long started = System.currentTimeMillis();
        return sync(properties.inProcedure(), arg, IN_COLUMNS, IN_UPSERT)
                .zipWhen(in -> sync(properties.outProcedure(), arg, OUT_COLUMNS, OUT_UPSERT))
                .doOnNext(r -> log.info("[Parking] {} 적재 — 입차 {}행 수신/{}행 변경, 출차 {}행 수신/{}행 변경 ({}ms)",
                        arg, r.getT1()[0], r.getT1()[1], r.getT2()[0], r.getT2()[1],
                        System.currentTimeMillis() - started))
                .then();
    }

    /** 프로시저 호출 → 변환 → UPSERT. 결과 [수신 행 수, 변경(INSERT+UPDATE) 행 수] */
    private Mono<long[]> sync(String procedure, String arg, Columns cols, String upsertSql) {
        // 날짜 인자는 우리가 만든 8자리 숫자뿐이라 그대로 넣어도 주입 위험이 없다
        String exec = "EXEC " + procedure + " '" + arg + "'";
        return parkingMssql.sql("EXEC " + procedure + " @date")
                .bind("date", arg)
                .map((row, meta) -> toRow(row, meta, cols))

                .all()
                //.doOnNext(r -> log.info("[Parking] {} 행 수신 {}", exec, r.seq()))
                .doOnComplete(() -> log.info("[Parking] {} 행 수신 완료", exec))
                //.filter(r -> r.seq() != null && r.date() != null && r.time() != null)
                .collectList()
                .flatMap(rows -> {
                    if (rows.isEmpty()) {
                        return Mono.just(new long[]{0, 0});
                    }
                    List<ParkingRow> unique = dedupe(rows);
                    return upsert(upsertSql, unique).map(changed -> new long[]{rows.size(), changed});
                })
                .doOnError(e -> log.error("[Parking] {} 실패: {}", exec, e.getMessage()));
    }

    private Mono<Long> upsert(String sql, List<ParkingRow> rows) {
        int n = rows.size();
        String[] seq = new String[n], d = new String[n], tm = new String[n], car = new String[n], card = new String[n],
                dev = new String[n], typ = new String[n], cust = new String[n], ex = new String[n], kind = new String[n];
        for (int i = 0; i < n; i++) {
            ParkingRow r = rows.get(i);
            seq[i] = r.seq();
            d[i] = r.date();
            tm[i] = r.time();
            car[i] = r.carNo();
            card[i] = r.cardNo();
            dev[i] = r.deviceName();
            typ[i] = r.type();
            cust[i] = r.customerType();
            ex[i] = r.exitStatus();
            kind[i] = r.carKind();
        }
        return postgres.sql(sql)
                .bind("seq", seq).bind("d", d).bind("tm", tm).bind("car", car).bind("card", card)
                .bind("dev", dev).bind("typ", typ).bind("cust", cust).bind("ex", ex).bind("kind", kind)
                .fetch()
                .rowsUpdated();
    }

    /** 같은 일련번호가 두 번 오면 마지막 것만(입력 순서 유지) */
    private static List<ParkingRow> dedupe(List<ParkingRow> rows) {
        Map<String, ParkingRow> map = new LinkedHashMap<>();
        for (ParkingRow r : rows) map.put(r.seq(), r);
        return new ArrayList<>(map.values());
    }

    private static ParkingRow toRow(Readable row, RowMetadata meta, Columns cols) {
        return new ParkingRow(
                text(row, meta, "주차일련번호"),
                normalizeDate(raw(row, meta, cols.date())),
                normalizeTime(raw(row, meta, cols.time())),
                text(row, meta, "차량번호"),
                text(row, meta, cols.card()),
                text(row, meta, "장비명"),
                text(row, meta, cols.type()),
                text(row, meta, cols.customerType()),
                text(row, meta, "출차여부"),
                text(row, meta, "차량종류"));
    }

    // ───────────── 값 변환 ─────────────

    private static Object raw(Readable row, RowMetadata meta, String column) {
        if (!hasColumn(meta, column)) return null;
        return row.get(column);
    }

    private static boolean hasColumn(RowMetadata meta, String column) {
        for (ReadableMetadata m : meta.getColumnMetadatas()) {
            if (column.equalsIgnoreCase(m.getName())) return true;
        }
        return false;
    }

    /** 문자열 열 — 앞뒤 공백 제거, 빈 문자열은 null */
    private static String text(Readable row, RowMetadata meta, String column) {
        Object v = raw(row, meta, column);
        if (v == null) return null;
        String s = v.toString().trim();
        return s.isEmpty() ? null : s;
    }

    /** 일자 → 'yyyy-MM-dd'. 날짜형·일시형·'2026-10-02'·'20261002' 모두 받는다 */
    private static String normalizeDate(Object v) {
        if (v == null) return null;
        if (v instanceof LocalDate ld) return ld.toString();
        if (v instanceof LocalDateTime ldt) return ldt.toLocalDate().toString();
        String s = v.toString().trim();
        if (s.matches("\\d{8}")) return s.substring(0, 4) + "-" + s.substring(4, 6) + "-" + s.substring(6, 8);
        if (s.length() >= 10 && s.matches("\\d{4}-\\d{2}-\\d{2}.*")) return s.substring(0, 10);
        return null;
    }

    /** 시각 → 'HH:mm:ss'. 시간형·일시형·'00:21:57'·'002157' 모두 받는다 */
    private static String normalizeTime(Object v) {
        if (v == null) return null;
        if (v instanceof LocalTime lt) return HMS.format(lt);   // toString 은 초가 0 이면 "HH:mm" 이라 쓰지 않는다
        if (v instanceof LocalDateTime ldt) return normalizeTime(ldt.toLocalTime());
        String s = v.toString().trim();
        if (s.matches("\\d{6}")) return s.substring(0, 2) + ":" + s.substring(2, 4) + ":" + s.substring(4, 6);
        if (s.matches("\\d{2}:\\d{2}:\\d{2}.*")) return s.substring(0, 8);
        if (s.matches("\\d{2}:\\d{2}")) return s + ":00";
        return null;
    }

    private static void requireProcName(String name) {
        if (name == null || !PROC_NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("hdt.parking 프로시저 이름이 올바르지 않습니다: " + name);
        }
    }
}
