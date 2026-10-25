package com.twomemory.app.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provides an opt-in fallback for Windows environments where the JDK loopback
 * pipe used by Tomcat's NIO selector cannot be created.
 */
@Configuration(proxyBeanMethods = false)
public class TomcatServerConfiguration {

    private static final String NIO2_PROTOCOL = "org.apache.coyote.http11.Http11Nio2Protocol";

    @Bean
    @ConditionalOnProperty(name = "moon-letter.server.tomcat.nio2-connector", havingValue = "true")
    WebServerFactoryCustomizer<TomcatServletWebServerFactory> nio2ConnectorCustomizer() {
        return factory -> factory.setProtocol(NIO2_PROTOCOL);
    }
}
