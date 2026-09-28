package com.mpole.hana.digitaltwin.deviceGroup.dto;

import com.mpole.hana.digitaltwin.deviceGroup.entity.DeviceGroupRole;
import com.mpole.hana.digitaltwin.deviceGroup.entity.DeviceGroupType;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 관리자 화면 select 를 채우기 위한 코드 목록.
 *
 * <p>{@code groupType}·{@code role} 은 DB 에서 CHECK 없는 {@code varchar} 라 값을 늘릴 수
 * 있다. 그래서 이 목록은 <b>강제 목록이 아니라 추천 목록</b>이다 — 화면은 이걸 기본
 * 선택지로 보여주되 직접 입력도 받아야 한다.</p>
 *
 * <p>{@code groupTypes} 에는 enum 에 정의된 값과 <b>DB 에 실제로 쓰이고 있는 값</b>을
 * 합쳐서 내려준다. 누군가 SQL 로 새 용도를 넣었다면 화면에서도 보여야 한다.</p>
 */
public record DeviceGroupMetaResponse(
        List<CodeItem> groupTypes,
        List<CodeItem> roles
) {
    public record CodeItem(String code, String label) {
    }

    public static DeviceGroupMetaResponse of(List<String> usedGroupTypes) {
        Set<String> codes = new LinkedHashSet<>();
        DeviceGroupType.all().forEach(t -> codes.add(t.name()));
        if (usedGroupTypes != null) {
            usedGroupTypes.stream().filter(c -> c != null && !c.isBlank()).forEach(codes::add);
        }

        return new DeviceGroupMetaResponse(
                codes.stream()
                        .map(c -> new CodeItem(c, DeviceGroupType.labelOf(c)))
                        .toList(),
                DeviceGroupRole.all().stream()
                        .map(r -> new CodeItem(r.name(), r.getLabel()))
                        .toList()
        );
    }
}
