package com.mirrorsoul.mirrorsoul_api.service;

import com.mirrorsoul.mirrorsoul_api.common.apiPayload.code.GeneralErrorCode;
import com.mirrorsoul.mirrorsoul_api.common.apiPayload.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MailService {

    private final JavaMailSender mailSender;

    public void sendVerificationCode(String toEmail, String code) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(toEmail);
            message.setSubject("[MirrorSoul] 이메일 인증번호");
            message.setText("인증번호는 [" + code + "] 입니다. 3분 안에 입력해주세요.");

            mailSender.send(message);
        } catch (Exception e) {
            throw new GeneralException(GeneralErrorCode.EMAIL_SEND_FAILED);
        }
    }

    public void sendPasswordResetCode(String toEmail, String code) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(toEmail);
            message.setSubject("[MirrorSoul] 비밀번호 재설정 인증번호");
            message.setText("비밀번호 재설정 인증번호는 [" + code + "] 입니다. 3분 안에 입력해주세요.");

            mailSender.send(message);
        } catch (Exception e) {
            throw new GeneralException(GeneralErrorCode.EMAIL_SEND_FAILED);
        }
    }

}
