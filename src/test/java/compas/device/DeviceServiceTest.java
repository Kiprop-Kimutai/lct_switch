package compas.device;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import compas.api.UpstreamException;
import compas.restservice.DeviceRestClient;
import compas.webservice.WsClient;
import hellios.wsdl.DeviceToSave;
import hellios.wsdl.SavedDevice;
import java.io.IOException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;

@ExtendWith(MockitoExtension.class)
class DeviceServiceTest {
    @Mock private DeviceRepository repository;
    @Mock private DeviceRestClient restClient;
    @Mock private WsClient wsClient;
    private DeviceService service;

    @BeforeEach
    void setUp() {
        service = new DeviceService(repository, restClient, wsClient);
    }

    @Test
    void returnsDeviceUsingExactMacAddress() {
        Device device = new Device("00:25:7E:03", "serial", "retailer", "agency");
        when(repository.findFirstByMacAddress(device.getMacAddress())).thenReturn(Optional.of(device));

        assertThat(service.findByMac(device.getMacAddress())).isSameAs(device);
        verify(repository).findFirstByMacAddress("00:25:7E:03");
        verifyNoInteractions(restClient, wsClient);
    }

    @Test
    void reportsMissingDevice() {
        when(repository.findFirstByMacAddress("missing")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.findByMac("missing")).isInstanceOf(DeviceNotFoundException.class);
    }

    @Test
    void doesNotHideDatabaseFailuresAsMissingDevices() {
        DataAccessResourceFailureException failure = new DataAccessResourceFailureException("database unavailable");
        when(repository.findFirstByMacAddress("mac")).thenThrow(failure);
        assertThatThrownBy(() -> service.findByMac("mac")).isSameAs(failure);
    }

    @Test
    void returnsOriginalDeviceOnlyAfterSuccessfulRestSave() {
        Device device = new Device();
        assertThat(service.saveViaRest(device)).isSameAs(device);
        verify(restClient).saveDevice(device);
        verifyNoInteractions(repository, wsClient);
    }

    @Test
    void propagatesRestSaveFailures() {
        Device device = new Device();
        UpstreamException failure = new UpstreamException("failed", new IOException("connection refused"));
        doThrow(failure).when(restClient).saveDevice(device);
        assertThatThrownBy(() -> service.saveViaRest(device)).isSameAs(failure);
    }

    @Test
    void delegatesSoapSaveAndReturnsUpstreamPayload() {
        DeviceToSave request = new DeviceToSave();
        SavedDevice response = new SavedDevice();
        when(wsClient.saveDevice(request)).thenReturn(response);
        assertThat(service.saveViaSoap(request)).isSameAs(response);
        verifyNoInteractions(repository, restClient);
    }

    @Test
    void delegatesConnectionTest() {
        when(restClient.testConnection()).thenReturn("upstream");
        assertThat(service.testRestConnection()).isEqualTo("upstream");
    }
}
