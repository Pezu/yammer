package com.yammer.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Company (merchant) identity printed on the proforma header, bound from {@code company.*}.
 * Defaults to RENDEZVOUS EVENTS; override per deployment via env/config.
 */
@Component
@ConfigurationProperties(prefix = "company")
@Getter
@Setter
public class CompanyProperties {

    private String name = "RENDEZVOUS EVENTS S.R.L.";
    private String cui = "RO41973877";
    private String regCom = "J2019003544080";
    private String address = "Str. Basarabia 12, Brasov";
    private String phone = "0748 869 869";
}
