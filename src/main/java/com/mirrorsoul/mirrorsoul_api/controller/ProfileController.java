package com.mirrorsoul.mirrorsoul_api.controller;

import com.mirrorsoul.mirrorsoul_api.common.apiPayload.ApiResponse;
import com.mirrorsoul.mirrorsoul_api.common.security.CustomUserDetails;
import com.mirrorsoul.mirrorsoul_api.dto.profile.ProfileReqDTO;
import com.mirrorsoul.mirrorsoul_api.dto.profile.ProfileResDTO;
import com.mirrorsoul.mirrorsoul_api.service.ProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Profile", description = "Profile 관련 API")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/my-page")
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService profileService;

    @Operation(summary = "마이페이지 진입 api", description = "마이페이지에 필요한 이름, 이메일 정보를 조회합니다.")
    @GetMapping
    public ApiResponse<ProfileResDTO.myProfileDTO> getMyProfile(
            @AuthenticationPrincipal CustomUserDetails currentUser
    ) {
        return ApiResponse.onSuccess(
                "마이페이지 조회에 성공했습니다.",
                profileService.getMyProfile(currentUser.getUuid())
        );
    }

    @Operation(summary = "나의 시간 조회 api", description = "나의 현재 남은 시간을 조회합니다.")
    @GetMapping("/buy-time")
    public ApiResponse<ProfileResDTO.timeStatusDTO> myTime(
            @AuthenticationPrincipal CustomUserDetails currentUser
    ) {
        return ApiResponse.onSuccess(
                "잔여 대화 시간 조회에 성공했습니다.",
                profileService.getMyTime(currentUser.getUuid())
        );
    }

    @Operation(summary = "대화 시간 채우기 api", description = "시간은 초단위로 계산하므로, 30분 추가->1800, 2시간 추가->7200, 10시간 추가->36000 으로 입력합니다.")
    @PostMapping("/buy-time")
    public ApiResponse<ProfileResDTO.timeStatusDTO> buyTime(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @Valid @RequestBody ProfileReqDTO.buyTimeReqDTO request
    ) {
        return ApiResponse.onSuccess(
                "대화 시간 충전에 성공했습니다.",
                profileService.buyTime(currentUser.getUuid(), request)
        );
    }

    @Operation(summary = "음성 및 오디오 설정 조회 api", description = "상대방 목소리 크기와 말하기 속도를 조회합니다. 말하기 속도의 ENUM값은 SLOW, NORMAL, FAST")
    @GetMapping("/audio-settings")
    public ApiResponse<ProfileResDTO.audioSettingsDTO> getAudioSettings(
            @AuthenticationPrincipal CustomUserDetails currentUser
    ) {
        return ApiResponse.onSuccess(
                "음성 및 오디오 설정 조회에 성공했습니다.",
                profileService.getAudioSettings(currentUser.getUuid())
        );
    }

    @Operation(summary = "음성 및 오디오 설정 수정 api", description = "상대방 목소리 크기와 말하기 속도를 수정합니다. 말하기 속도의 ENUM값은 SLOW, NORMAL, FAST")
    @PatchMapping("/audio-settings")
    public ApiResponse<ProfileResDTO.audioSettingsDTO> updateAudioSettings(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @Valid @RequestBody ProfileReqDTO.audioSettingsReqDTO request
    ) {
        return ApiResponse.onSuccess(
                "음성 및 오디오 설정 수정에 성공했습니다.",
                profileService.updateAudioSettings(currentUser.getUuid(), request)
        );
    }

    @Operation(summary = "알림 설정 여부 조회 api")
    @GetMapping("/alarm")
    public ApiResponse<ProfileResDTO.alarmSettingDTO> getAlarmSetting (
            @AuthenticationPrincipal CustomUserDetails currentUser
    ) {
        return ApiResponse.onSuccess(
                "알림 설정 조회에 성공했습니다.",
                profileService.getAlarmSetting(currentUser.getUuid())
        );
    }

    @Operation(summary = "알림 설정 수정 api")
    @PatchMapping("/alarm")
    public ApiResponse<ProfileResDTO.alarmSettingDTO> modifyAlarmSetting (
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @Valid @RequestBody ProfileReqDTO.alarmSettingReqDTO request
    ) {
        return ApiResponse.onSuccess(
                "알림 설정 수정에 성공했습니다.",
                profileService.modifyAlarmSetting(currentUser.getUuid(), request)
        );
    }

    // TODO 나중에 소셜로그인 확장시 소셜계정 연동 현황 조회 추가
    @Operation(summary = "계정관리 api", description = "자신의 닉네임, 소셜계정 연동 여부를 조회합니다. (현재는 닉네임만)")
    @GetMapping("/account")
    public ApiResponse<ProfileResDTO.accountInfoDTO> accountInfo (
            @AuthenticationPrincipal CustomUserDetails currentUser
    ) {
        return ApiResponse.onSuccess(
                "계정 정보 조회에 성공했습니다.",
                profileService.getAccountInfo(currentUser.getUuid())
        );
    }

    @Operation(summary = "닉네임 변경 api", description = "자신의 닉네임을 수정합니다.")
    @PostMapping("/account")
    public ApiResponse<Void> modifyNickname (
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @Valid @RequestBody ProfileReqDTO.modifyNicknameReqDTO request
    ) {
        profileService.modifyNickname(currentUser.getUuid(), request);
        return ApiResponse.onSuccess("닉네임 수정에 성공했습니다.");
    }

    @Operation(summary = "회원 탈퇴 api", description = "계정을 비활성화(Soft Delete)합니다. 30일 뒤에 계정은 영구 삭제됩니다.")
    @DeleteMapping
    public ApiResponse<Void> inActiveAccount (
            @AuthenticationPrincipal CustomUserDetails currentUser
    ) {
        profileService.inactiveAccount(currentUser.getUuid());
        return ApiResponse.onSuccess("회원 탈퇴가 완료되었습니다.");
    }
}
