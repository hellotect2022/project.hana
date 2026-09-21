package com.mpole.hana.digitaltwin.command.entity;

import lombok.Getter;

/**
 * 제어 포인트의 조작 방식.
 *
 * <p>유형에 따라 <b>화면 컨트롤</b>과 <b>전송값 검증</b>이 갈린다. DB 에는 상수명 문자열로 저장된다.</p>
 *
 * <table border="1">
 *   <tr><th>유형</th><th>화면</th><th>전송값</th><th>필요 설정</th></tr>
 *   <tr><td>TOGGLE</td><td>버튼 2개</td><td>on_value / off_value</td><td>on_value, off_value</td></tr>
 *   <tr><td>ENUM</td><td>옵션 수만큼 버튼</td><td>선택한 value</td><td>options</td></tr>
 *   <tr><td>RANGE</td><td>숫자 입력 + 전송</td><td>입력값 그대로</td><td>min_value, max_value</td></tr>
 * </table>
 *
 * <p><b>순간 명령(정지 등)은 옵션 1개짜리 ENUM</b> 으로 표현한다 — 별도 MOMENTARY 유형을 두지 않는다.
 * 버튼 1개에 값 1개를 보내는 동작이 정확히 같고, 유형이 하나 줄어든다.</p>
 */
@Getter
public enum CommandType {

    /** 두 값 전환. 현재값과 일치하는 쪽을 하이라이트한다. */
    TOGGLE("토글(ON/OFF)"),

    /** 미리 정한 값 중 선택. 옵션이 1개면 순간 명령 버튼이 된다. */
    ENUM("선택(옵션)"),

    /** 숫자 직접 입력. min~max 범위와 step 단위를 서버가 검증한다. */
    RANGE("숫자 입력(범위)");

    private final String description;

    CommandType(String description) {
        this.description = description;
    }
}
