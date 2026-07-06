# 작업 보고서 — Loop 03-C: digitaltwin aggregator 필드 확장

- 작성일: 2026-07-01
- Loop: 03-C
- 담당 AGENT: spring-backend-agent
- 관련 승인 요청서: `qa/change-requests/2026-07-01-loop03-monitoring-console-fields.md` (사용자 승인 2026-07-01)
- 선행: 03-A(devops: build.gradle actuator compileOnly + yml show-details) QA PASS, 03-B(hana-common DTO 확장 + PidInfoContributor auto-config) QA PASS

## 1. 작업 목표
기존 aggregator `GET /api/monitor/status`(MonitorService/MonitorController)를 확장해 콘솔 화면
필드를 모두 채운다.
- 서비스별 PID / 시작시각(`/actuator/info`의 process.pid / process.startTime)
- 상단 호스트 카드 `HostSummary`(CPU / 힙 메모리 / 디스크 / DB / 네트워크), self(digitaltwin) 기준

## 2. 변경 요약
- 서비스별 폴링 시 UP 상태이면 `/actuator/info`를 추가 호출해 `pid`/`startedAt`을 채운다.
  info 수집·파싱 실패는 두 필드만 null로 두고 서비스 status는 기존 로직(health) 유지.
- self(digitaltwin) 기준으로 `HostSummary` 조립 로직 신규 추가.
  - `cpuUsage` = metric `system.cpu.usage` (실패 시 -1.0)
  - `memoryUsage` = **JVM 힙 사용률** = `jvm.memory.used?tag=area:heap` / `jvm.memory.max?tag=area:heap`.
    태그 `area:heap`을 쿼리파라미터로 전달해 nonheap 합산 왜곡 방지. max<=0/실패 시 -1.0.
  - `diskUsage` = health `components.diskSpace.details.{total,free}` → (total-free)/total. 실패/범위이탈 시 -1.0.
  - `dbStatus` = health components의 `db`(우선) 또는 `r2dbc` status → UP/그외 DOWN, 컴포넌트 부재 UNKNOWN.
  - `networkStatus` = 의존처 host:port TCP 도달성. 전부 도달 UP / 하나라도 실패 DOWN / 목록 비면 UNKNOWN.
    대상은 `@Value("${hdt.monitor.network-targets:192.168.250.181:5432,192.168.250.249:9092}")` 코드 기본값
    (postgres+kafka). yml override 가능하나 이 Loop에서 yml은 수정하지 않음.
- `getSnapshot()`이 `List<ServiceMonitor>` + `HostSummary`로 `MonitorSnapshot(timestamp, services, host)` 구성.
- 컨트롤러 시그니처/경로(`GET /api/monitor/status`, `ApiResponse` 래퍼)는 유지, Swagger `@Operation` 설명만 갱신
  (host 필드·힙 사용률 주의 문구 추가).

## 3. 변경 파일 목록
### 수정
- `hana-digitaltwin/src/main/java/com/mpole/hana/digitaltwin/monitor/service/MonitorService.java`
  - import 추가: `HostSummary`, `java.net.InetSocketAddress`, `java.net.Socket`,
    `java.time.format.DateTimeParseException`, `java.util.ArrayList`
  - 생성자에 `network-targets` `@Value` 파라미터 추가 + `parseNetworkTargets()`로 파싱
  - `pollService()`: UP일 때 `collectProcessInfo()` 호출, ServiceMonitor 빌더에 `pid`/`startedAt` 세팅
    (DOWN/UNKNOWN/health실패 분기에도 pid/startedAt=null 명시)
  - 신규 private 메서드: `collectProcessInfo`, `collectHostSummary`, `collectHeapUsage`,
    `collectDiskUsage`, `collectDbStatus`, `collectNetworkStatus`, `isReachable`, `fetchHealth`,
    `taggedMetricValue`, `metricValueInternal`(기존 metricValue 내부 위임),
    파싱 헬퍼 `parseNetworkTargets`/`asLong`/`asOffsetDateTime`/`valueAsMap`/`nestedMap`
  - 내부 record `ProcessInfo(Long pid, OffsetDateTime startedAt)` 추가
- `hana-digitaltwin/src/main/java/com/mpole/hana/digitaltwin/monitor/api/MonitorController.java`
  - `@Operation.description`만 갱신(host 요약·힙 사용률 명시). 경로/시그니처/보안 무변경.

### 신규
- 없음(기존 MonitorService/Controller 확장으로 처리).

