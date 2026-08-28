package com.bioplatform.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * 邮箱验证码服务
 * 发送验证码、存储到Redis、校验验证码
 */
@Service
public class EmailCodeService {

    private static final Logger log = LoggerFactory.getLogger(EmailCodeService.class);
    private static final String REDIS_PREFIX = "email:code:";
    private static final long CODE_EXPIRE_MINUTES = 5;

    private final JavaMailSender mailSender;
    private final StringRedisTemplate redisTemplate;

    @Value("${spring.mail.username}")
    private String mailFrom;

    public EmailCodeService(JavaMailSender mailSender, StringRedisTemplate redisTemplate) {
        this.mailSender = mailSender;
        this.redisTemplate = redisTemplate;
    }

    /**
     * 发送验证码邮件
     */
    public void sendCode(String email) {
        String code = String.valueOf((int) ((Math.random() * 9 + 1) * 100000));
        // 存入Redis，5分钟过期
        redisTemplate.opsForValue().set(REDIS_PREFIX + email, code, CODE_EXPIRE_MINUTES, TimeUnit.MINUTES);

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailFrom);
        message.setTo(email);
        message.setSubject("生信云平台 - 注册验证码");
        message.setText("您好！您的注册验证码是：" + code + "。有效期" + CODE_EXPIRE_MINUTES + "分钟，请勿泄露。");
        mailSender.send(message);
        log.info("验证码已发送至: {}", email);
    }

    /**
     * 校验验证码
     * @return true=验证通过
     */
    public boolean verifyCode(String email, String code) {
        String key = REDIS_PREFIX + email;
        String cached = redisTemplate.opsForValue().get(key);
        if (cached != null && cached.equals(code)) {
            redisTemplate.delete(key); // 验证成功后删除
            return true;
        }
        return false;
    }
}
