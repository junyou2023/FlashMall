package com.example.demo.mapper;

import com.example.demo.model.PaymentOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PaymentOrderMapper {

    int insert(PaymentOrder paymentOrder);

    PaymentOrder findReusableUnpaidByOrderId(@Param("orderId") Long orderId,
                                             @Param("provider") String provider);

    int updateStripeSessionInfo(PaymentOrder paymentOrder);

    // webhook: payment_intent.* 事件使用 payment_intent_id 回查本地支付单
    PaymentOrder findByStripePaymentIntentId(@Param("stripePaymentIntentId") String stripePaymentIntentId);

    // webhook: checkout.session.* 事件使用 session_id 回查本地支付单
    PaymentOrder findByStripeCheckoutSessionId(@Param("stripeCheckoutSessionId") String stripeCheckoutSessionId);

    int markPaid(@Param("id") Long id);

    int markFailed(@Param("id") Long id);

    int markExpired(@Param("id") Long id);
}
