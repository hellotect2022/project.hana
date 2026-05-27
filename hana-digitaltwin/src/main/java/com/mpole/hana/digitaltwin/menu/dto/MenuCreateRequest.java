package com.mpole.hana.digitaltwin.menu.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record MenuCreateRequest(
        @NotBlank(message = "메뉴명은 필수입니다.") String menuName,
        @NotBlank(message = "메뉴 코드는 필수입니다.") String menuCode,
        @NotNull(message = "정렬 순서는 필수입니다.") Integer sortOrder
) {
}