## 4. 최종 응답 구조(개략 JSON)
```json
{
  "success": true,
  "message": "서비스 통합 모니터링 스냅샷 조회",
  "data": {
    "timestamp": "2026-07-01T10:15:30.123+09:00",
    "services": [
      {
        "service": "hana-event",
        "status": "UP",
        "responseTimeMs": 12,
        "resource": {
          "heapUsedBytes": 123456789,
          "heapMaxBytes": 536870912,
          "processCpuLoad": 0.04,
          "systemCpuLoad": 0.11,
          "threadCount": 42,
          "uptimeMs": 3600000
        },
        "detail": null,
        "pid": 12345,
        "startedAt": "2026-07-01T09:15:30Z"
      }
      // hana-gateway, hana-digitaltwin 동일 구조
    ],
    "host": {
      "cpuUsage": 0.11,
      "memoryUsage": 0.23,
      "diskUsage": 0.57,
      "dbStatus": "UP",
      "networkStatus": "UP"
    }
  }
}
```
- `host.cpuUsage`/`memoryUsage`/`diskUsage`는 0.0~1.0 분수, 측정 불가 시 `-1.0`.
- `host.memoryUsage`는 **JVM 힙 사용률**(heapUsed/heapMax), 호스트 물리메모리 아님.
- `dbStatus`/`networkStatus`는 UP/DOWN/UNKNOWN.
- 서비스가 DOWN/UNKNOWN이면 `resource`/`pid`/`startedAt`은 null.

## 5. 기술 판단 근거
- **블로킹 유지**: digitaltwin은 WebMVC/JPA 모듈이므로 RestClient(동기)와 java.net.Socket 블로킹 호출이 규약에 부합.
  리액티브(gateway/event)로의 오염 없음.
- **추가 의존성 없음**: RestClient는 spring-web 내장, Socket은 JDK. 03-A에서 확보한 actuator show-details 전제만 사용.
- **장애 격리**: info/health/metrics 호출에 기존 2초 timeout(SimpleClientHttpRequestFactory) 그대로 적용,
  네트워크 도달성은 대상당 1초 connect timeout. try-with-resources로 Socket 누수 방지.
- **파싱 방어**: Map/JsonNode 대신 `Map<String,Object>`로 받아 `instanceof` 기반 null/타입 방어.
  info의 `process.startTime`은 `Instant.toString()`(UTC, ISO-8601)이라 `OffsetDateTime.parse` 직접 호환.
  PidInfoContributor 키(`process.pid`, `process.startTime`)와 파서 키가 일치함을 확인.
- **힙 태그 정확성**: `jvm.memory.used`/`max`는 태그 없이 조회 시 heap+nonheap 합산이므로 `?tag=area:heap`을
  명시해 힙만 집계.

## 6. 컴파일 검증
- `./gradlew :hana-digitaltwin:compileJava -q` → 오류 없음(정상 종료).

## 7. 다른 AGENT 영향
- **devops-agent / yml**: 무수정. `network-targets`는 코드 기본값(현 postgres 192.168.250.181:5432,
  kafka 192.168.250.249:9092)으로 동작. 운영 값 변경 필요 시 yml에
  `hdt.monitor.network-targets` override(별도 승인 대상, 이 Loop 범위 아님).
- **SecurityConfig**: 무수정. `/api/monitor/status`는 기존 posture 그대로.
- **hana-common**: 무수정(03-B 산출물 그대로 소비).
- **react-frontend-agent**: 화면 표시명(hdt-*) ↔ hana 3서비스(hana-event/gateway/digitaltwin) 매핑은 프론트 책임.
  상단 카드는 `data.host` 사용, `host.memoryUsage`는 힙 사용률임을 UI 라벨/툴팁에 반영 권장.
  분수(0~1)이므로 ×100 표기, -1.0은 "측정 불가" 처리 필요.

## 8. QA 요청 항목
1. MonitorService: pid/startedAt 세팅 분기(UP/DOWN/UNKNOWN/health실패) 누락 없는지.
2. 힙 사용률의 `?tag=area:heap` 쿼리파라미터 전달 및 max<=0 센티넬 처리.
3. diskUsage/dbStatus의 health components 안전 파싱(컴포넌트 부재 시 -1.0/UNKNOWN).
4. networkStatus TCP 도달성: try-with-resources 누수 방지, 목록 파싱(잘못된 항목 skip), 1초 timeout.
5. 전체 지연 격리: info/health/metrics 2초 timeout 유지 확인.
6. 범위 준수: hana-digitaltwin/src 외 무수정(yml/build.gradle/SecurityConfig/hana-common).
7. 응답 하위호환: 필드 추가만(services 기존 필드 유지, host 신규).
