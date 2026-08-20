package com.mirrorsoul.mirrorsoul_api.domain.enums;

public enum UserStatus {
    ONBOARD_A, // 회원가입 완료
    ONBOARD_B, // 기본 프로필 완료
    ONBOARD_C, // 성격 유형 완료
    ONBOARD_D, // 음성 인터뷰 완료
    ACTIVE, // 얼굴 스캔 완료 (온보딩 전체 완료)
    INACTIVE, // 탈퇴 후 30일 이내, 복구 가능
    DELETED, // 30일 경과 후 개인정보 익명화 완료

}
