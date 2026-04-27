package com.saga.sattolux.core.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;

@Component
@RequiredArgsConstructor
public class OtpUtil {

    private final JavaMailSender mailSender;
    private final SecureRandom secureRandom = new SecureRandom();

    public String generateCode() {
        int code = 100000 + secureRandom.nextInt(900000);
        return String.valueOf(code);
    }

    public void sendOtp(String toEmail, String code) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(toEmail);
        message.setSubject("[SattoLux] 인증 코드");
        message.setText("인증 코드: " + code + "\n\n5분 내에 입력하세요.");
        mailSender.send(message);
    }
}
