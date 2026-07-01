package com.mpole.hana.digitaltwin.iam.user.service;

import com.mpole.hana.digitaltwin.iam.role.repository.UserRoleRepository;
import com.mpole.hana.digitaltwin.iam.user.dto.UserCreateRequest;
import com.mpole.hana.digitaltwin.iam.user.dto.UserResponse;
import com.mpole.hana.digitaltwin.iam.user.dto.UserUpdateRequest;
import com.mpole.hana.digitaltwin.iam.user.entity.User;
import com.mpole.hana.digitaltwin.iam.user.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public Page<UserResponse> getUsers(Boolean active, String loginId, String username, Long roleId, Pageable pageable) {
        Page<User> page = userRepository.findUsersByCondition(active, loginId, username, roleId, pageable);

        List<Long> userIds = page.getContent().stream().map(User::getUserId).toList();
        Map<Long, List<UserResponse.RoleBrief>> rolesByUser = userIds.isEmpty()
                ? Map.of()
                : userRoleRepository.findWithRoleByUserIds(userIds).stream()
                .collect(Collectors.groupingBy(
                        ur -> ur.getUser().getUserId(),
                        Collectors.mapping(
                                ur -> new UserResponse.RoleBrief(ur.getRole().getRoleId(), ur.getRole().getRoleName()),
                                Collectors.toList())));

        return page.map(u -> UserResponse.from(u, rolesByUser.getOrDefault(u.getUserId(), List.of())));
    }

    @Transactional
    public void createUser(UserCreateRequest request) {
        if (userRepository.existsByLoginId(request.loginId())) {
            throw new IllegalStateException("이미 존재하는 ID입니다.");
        }
        User user = User.create(
                request.loginId(),
                request.username(),
                request.email(),
                passwordEncoder.encode(request.password()),
                request.active()
        );
        userRepository.save(user);
    }

    @Transactional
    public void updateUser(Long userId, UserUpdateRequest request) {
        User user = getUser(userId);
        String encodedPassword = null;
        if (StringUtils.hasText(request.password())) {
            encodedPassword = passwordEncoder.encode(request.password());
        }
        user.update(
                request.username(),
                request.email(),
                request.active(),
                request.accountNonLocked(),
                request.failedLoginAttempts(),
                encodedPassword
        );
    }

    @Transactional
    public void deleteUser(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new EntityNotFoundException("사용자가 없습니다.");
        }
        userRepository.deleteById(userId);
    }
    private User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("사용자가 없습니다."));
    }
}
