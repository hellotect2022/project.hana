package com.mpole.hana.digitaltwin.menu.dto;

import jakarta.validation.constraints.NotBlank;

public record MenuUpdateRequest(
        @NotBlank(message = "메뉴명은 필수입니다.") String menuName,
        @NotBlank(message = "메뉴 코드는 필수입니다.") String menuCode,
        Boolean active
) {
}
