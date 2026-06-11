package com.yammer.service;

import com.yammer.dto.PaymentResponse;
import com.yammer.repository.PaymentRepository;
import com.yammer.security.AccessGuard;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final AccessGuard accessGuard;

    @Transactional(readOnly = true)
    public List<PaymentResponse> listByOrderPoint(UUID orderPointId) {
        accessGuard.requireAccessibleOrderPoint(orderPointId);
        return paymentRepository.findByOrderPointIdOrderByCreatedAtDesc(orderPointId).stream()
                .map(PaymentResponse::from)
                .toList();
    }
}
