/**
 * 이벤트 도메인 엔티티 — <b>설정(Rule) → 발생(Event)</b> 2계층. (발행 계층 tbl_event_action 은 2026-10-02 폐기)
 *
 * <h2>용어</h2>
 * <table border="1">
 *   <tr><th>계층</th><th>테이블</th><th>의미</th></tr>
 *   <tr><td>Rule</td><td>tbl_event_rule + _level</td><td><b>설정</b> — 무엇을 감시하고, 걸리면 어떤 채널을 켤지</td></tr>
 *   <tr><td>Event</td><td>tbl_event</td><td><b>발생</b> — 실제로 일어난 한 건(불변 기록 + 상태 2축 + 켜진 채널 스냅샷)</td></tr>
 * </table>
 *
 * <p><b>event 와 alarm 의 관계</b> — event 는 "일어난 모든 것"이고, alarm 은 "액션이 붙어 대응이
 * 필요한 event"를 가리키는 <i>의미</i>일 뿐 별도 테이블이 아니다. 그래서 액션이 0개인 event
 * (예: 화면 색상만 바뀌는 표시 전용)도 정상이며 {@code tbl_event} 에 그대로 기록된다.</p>
 *
 * <h2>구조와 관계</h2>
 * <pre>
 *   ┌──────────────────────────────────┐
 *   │ tbl_event_rule            [설정] │  무엇을·어떻게 감시할지
 *   │  rule_id (PK)                    │
 *   │  scope ─┬─ CATEGORY → category_id│───소프트 참조──▶ tbl_device_category
 *   │         └─ DEVICE   → device_id  │───소프트 참조──▶ tbl_device
 *   │  tag_name  (감시 대상 포인트)     │
 *   │  condition_kind / operator       │
 *   │  sustain_sec / deadband          │
 *   │  suppress_mode                   │
 *   └───────────────┬──────────────────┘
 *                   │ 1 : N   (진짜 FK · fk_event_rule_level_rule · ON DELETE CASCADE)
 *   ┌───────────────▼──────────────────┐
 *   │ tbl_event_rule_level      [설정] │  등급별 임계값과 켤 채널
 *   │  level_id (PK) / rule_id (FK)    │
 *   │  level  CAUTION|WARNING|CRITICAL │
 *   │  threshold_value                 │
 *   │  outputs (jsonb)  ───────────────┼──┐ 발생 순간 "그대로 복사"
 *   └──────────────────────────────────┘  │ (정책이 나중에 바뀌어도 과거 이벤트의 의미는 안 변함)
 *                                          │
 *   ┌──────────────────────────────────┐  │
 *   │ tbl_event                 [발생] │◀─┘
 *   │  event_id (PK, UUIDv7)           │  발생 시점에 서버가 생성 → 웹소켓 id 와 동일
 *   │  rule_id / rule_name  (스냅샷)    │  소프트 참조 + 이름 박제
 *   │  device_id / point_id / tag_name │  소프트 참조
 *   │  level / value_raw / outputs     │  발생 순간의 값·채널 스냅샷
 *   │  ── Active 축(시스템) ──          │
 *   │  cleared_at / cleared_value      │  UNIQUE(rule_id,device_id,point_id) WHERE cleared_at IS NULL
 *   │  occurrence_count / last_occurred_at │
 *   │  ── Ack 축(사람) ──               │
 *   │  acked_at / acked_by             │  확인 = 종료 (미해소면 cleared_at 도 닫힘)
 *   │  outputs (jsonb)                 │  켜진 채널 스냅샷 — 클라이언트 표출 근거
 *   └──────────────────────────────────┘
 * </pre>
 *
 * <h2>동작 원리 — 한 포인트 값이 들어왔을 때</h2>
 * <pre>
 *  1. 매칭   device+tag 로 규칙을 찾는다(개별 DEVICE 규칙 우선, 없으면 소속 카테고리 규칙 상속)
 *  2. 판정   operator 가 비교(GTE/LTE/GT/LT) → 레벨별 threshold_value 비교, 만족 중 최고 심각도 선택
 *            operator 가 일치(EQ)           → 레벨별 trigger_value(쉼표 집합) 일치, 만족 중 최고 심각도 선택
 *  3. 전이   조건 성립 & 활성 없음 → 발생   (UUIDv7 생성 → tbl_event INSERT → 웹소켓 {code})
 *            조건 성립 & 활성 있음 → 무시   (중복 억제 — 그래서 cooldown 이 필요 없다)
 *            조건 성립 & 해소·미확인 → 재활성 (같은 행 cleared_at=null, occurrence_count+1 → 같은 eventId 재발송)
 *            조건 해소 & 활성 있음 → 해소   (cleared_* 기록 → 웹소켓 {code}_CLEAR. 미확인이면 억제 유지)
 *            조건 해소 & 활성 없음 → 무시
 *  4. 확인   관제자가 확인하면 acked_at/acked_by 기록 + 미해소면 cleared_at 도 닫음 = 종료 → 웹소켓 {code}_ACK
 *            사건형(NONE)은 발행과 동시에 자동 완료(acked_at=occurred_at, acked_by NULL)
 * </pre>
 *
 * <p><b>왜 상태가 2축인가</b>(ISA-18.2 모델) — {@code cleared_at}(조건이 해소됐는가, 시스템 판단)와
 * {@code acked_at}(관제자가 확인했는가)은 서로 독립이다. 값이 정상 복귀해도 아무도 확인하지 않았을 수 있다.
 * 완료 여부는 {@code acked_at} 하나로 판정한다(2026-10-02). 두 축의 조합이 {@link com.mpole.hana.digitaltwin.event.entity.EventStatus} 다.</p>
 *
 * <h2>쓰기 주체</h2>
 * <ul>
 *   <li>{@code tbl_event_rule} / {@code _level} — digitaltwin(JPA). 관리자 화면이 CRUD</li>
 *   <li>{@code tbl_event} 발생/해소/재활성 · 웹소켓 ACK 확인 — <b>event-server(R2DBC)</b></li>
 *   <li>{@code tbl_event} 확인(ack) REST 보조 경로 — digitaltwin(30초 스윕으로 event-server 에 반영)</li>
 * </ul>
 *
 * <p><b>@Comment 주의</b> — Hibernate 의 {@code @Comment} 는 DDL 을 생성할 때만 반영된다.
 * 이 테이블들은 마이그레이션 SQL 로 만들었고 {@code ddl-auto=update} 는 기존 컬럼에 코멘트를
 * 소급 추가하지 않으므로, 실제 DB 주석은 {@code scripts/event/2026-09-04-column-comments.sql} 로 넣는다.</p>
 */
package com.mpole.hana.digitaltwin.event.entity;
