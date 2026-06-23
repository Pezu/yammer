package com.yammer.dto.netopia;

/** Acknowledgement Netopia expects back from the IPN endpoint ({@code errorCode 0} = handled). */
public record NetopiaIpnResponse(Integer errorCode, String message) {}
