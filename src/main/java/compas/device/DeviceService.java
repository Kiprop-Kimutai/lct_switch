package compas.device;

import compas.restservice.DeviceRestClient;
import compas.webservice.WsClient;
import hellios.wsdl.DeviceToSave;
import hellios.wsdl.SavedDevice;
import org.springframework.stereotype.Service;

@Service
public class DeviceService {
    private final DeviceRepository repository;
    private final DeviceRestClient restClient;
    private final WsClient wsClient;

    public DeviceService(DeviceRepository repository, DeviceRestClient restClient, WsClient wsClient) {
        this.repository = repository;
        this.restClient = restClient;
        this.wsClient = wsClient;
    }

    public Device findByMac(String macAddress) {
        return repository.findFirstByMacAddress(macAddress).orElseThrow(DeviceNotFoundException::new);
    }

    public Device saveViaRest(Device device) {
        restClient.saveDevice(device);
        return device;
    }

    public SavedDevice saveViaSoap(DeviceToSave device) {
        return wsClient.saveDevice(device);
    }

    public String testRestConnection() {
        return restClient.testConnection();
    }
}
