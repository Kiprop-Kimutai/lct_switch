package compas;

import static org.assertj.core.api.Assertions.assertThat;

import compas.restservice.RestProperties;
import compas.webservice.SoapProperties;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class ConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(PropertiesConfig.class)
            .withPropertyValues(
                    "lct.rest.base-url=https://rest.example:8443",
                    "lct.rest.connect-timeout=250ms",
                    "lct.rest.read-timeout=2s",
                    "lct.soap.uri=https://soap.example/service",
                    "lct.soap.connect-timeout=500ms",
                    "lct.soap.read-timeout=3s");

    @Test
    void bindsEndpointAndTimeoutOverrides() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            RestProperties rest = context.getBean(RestProperties.class);
            SoapProperties soap = context.getBean(SoapProperties.class);
            assertThat(rest.baseUrl().toString()).isEqualTo("https://rest.example:8443");
            assertThat(rest.connectTimeout()).isEqualTo(Duration.ofMillis(250));
            assertThat(rest.readTimeout()).isEqualTo(Duration.ofSeconds(2));
            assertThat(soap.uri().toString()).isEqualTo("https://soap.example/service");
            assertThat(soap.connectTimeout()).isEqualTo(Duration.ofMillis(500));
            assertThat(soap.readTimeout()).isEqualTo(Duration.ofSeconds(3));
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "lct.rest.connect-timeout=0s", "lct.rest.read-timeout=-1s",
            "lct.soap.connect-timeout=0s", "lct.soap.read-timeout=-1s",
            "lct.rest.base-url=ftp://rest.example", "lct.soap.uri=/relative",
            "lct.rest.base-url=http:/missing-host", "lct.soap.uri=ftp://soap.example"
    })
    void rejectsInvalidConnectionSettingsInsteadOfSilentlyDefaulting(String property) {
        runner.withPropertyValues(property).run(context -> assertThat(context).hasFailed());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties({RestProperties.class, SoapProperties.class})
    static class PropertiesConfig {}
}
