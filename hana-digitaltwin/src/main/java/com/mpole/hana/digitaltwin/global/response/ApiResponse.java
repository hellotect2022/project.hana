package com.mpole.hana.digitaltwin.global.response;

import lombok.Builder;
import org.springframework.data.domain.Page;

import java.util.List;

@Builder
public record ApiResponse<T>(
        boolean success,
        String message,
        T data,
        String errorCode
) {
    public static <T>ApiResponse<T> success(String message, T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .message(message)
                .data(data)
                .build();
    }

    // 💡 2. Page 객체를 넘기면 자동으로 PageResponse로 변환하는 오버로딩 메서드
    public static <T> ApiResponse<ApiResponse.PageResponse<T>> success(String message, Page<T> page) {
        return ApiResponse.<ApiResponse.PageResponse<T>>builder()
                .success(true)
                .message(message)
                .data(PageResponse.from(page))
                .build();
    }

    public static <T> ApiResponse<T> success(String message) {
        return ApiResponse.<T>builder()
                .success(true)
                .message(message)
                .build();
    }
    public static <T> ApiResponse<T> error(String message, String errorCode) {
        return ApiResponse.<T>builder()
                .success(false)
                .message(message)
                .errorCode(errorCode)
                .build();
    }

    public record PageResponse<T>(
            List<T> content,
            PageInfo page
    ) {
        public static <T> PageResponse<T> from(Page<T> page) {
            return new PageResponse<>(
                    page.getContent(),
                    new PageInfo(
                            page.getTotalElements(),
                            page.getTotalPages(),
                            page.getNumber(),
                            page.getSize(),
                            page.isFirst(),
                            page.isLast()
                    )
            );
        }

        public record PageInfo(
                long totalElements,
                int totalPages,
                int number,
                int size,
                boolean first,
                boolean last
        ) {
        }
    }
}
