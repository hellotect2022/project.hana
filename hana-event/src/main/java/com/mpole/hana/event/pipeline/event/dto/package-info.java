/**
 * 이벤트 파이프라인이 주고받는 <b>데이터만</b> 모아둔 곳. 로직은 상위 패키지에 있다.
 *
 * <h3>DB 원본 행 (Reader 가 읽어 오는 그대로)</h3>
 * {@link com.mpole.hana.event.pipeline.event.dto.RuleRow},
 * {@link com.mpole.hana.event.pipeline.event.dto.RuleLevelRow},
 * {@link com.mpole.hana.event.pipeline.event.dto.CategoryRow},
 * {@link com.mpole.hana.event.pipeline.event.dto.ActiveEventRow}
 *
 * <h3>가공된 조회 구조</h3>
 * {@link com.mpole.hana.event.pipeline.event.dto.RuleView} — 규칙+레벨 결합 뷰 ·
 * {@link com.mpole.hana.event.pipeline.event.dto.RuleIndex} — 포인트로 규칙을 O(1) 조회하는 역인덱스 스냅샷
 *
 * <h3>런타임 상태 / 결과</h3>
 * {@link com.mpole.hana.event.pipeline.event.dto.ActiveEvent} — 지금 떠 있는 이벤트 1건 ·
 * {@link com.mpole.hana.event.pipeline.event.dto.EvalOutcome} — 평가 결과(발생 또는 해소)
 *
 * <p>모두 불변 record 이고 digitaltwin enum 에 의존하지 않는다. level/scope/operator 등은
 * DB 에 저장된 enum 이름 문자열을 그대로 담는다(모듈 간 결합 회피).</p>
 */
package com.mpole.hana.event.pipeline.event.dto;
