package com.mpole.hana.digitaltwin.iam.user.repository;

import com.mpole.hana.digitaltwin.iam.user.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken,Long> {

    // 토큰으로 사용자 조회
    Optional<RefreshToken> findByToken(String token);
    
    // 로그인 ID + 디바이스 ID로 조회 
    Optional<RefreshToken> findByLoginIdAndDeviceId(String loginId, String deviceId);
    
    // 로그인 ID로 모든 토큰 조회 (다중 기기)
    List<RefreshToken> findByLoginId(String loginId);
    
    // 로그인 ID + 디바이스 아이디로 토큰 삭제 
    int deleteByLoginIdAndDeviceId(String loginId, String deviceId);
    
    // 로그인 ID로 모든 토큰 삭제 (모든 기기에서 로그아웃)
    void deleteByLoginId(String loginId);
    
    // 만료된 토큰 삭제 
    void deleteByExpiryDateBefore(OffsetDateTime now);
    
    // 사용자의 활성 토큰 개수
    long countByLoginIdAndExpiryDateAfter(String loginId, OffsetDateTime now);
}
