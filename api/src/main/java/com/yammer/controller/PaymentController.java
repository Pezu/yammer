package com.yammer.controller;

import com.yammer.dto.LinePaymentRequest;
import com.yammer.dto.LinePaymentResult;
import com.yammer.dto.PaymentRequest;
import com.yammer.dto.PaymentResponse;
import com.yammer.service.PaymentService;
import com.yammer.service.PaymentSplitService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final PaymentSplitService paymentSplitService;

    @GetMapping
    public List<PaymentResponse> list(@RequestParam UUID orderPointId) {
        return paymentService.listByOrderPoint(orderPointId);
    }

    /** Line-level settle (full or partial, with order-line splitting). */
    @PostMapping("/lines")
    public LinePaymentResult payLines(@Valid @RequestBody LinePaymentRequest request) {
        return paymentSplitService.pay(request);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentResponse create(@Valid @RequestBody PaymentRequest request) {
        return paymentService.create(request);
    }
}
