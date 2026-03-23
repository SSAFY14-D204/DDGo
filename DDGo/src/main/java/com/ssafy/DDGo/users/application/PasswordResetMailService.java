package com.ssafy.DDGo.users.application;

import com.ssafy.DDGo.global.config.PasswordResetProperties;
import jakarta.mail.MessagingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetMailService {

    private final JavaMailSender javaMailSender;
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
        String plainText = """
                안녕하세요, DDGo입니다.

                아래 링크를 눌러 비밀번호를 재설정해 주세요.
                %s

                이 링크는 %d초 동안만 유효합니다.
                본인이 요청하지 않았다면 이 메일을 무시해 주세요.
                """.formatted(resetLink, passwordResetProperties.getTokenTtlSeconds());

        String htmlText = """
                <html>
                  <body style="font-family: Arial, sans-serif; line-height: 1.6; color: #222;">
                    <p>안녕하세요, <strong>DDGo</strong>입니다.</p>
                    <p>아래 버튼을 눌러 비밀번호를 재설정해 주세요.</p>
                    <p style="margin: 24px 0;">
                      <a href="%s"
                         style="display: inline-block; padding: 12px 20px; background: #1f6feb; color: #ffffff; text-decoration: none; border-radius: 8px; font-weight: 600;">
                        비밀번호 재설정
                      </a>
                    </p>
                    <p>버튼이 동작하지 않으면 아래 링크를 직접 열어 주세요.</p>
                    <p><a href="%s">%s</a></p>
                    <p>이 링크는 <strong>%d초</strong> 동안만 유효합니다.</p>
                    <p>본인이 요청하지 않았다면 이 메일을 무시해 주세요.</p>
                  </body>
                </html>
                """.formatted(resetLink, resetLink, resetLink, passwordResetProperties.getTokenTtlSeconds());

        try {
            var mimeMessage = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, "UTF-8");
            helper.setTo(toEmail);
            helper.setFrom(passwordResetProperties.getFrom());
            helper.setSubject("[DDGo] 비밀번호 재설정 안내");
            helper.setText(plainText, htmlText);

            javaMailSender.send(mimeMessage);
            return true;
        } catch (MessagingException e) {
            log.error("비밀번호 재설정 메일 메시지 생성에 실패했습니다. email={}", toEmail, e);
            return false;
        } catch (MailException e) {
            log.error("비밀번호 재설정 메일 전송에 실패했습니다. email={}", toEmail, e);
            return false;
        }
    }
}
