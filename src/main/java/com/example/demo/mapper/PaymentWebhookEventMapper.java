package com.example.demo.mapper;

import com.example.demo.model.PaymentWebhookEvent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

@Mapper
public interface PaymentWebhookEventMapper {

    int insertIgnore(PaymentWebhookEvent event);

    int markProcessed(@Param("id") Long id,
                      @Param("processedAt") LocalDateTime processedAt);
}
