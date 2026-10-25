package com.twomemory.app.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;

import org.junit.jupiter.api.Test;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;

class TomcatServerConfigurationTest {

    @Test
    void fallbackConnectorCustomizerUsesTomcatNio2Protocol() throws Exception {
        var factory = new TomcatServletWebServerFactory();

        new TomcatServerConfiguration().nio2ConnectorCustomizer().customize(factory);

        Field protocol = TomcatServletWebServerFactory.class.getDeclaredField("protocol");
        protocol.setAccessible(true);
        assertThat(protocol.get(factory)).isEqualTo("org.apache.coyote.http11.Http11Nio2Protocol");
    }
}
