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
}
