package com.mpole.hana.digitaltwin.iam.role.service;

import com.mpole.hana.digitaltwin.iam.role.dto.RoleRequest;
import com.mpole.hana.digitaltwin.iam.role.dto.RoleResponse;
import com.mpole.hana.digitaltwin.iam.role.dto.RoleUserResponse;
import com.mpole.hana.digitaltwin.iam.role.entity.Role;
import com.mpole.hana.digitaltwin.iam.role.entity.UserRole;
import com.mpole.hana.digitaltwin.iam.role.repository.RoleRepository;
import com.mpole.hana.digitaltwin.iam.role.repository.UserRoleRepository;
import com.mpole.hana.digitaltwin.iam.user.entity.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RoleService {
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final EntityManager entityManager;

    public List<RoleResponse> getRoles() {
        return roleRepository.findRoleResponses();
    }

    public List<Role> getRoleInfoByRoleName(Collection<String> roleNames) {
        return roleRepository.findAllByRoleNameIn(roleNames);
    }

    public List<RoleUserResponse> getUsersInRole(Long roleId) {
        ensureRoleExists(roleId);
        return roleRepository.findUsersInRole(roleId);
    }

    public List<RoleUserResponse> getUsersExceptRole(Long roleId, String search) {
        ensureRoleExists(roleId);
        return roleRepository.findUsersExceptRole(roleId, search);
    }

    @Transactional
    public RoleResponse createRole(RoleRequest request) {
        if (roleRepository.existsByRoleName(request.roleName())) {
            throw new IllegalStateException("이미 존재하는 권한명입니다.");
        }
        Role role = roleRepository.save(Role.create(request.roleName(), request.description()));
        return RoleResponse.of(role, 0);
    }
    @Transactional
    public void updateRole(Long roleId, RoleRequest request) {
        Role role = getRole(roleId);
        role.update(request.roleName(), request.description());
    }
    @Transactional
    public void deleteRole(Long roleId) {
        ensureRoleExists(roleId);
        roleRepository.deleteById(roleId);
    }
    @Transactional
    public void addUserToRole(Long roleId, Long userId) {
        if (userRoleRepository.existsByUser_UserIdAndRole_RoleId(userId, roleId)) {
            throw new IllegalStateException("이미 권한에 등록된 사용자입니다.");
        }
        User user = entityManager.getReference(User.class, userId);
        Role role = entityManager.getReference(Role.class, roleId);
        userRoleRepository.save(UserRole.of(user, role));
    }

    @Transactional
    public void removeUserFromRole(Long roleId, Long userId) {
        userRoleRepository.deleteByUser_UserIdAndRole_RoleId(userId, roleId);
    }
    private Role getRole(Long roleId) {
        return roleRepository.findById(roleId)
                .orElseThrow(() -> new EntityNotFoundException("권한이 존재하지 않습니다."));
    }
    private void ensureRoleExists(Long roleId) {
        if (!roleRepository.existsById(roleId)) {
            throw new EntityNotFoundException("권한이 존재하지 않습니다.");
        }
    }
}
