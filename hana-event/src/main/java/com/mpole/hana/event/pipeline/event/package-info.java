/**
 * 이벤트 도메인 파이프라인 — <b>규칙 판정부터 발행까지 한 곳</b>.
 *
 * <p>이전에는 {@code pipeline/rule}(규칙 로딩·평가)과 {@code pipeline/event}(이력)로 갈려 있었는데,
 * 실제로는 하나의 흐름이라 합쳤다. 데이터(레코드)는 {@code dto} 하위로 분리하고 여기에는 로직만 둔다.</p>
 *
 * <h3>흐름</h3>
 * <pre>
 *  값 스트림(ReactiveQueue)
 *      │
 *      ▼
 *  EventEvaluationPipeline ── 조립·구독·에러 격리
 *      │
 *      ├─ EventEvaluator ─────── 조건 판정 + Active 축 전이 → EvalOutcome(발생 | 해소)
 *      │     ├─ EventRuleCache ── 규칙 역인덱스 스냅샷(30초 갱신)
 *      │     │     └─ EventRuleReader ── tbl_event_rule / _level / 카테고리 트리 조회
 *      │     └─ ActiveEventStore ─ 지금 떠 있는 이벤트(중복 억제·재기동 복원·ack 스윕)
 *      │
 *      ├─ EventWriter ────────── tbl_event 발생 INSERT / 해소·재활성·확인 UPDATE
 *      ├─ EventAckService ────── 웹소켓 ACK → DB → 활성맵 제거 → {code}_ACK 브로드캐스트
 *      └─ WebsocketBroadcastService ── {code} / {code}_CLEAR / {code}_ACK 발행
 *   (EventActionWriter / tbl_event_action 은 2026-10-02 폐기)
 * </pre>
 *
 * <h3>역할 구분</h3>
 * <ul>
 *   <li><b>읽기</b> {@link com.mpole.hana.event.pipeline.event.EventRuleReader}</li>
 *   <li><b>상태</b> {@link com.mpole.hana.event.pipeline.event.EventRuleCache}(설정 스냅샷),
 *       {@link com.mpole.hana.event.pipeline.event.ActiveEventStore}(활성 이벤트)</li>
 *   <li><b>판정</b> {@link com.mpole.hana.event.pipeline.event.EventEvaluator}</li>
 *   <li><b>쓰기</b> {@link com.mpole.hana.event.pipeline.event.EventWriter},
 *       {@link com.mpole.hana.event.pipeline.event.EventActionWriter}</li>
 *   <li><b>조립</b> {@link com.mpole.hana.event.pipeline.event.EventEvaluationPipeline}</li>
 * </ul>
 *
 * <p>{@code EventHistoryService} 만 계보가 다르다 — 이벤트 도메인이 아니라 장비 값 번들을
 * {@code tbl_device_event_history} 에 적재하는 코드이며 현재 구독이 꺼져 있다(그 클래스 주석 참고).</p>
 */
package com.mpole.hana.event.pipeline.event;
