package com.example.demo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Stripe 配置
 *
 * 这一层只做配置聚合，不承载业务逻辑。
 * 目的是把密钥、回跳地址、会话过期时间集中管理，
 * 避免散落在 Service 里硬编码。
 */
@Component
@ConfigurationProperties(prefix = "app.payment.stripe")
public class StripeProperties {

    private String secretKey;
    private String successUrl;
    private String cancelUrl;
    private String currency = "usd";
    private long checkoutExpireMinutes = 30L;

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public String getSuccessUrl() {
        return successUrl;
    }

    public void setSuccessUrl(String successUrl) {
        this.successUrl = successUrl;
    }

    public String getCancelUrl() {
        return cancelUrl;
    }

    public void setCancelUrl(String cancelUrl) {
        this.cancelUrl = cancelUrl;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public long getCheckoutExpireMinutes() {
        return checkoutExpireMinutes;
    }

    public void setCheckoutExpireMinutes(long checkoutExpireMinutes) {
        this.checkoutExpireMinutes = checkoutExpireMinutes;
    }
}
