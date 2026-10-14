package com.mpole.hana.digitaltwin.deviceGroup.dto;


import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Map;

/**
 * 그룹 생성·수정 요청(공용).
 *
 * <p>{@code groupCode} 는 생성 시 클라이언트가 정한다 — 외부 연동 식별자를 겸하기 때문이다
 * (HQS {@code wcBoothId}). 허용 문자를 제한하는 이유는 HQS 응답에 그대로 실린다는 것.</p>
 *
 * <p>{@code groupCode} 는 수정할 수 있다(중복이면 409). 숫자 PK 라 코드 변경이 그룹 한 행
 * UPDATE 로 끝나고 소속 장비는 영향받지 않는다. 다만 외부 연동(HQS {@code wcBoothId})이
 * 이 값을 식별자로 쓴다.</p>
 *
 * <p>{@code groupType} 은 수정에서 무시한다. 용도를 바꾸면 이미 배정된 장비가 엉뚱한 곳에
 * 남으므로, 그룹을 새로 만들어 옮기는 것이 맞다.</p>
 *
 * <p>{@code groupType} 이 enum 이 아니라 {@code String} 인 것은 의도다 — DB 가 CHECK 없는
 * {@code varchar} 라 용도를 나중에 늘릴 수 있고, enum 으로 받으면 모르는 값에서 터진다.
 * 서버는 대문자 정규화만 하고 모르는 값도 그대로 받는다.</p>
 */
public record DeviceGroupRequest(

        @NotBlank(message = "groupCode 는 필수입니다.")
        @Size(max = 100, message = "groupCode 는 100자 이하여야 합니다.")
        @Pattern(regexp = "^[A-Za-z0-9_.:-]+$",
                message = "groupCode 는 영문·숫자와 _ . : - 만 쓸 수 있습니다.")
        String groupCode,

        @NotBlank(message = "groupName 은 필수입니다.")
        @Size(max = 200, message = "groupName 은 200자 이하여야 합니다.")
        String groupName,

        // 생성 시 필수(서비스에서 검증). 수정 시에는 무시된다.
        @Size(max = 40, message = "groupType 은 40자 이하여야 합니다.")
        String groupType,

        Integer sortOrder,

        Map<String, Object> attributes,

        Boolean active,

        String description
) {
}
