package com.mpole.hana.digitaltwin.command.dto;

import com.mpole.hana.digitaltwin.command.entity.CommandPoint.CommandOption;
import com.mpole.hana.digitaltwin.command.entity.CommandType;
import lombok.Builder;

import java.util.List;

/**
 * 디바이스 제어 포인트 등록/수정 요청.
 *
 * <p>유형별로 필요한 필드가 다르며, 검증은 {@code CommandPointService.validate()} 가 한다.
 * {@code commandType} 을 보내지 않으면 {@link CommandType#TOGGLE} 로 처리한다(하위호환).</p>
 *
 * @param deviceId    대상 디바이스 ID
 * @param pointId     제어에 사용할 포인트(tbl_device_point) ID — 해당 디바이스 소속이어야 함
 * @param label       제어 표시명
 * @param commandType 제어 유형 (null 이면 TOGGLE)
 * @param onValue     [TOGGLE] ON 전송값 (null 이면 기본 "1")
 * @param offValue    [TOGGLE] OFF 전송값 (null 이면 기본 "0")
 * @param onLabel     [TOGGLE] ON 버튼 표시명 (예: 올림)
 * @param offLabel    [TOGGLE] OFF 버튼 표시명 (예: 내림)
 * @param minValue    [RANGE] 허용 최솟값(포함)
 * @param maxValue    [RANGE] 허용 최댓값(포함)
 * @param stepValue   [RANGE] 입력 단위 (1=정수만, 0.5=반값 허용, null=제약 없음)
 * @param unit        [RANGE] 표시용 단위 (%, ℃)
 * @param options     [ENUM] 선택지 목록 — 1개면 버튼 하나짜리 순간 명령이 된다
 * @param sortOrder   정렬 순서
 * @param active      활성 여부 (null 이면 true)
 */
@Builder
public record CommandPointRequest(
        Long deviceId,
        Long pointId,
        String label,
        CommandType commandType,
        String onValue,
        String offValue,
        String onLabel,
        String offLabel,
        Double minValue,
        Double maxValue,
        Double stepValue,
        String unit,
        List<CommandOption> options,
        Integer sortOrder,
        Boolean active
) {
}
