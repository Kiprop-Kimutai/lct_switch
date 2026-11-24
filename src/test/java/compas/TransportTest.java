package compas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import compas.api.UpstreamException;
import compas.restservice.DeviceRestClient;
import compas.restservice.RestClientConfig;
import compas.restservice.RestProperties;
import compas.webservice.ClientConfig;
import compas.webservice.SoapProperties;
import compas.webservice.WsClient;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;
import org.springframework.web.client.RestClient;

class TransportTest {
    private final CountDownLatch release = new CountDownLatch(1);
    private final CountDownLatch received = new CountDownLatch(1);
    private HttpServer server;
    private ExecutorService executor;
    private URI endpoint;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        executor = Executors.newSingleThreadExecutor();
        server.setExecutor(executor);
        server.createContext("/", exchange -> {
            try (exchange) {
                exchange.getRequestBody().readAllBytes();
                received.countDown();
                try {
                    release.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Test fixture interrupted", exception);
                }
            }
        });
        server.start();
        endpoint = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        release.countDown();
        server.stop(0);
        executor.shutdownNow();
        assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void realRestTransportHonorsConfiguredReadTimeout() {
        RestClientConfig config = new RestClientConfig();
        DeviceRestClient client = new DeviceRestClient(config.restClient(RestClient.builder(),
                new RestProperties(endpoint, Duration.ofSeconds(1), Duration.ofMillis(100))), config.gson());
        assertTimeout(() -> client.testConnection());
    }

    @Test
    void realSoapTransportHonorsConfiguredReadTimeout() throws Exception {
        ClientConfig config = new ClientConfig();
        Jaxb2Marshaller marshaller = config.jaxb2Marshaller();
        marshaller.afterPropertiesSet();
        WsClient client = new WsClient(config.webServiceTemplate(marshaller,
                new SoapProperties(endpoint, Duration.ofSeconds(1), Duration.ofMillis(100))));
        assertTimeout(() -> client.sayHello("First", "Last"));
    }

    @Test
    void rejectsMalformedSoapFromTheRealTransport() throws Exception {
        server.removeContext("/");
        server.createContext("/", exchange -> {
            try (exchange) {
                exchange.getRequestBody().readAllBytes();
                byte[] body = "not a SOAP envelope".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "text/xml");
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
            }
        });
        ClientConfig config = new ClientConfig();
        Jaxb2Marshaller marshaller = config.jaxb2Marshaller();
        marshaller.afterPropertiesSet();
        WsClient client = new WsClient(config.webServiceTemplate(marshaller,
                new SoapProperties(endpoint, Duration.ofSeconds(1), Duration.ofSeconds(1))));
        assertThatThrownBy(() -> client.sayHello("First", "Last")).isInstanceOf(UpstreamException.class);
    }

    private void assertTimeout(Runnable request) {
        assertThatThrownBy(request::run).isInstanceOfSatisfying(UpstreamException.class,
                exception -> assertThat(exception.isTimeout()).isTrue());
        assertThat(received.getCount()).isZero();
    }
}
