package compas.restservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import compas.api.UpstreamException;
import compas.device.Device;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

class DeviceRestClientTest {
    private MockRestServiceServer server;
    private DeviceRestClient client;

    @BeforeEach
    void setUp() {
        RestClientConfig config = new RestClientConfig();
        RestProperties properties = new RestProperties(URI.create("https://upstream.example"),
                Duration.ofSeconds(1), Duration.ofSeconds(2));
        RestClient.Builder builder = config.restClient(RestClient.builder(), properties).mutate();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new DeviceRestClient(builder.build(), config.gson());
    }

    @ParameterizedTest
    @EnumSource(value = HttpStatus.class, names = {"OK", "CREATED", "NO_CONTENT"})
    void postsUtf8JsonAndAcceptsSuccessfulStatuses(HttpStatus status) {
        Device device = new Device("mac", "serial", "caf\u00e9", null);
        String json = "{\"macAddress\":\"mac\",\"serial\":\"serial\",\"retailer\":\"caf\u00e9\"}";
        server.expect(requestTo("https://upstream.example/hmis/rest/save_devices"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Content-Type", "application/json"))
                .andExpect(content().bytes(json.getBytes(StandardCharsets.UTF_8)))
                .andRespond(withStatus(status));

        client.saveDevice(device);
        server.verify();
    }

    @Test
    void getsTheRootAndPreservesLegacyLineConcatenation() {
        server.expect(requestTo("https://upstream.example/"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("first\r\nsecond\n", MediaType.TEXT_PLAIN));
        assertThat(client.testConnection()).isEqualTo("firstsecond");
        server.verify();
    }

    @Test
    void acceptsAnEmptySuccessfulGetResponse() {
        server.expect(requestTo("https://upstream.example/")).andRespond(withStatus(HttpStatus.NO_CONTENT));
        assertThat(client.testConnection()).isEmpty();
        server.verify();
    }

    @ParameterizedTest
    @EnumSource(value = HttpStatus.class, names = {"FOUND", "BAD_REQUEST", "INTERNAL_SERVER_ERROR"})
    void reportsNonSuccessfulPostStatuses(HttpStatus status) {
        server.expect(requestTo("https://upstream.example/hmis/rest/save_devices")).andRespond(withStatus(status));
        assertThatThrownBy(() -> client.saveDevice(new Device()))
                .isInstanceOf(UpstreamException.class)
                .hasMessage("REST device save failed")
                .hasCauseInstanceOf(RestClientException.class);
        server.verify();
    }

    @Test
    void reportsGetFailuresInsteadOfAnEmptySuccess() {
        server.expect(requestTo("https://upstream.example/")).andRespond(withStatus(HttpStatus.BAD_GATEWAY));
        assertThatThrownBy(client::testConnection).isInstanceOf(UpstreamException.class);
        server.verify();
    }

    @Test
    void retainsConnectionFailureCause() {
        IOException failure = new IOException("connection refused");
        server.expect(requestTo("https://upstream.example/")).andRespond(withException(failure));
        assertThatThrownBy(client::testConnection).isInstanceOfSatisfying(UpstreamException.class, exception -> {
            assertThat(exception.isTimeout()).isFalse();
            assertThat(exception).hasRootCause(failure);
        });
        server.verify();
    }

    @Test
    void identifiesTransportTimeout() {
        server.expect(requestTo("https://upstream.example/"))
                .andRespond(withException(new SocketTimeoutException("read timed out")));
        assertThatThrownBy(client::testConnection).isInstanceOfSatisfying(UpstreamException.class,
                exception -> assertThat(exception.isTimeout()).isTrue());
        server.verify();
    }
}
