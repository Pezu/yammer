package com.yammer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

// Auth is JWT-based (JwtAuthFilter + /auth/login); we never use Spring's default in-memory
// UserDetailsService, so exclude its autoconfiguration to stop the bogus "generated security
// password" log line at startup.
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class YammerApplication {

    public static void main(String[] args) {
        SpringApplication.run(YammerApplication.class, args);
    }
}
