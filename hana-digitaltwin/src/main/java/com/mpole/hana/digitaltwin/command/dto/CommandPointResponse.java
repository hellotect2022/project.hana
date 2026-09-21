package com.mpole.hana.digitaltwin.command.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.mpole.hana.digitaltwin.command.entity.CommandPoint;
import com.mpole.hana.digitaltwin.command.entity.CommandPoint.CommandOption;
import com.mpole.hana.digitaltwin.command.entity.CommandType;
import com.mpole.hana.digitaltwin.device.entity.Device;
import com.mpole.hana.digitaltwin.device.entity.DevicePoint;
import lombok.Builder;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 디바이스 제어 포인트 응답. 제어 대상 포인트의 tagName/ref 코드까지 함께 노출한다.
 *
 * <p>유형별 설정(TOGGLE 라벨 / ENUM 옵션 / RANGE 범위)을 모두 내려주므로
 * 관리자 화면은 이 응답만으로 편집 폼을 구성할 수 있다.</p>
 *
 * <p>{@code @JsonInclude(NON_NULL)} — <b>그 유형이 쓰지 않는 필드는 응답에서 아예 빠진다.</b>
 * 저장 단계에서 무관한 값을 null 로 정리하므로(CommandPointService.applyTypeFields),
 * RANGE 에 onValue 가 따라오거나 TOGGLE 에 minValue: null 이 줄줄이 붙지 않는다.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Builder
public record CommandPointResponse(
        Long commandPointId,
        Long deviceId,
        String deviceName,
        Long pointId,
        String tagName,
        String refDeviceCode,
        String refPointCode,
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
        int sortOrder,
        Boolean active,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static CommandPointResponse from(CommandPoint c) {
        if (c == null) return null;
        Device d = c.getDevice();
        DevicePoint p = c.getPoint();
        return CommandPointResponse.builder()
                .commandPointId(c.getCommandPointId())
                .deviceId(d != null ? d.getDeviceId() : null)
                .deviceName(d != null ? d.getDeviceName() : null)
                .pointId(p != null ? p.getPointId() : null)
                .tagName(p != null ? p.getTagName() : null)
                .refDeviceCode(p != null ? p.getRefDeviceCode() : null)
                .refPointCode(p != null ? p.getRefPointCode() : null)
                .label(c.getLabel())
                .commandType(c.getCommandType())
                .onValue(c.getOnValue())
                .offValue(c.getOffValue())
                .onLabel(c.getOnLabel())
                .offLabel(c.getOffLabel())
                .minValue(c.getMinValue())
                .maxValue(c.getMaxValue())
                .stepValue(c.getStepValue())
                .unit(c.getUnit())
                .options(c.getOptions())
                .sortOrder(c.getSortOrder())
                .active(c.getActive())
                .createdAt(c.getCreatedAt())
                .updatedAt(c.getUpdatedAt())
                .build();
    }
}
