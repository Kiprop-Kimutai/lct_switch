package compas.restservice;

import com.google.gson.Gson;
import compas.api.UpstreamException;
import compas.device.Device;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class DeviceRestClient {
    private final RestClient client;
    private final Gson gson;

    public DeviceRestClient(RestClient client, Gson gson) {
        this.client = client;
        this.gson = gson;
    }

    public void saveDevice(Device device) {
        byte[] body = gson.toJson(device).getBytes(StandardCharsets.UTF_8);
        try {
            client.post().uri("/hmis/rest/save_devices")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body).retrieve().toBodilessEntity();
        } catch (RestClientException exception) {
            throw new UpstreamException("REST device save failed", exception);
        }
    }

    public String testConnection() {
        try {
            String response = client.get().uri("/").retrieve().body(String.class);
            // Preserve the legacy test endpoint's line-concatenated response.
            return response == null ? "" : response.lines().collect(Collectors.joining());
        } catch (RestClientException exception) {
            throw new UpstreamException("REST connection test failed", exception);
        }
    }
}
