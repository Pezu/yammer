package com.yammer.service;

import com.yammer.config.NetopiaProperties;
import com.yammer.dto.netopia.NetopiaStartRequest;
import com.yammer.dto.netopia.NetopiaStartResponse;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * Thin client over Netopia's v2 card-payment API. Builds and sends the start-payment request and
 * returns the gateway's response (which carries the {@code paymentURL} to redirect the customer to).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NetopiaPaymentService {

    private static final int ROMANIA_ISO_NUMERIC = 642;

    private final NetopiaProperties props;

    /**
     * Starts a card payment for {@code reference} (our online-payment id, used as the gateway order
     * id). {@code redirectUrl} is where the gateway sends the browser back after payment.
     */
    public NetopiaStartResponse startPayment(
            String reference, double amount, String firstName, String lastName, String email,
            String phone, String redirectUrl) {
        var config = new NetopiaStartRequest.Config(
                props.getEmailTemplate(), props.getNotifyUrl(), redirectUrl, props.getLanguage());
        String billingEmail = email == null || email.isBlank() ? props.getGuestEmail() : email;
        String billingPhone = phone == null || phone.isBlank() ? props.getGuestPhone() : phone;
        var billing = new NetopiaStartRequest.Billing(
                billingEmail, billingPhone, firstName, lastName,
                "Bucharest", ROMANIA_ISO_NUMERIC, "Romania", "Bucharest", "010000", "N/A");
        var order = new NetopiaStartRequest.Order(
                reference, amount, props.getPosSignature(),
                ZonedDateTime.now().format(DateTimeFormatter.ISO_INSTANT), props.getCurrency(), billing);

        log.info("Starting Netopia payment: reference={}, amount={}", reference, amount);
        NetopiaStartResponse response = RestClient.create(props.getBaseUrl())
                .post()
                .uri("/payment/card/start")
                .header(HttpHeaders.AUTHORIZATION, props.getApiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .body(new NetopiaStartRequest(config, order))
                .retrieve()
                .body(NetopiaStartResponse.class);
        log.info("Netopia start response: status={}, message={}",
                response == null ? null : response.status(),
                response == null ? null : response.message());
        return response;
    }
}
