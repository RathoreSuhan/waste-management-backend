package com.cleanbharat.wastemanagement.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.Properties;

/**
 * ============================================================
 *  Outgoing mail
 * ============================================================
 *
 *  Builds the sender used to deliver sign-up verification codes.
 *
 *  WHY THIS IS HAND-BUILT RATHER THAN AUTO-CONFIGURED
 *  --------------------------------------------------
 *  Spring Boot will create a JavaMailSender from spring.mail.* on its own, but
 *  it does not turn on STARTTLS, which every mainstream SMTP provider requires
 *  on port 587. Supplying that through auto-configuration means
 *  spring.mail.properties.mail.smtp.* entries in a properties file - and this
 *  project's application.properties is off limits. Reading plain MAIL_*
 *  environment variables with @Value and setting the two flags in code keeps the
 *  whole configuration surface in environment variables, which is also how the
 *  service is deployed.
 *
 *  Same shape as CloudinaryConfig and GoogleAuthConfig: @Value fields, one @Bean.
 *
 *  UNCONFIGURED IS A VALID STATE
 *  -----------------------------
 *  The bean is always created, even with no credentials, so the application
 *  starts cleanly on a machine that has no SMTP account - every other feature
 *  works without it. EmailVerificationService checks isConfigured() before it
 *  tries to send and refuses the sign-up outright if nothing is set, rather than
 *  letting an account through unverified.
 * ============================================================
 */
@Configuration
public class MailConfig {

    // Defaults to Gmail's SMTP, which is what a free deployment usually uses
    @Value("${mail.host:smtp.gmail.com}")
    private String host;

    // 587 is the submission port, which is STARTTLS rather than implicit TLS
    @Value("${mail.port:587}")
    private int port;

    @Value("${mail.username:}")
    private String username;

    // An app password, never the account password. Read, never logged.
    @Value("${mail.password:}")
    private String password;

    @Bean
    public JavaMailSender javaMailSender() {

        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();

        // Trimmed: Render/Vercel dashboards often add a trailing space or newline on paste
        String cleanHost = host != null ? host.trim() : "smtp.gmail.com";
        String cleanUsername = username != null ? username.trim() : "";
        // Gmail shows app passwords as "abcd efgh ijkl mnop" - spaces must be stripped
        String cleanPassword = password != null ? password.replaceAll("\\s+", "") : "";

        mailSender.setHost(cleanHost);
        mailSender.setPort(port);
        mailSender.setUsername(cleanUsername);
        mailSender.setPassword(cleanPassword);

        Properties properties = mailSender.getJavaMailProperties();

        properties.put("mail.transport.protocol", "smtp");

        // Without these two the connection stays in the clear and the provider
        // rejects the credentials - the usual cause of a silent send failure
        properties.put("mail.smtp.auth", "true");
        properties.put("mail.smtp.starttls.enable", "true");
        // Required (not just enabled) so a downgraded plain connection is refused outright
        properties.put("mail.smtp.starttls.required", "true");
        // Trust the SMTP host explicitly - needed inside minimal Docker images
        properties.put("mail.smtp.ssl.trust", cleanHost);
        properties.put("mail.smtp.ssl.protocols", "TLSv1.2");

        /*
          Bounded waits. The default is to block indefinitely, so one
          unreachable SMTP host would hold a request thread until the client
          gave up - and sign-up is a path a person is sitting in front of.
          Note: on Render free tier outbound 25/465/587 is blocked entirely
          (see EmailVerificationService Brevo fallback over HTTPS/443).
        */
        properties.put("mail.smtp.connectiontimeout", "10000");
        properties.put("mail.smtp.timeout", "10000");
        properties.put("mail.smtps.timeout", "10000");
        properties.put("mail.smtp.writetimeout", "10000");

        return mailSender;
    }
}
