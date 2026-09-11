/**
 * HQS 연동 — <b>전동스크린</b> 도메인.
 *
 * <p>아직 구현 전이며 자리만 잡아 둔다. 컨트롤러·서비스·리포지토리와 응답 DTO(`dto/`)를
 * 이 패키지 안에 둔다.</p>
 *
 * <h3>패키지 규칙</h3>
 * <pre>
 *   hqs/                공용 — HqsClient(외부 호출), 예외 핸들러, 응답 봉투(dto/HqsApiResponse), 코드(code/)
 *   hqs/wc/             화장실 — 부스 재실·물리버튼
 *   hqs/charger/        전기차 충전기
 *   hqs/screen/         전동스크린 (여기)
 * </pre>
 *
 * <p>도메인 패키지는 공용을 참조하되 <b>서로를 참조하지 않는다.</b> 공통으로 쓸 것이 생기면
 * 상위 {@code hqs} 로 올린다.</p>
 *
 * <p>예외 응답은 {@code HqsApiExceptionHandler} 가 {@code hqs} 패키지 전체를 커버하므로
 * 도메인마다 따로 만들지 않는다.</p>
 */
package com.mpole.hana.digitaltwin.integration.emblaze.hqs.screen;
