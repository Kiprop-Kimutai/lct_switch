package compas.restservice;

import com.google.gson.Gson;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Configuration(proxyBeanMethods = false)
public class RestClientConfig {
    @Bean
    public RestClient restClient(RestClient.Builder builder, RestProperties properties) {
        SimpleClientHttpRequestFactory requests = new SimpleClientHttpRequestFactory();
        requests.setConnectTimeout(properties.connectTimeout());
        requests.setReadTimeout(properties.readTimeout());
        return builder.baseUrl(properties.baseUrl().toString()).requestFactory(requests)
                .defaultStatusHandler(status -> !status.is2xxSuccessful(), (request, response) -> {
                    throw new RestClientException("Upstream HTTP status " + response.getStatusCode().value());
                }).build();
    }

    @Bean
    public Gson gson() {
        return new Gson();
    }
}
