package compas.webservice;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("lct.soap")
public record SoapProperties(URI uri, Duration connectTimeout, Duration readTimeout) {
    public SoapProperties {
        if (uri == null || uri.getHost() == null
                || !("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))) {
            throw new IllegalArgumentException("lct.soap.uri must be an absolute HTTP(S) URL");
        }
        if (connectTimeout == null || connectTimeout.toMillis() < 1
                || readTimeout == null || readTimeout.toMillis() < 1) {
            throw new IllegalArgumentException("SOAP timeouts must be at least one millisecond");
        }
    }
}
