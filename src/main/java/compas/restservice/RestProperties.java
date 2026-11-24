package compas.restservice;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("lct.rest")
public record RestProperties(URI baseUrl, Duration connectTimeout, Duration readTimeout) {
    public RestProperties {
        if (baseUrl == null || baseUrl.getHost() == null
                || !("http".equals(baseUrl.getScheme()) || "https".equals(baseUrl.getScheme()))) {
            throw new IllegalArgumentException("lct.rest.base-url must be an absolute HTTP(S) URL");
        }
        if (connectTimeout == null || connectTimeout.toMillis() < 1
                || readTimeout == null || readTimeout.toMillis() < 1) {
            throw new IllegalArgumentException("REST timeouts must be at least one millisecond");
        }
    }
}
