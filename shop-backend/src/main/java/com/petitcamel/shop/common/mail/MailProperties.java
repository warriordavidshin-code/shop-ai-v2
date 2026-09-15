package com.petitcamel.shop.common.mail;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.mail")
public class MailProperties {

    /**
     * When false, password-reset emails are written to application logs (local/dev).
     * Enable with real SMTP credentials for production.
     */
    private boolean enabled = false;
    private String from = "noreply@btc-camel.com";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }
}
