package com.petitcamel.shop.common.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    private final MailProperties mailProperties;
    private final JavaMailSender javaMailSender;

    public MailService(MailProperties mailProperties, ObjectProvider<JavaMailSender> javaMailSender) {
        this.mailProperties = mailProperties;
        this.javaMailSender = javaMailSender.getIfAvailable();
    }

    public void sendPlainText(String to, String subject, String body) {
        if (!mailProperties.isEnabled() || javaMailSender == null) {
            log.info(
                    "Mail disabled — would send to={} subject={} bodyLength={}",
                    maskEmail(to),
                    subject,
                    body == null ? 0 : body.length());
            log.info("Mail disabled body preview (dev only): {}", body);
            return;
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailProperties.getFrom());
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        javaMailSender.send(message);
        log.info("Mail sent to={} subject={}", maskEmail(to), subject);
    }

    private static String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return "***";
        }
        int at = email.indexOf('@');
        return "***@" + email.substring(at + 1);
    }
}
