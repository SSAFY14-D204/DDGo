package com.ssafy.DDGo.users.application;

import com.ssafy.DDGo.global.config.PasswordResetProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.MailException;
import org.springframework.mail.MailSender;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetMailService {

    private final MailSender mailSender;
    private final PasswordResetProperties passwordResetProperties;

    public boolean sendPasswordResetMail(String toEmail, String token) {
        if (!passwordResetProperties.isEnabled()) {
            log.info("비밀번호 재설정 메일 기능이 비활성화되어 있어 메일을 전송하지 않습니다. email={}", toEmail);
            return false;
        }

        if (!StringUtils.hasText(passwordResetProperties.getFrom())
                || !StringUtils.hasText(passwordResetProperties.getResetUrl())) {
            log.error("비밀번호 재설정 메일 설정이 올바르지 않습니다.");
            return false;
        }

        String resetLink = passwordResetProperties.getResetUrl() + "?token=" + token;
        String text = """
                안녕하세요, DDGo입니다.

                아래 링크를 눌러 비밀번호를 재설정해 주세요.
                %s

                이 링크는 %d초 동안만 유효합니다.
                본인이 요청하지 않았다면 이 메일을 무시해 주세요.
                """.formatted(resetLink, passwordResetProperties.getTokenTtlSeconds());

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(toEmail);
            message.setFrom(passwordResetProperties.getFrom());
            message.setSubject("[DDGo] 비밀번호 재설정 안내");
            message.setText(text);

            mailSender.send(message);
            return true;
        } catch (MailException e) {
            log.error("비밀번호 재설정 메일 전송에 실패했습니다. email={}", toEmail, e);
            return false;
        }
    }
}
