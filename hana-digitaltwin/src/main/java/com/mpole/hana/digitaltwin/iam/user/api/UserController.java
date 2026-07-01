package com.mpole.hana.digitaltwin.iam.user.api;

import com.mpole.hana.digitaltwin.global.response.ApiResponse;
import com.mpole.hana.digitaltwin.iam.user.dto.UserCreateRequest;
import com.mpole.hana.digitaltwin.iam.user.dto.UserResponse;
import com.mpole.hana.digitaltwin.iam.user.dto.UserUpdateRequest;
import com.mpole.hana.digitaltwin.iam.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping
    public ResponseEntity<ApiResponse<ApiResponse.PageResponse<UserResponse>>> getUsers(
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) String loginId,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) Long roleId,
            @PageableDefault(size = 10, sort = "userId", direction = Sort.Direction.ASC) Pageable pageable
    ) {

        Page<UserResponse> result = userService.getUsers(active, loginId, username, roleId, pageable);
        return ResponseEntity.ok(ApiResponse.success("사용자 목록 조회", result));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Void>> createUser(@Valid @RequestBody UserCreateRequest request) {
        userService.createUser(request);
        return ResponseEntity.ok(ApiResponse.success("사용자 생성"));
    }

    @PutMapping("/{userId}")
    public ResponseEntity<ApiResponse<Void>> updateUser(
            @PathVariable Long userId,
            @Valid @RequestBody UserUpdateRequest request
    ) {
        userService.updateUser(userId, request);
        return ResponseEntity.ok(ApiResponse.success("사용자 수정"));
    }

    @DeleteMapping("/{userId}")
    public ResponseEntity<ApiResponse<Void>> deleteUser(@PathVariable Long userId) {
        userService.deleteUser(userId);
        return ResponseEntity.ok(ApiResponse.success("사용자 삭제"));
    }
}
