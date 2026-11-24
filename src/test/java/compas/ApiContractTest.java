package compas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mongodb.client.MongoClient;
import compas.api.UpstreamException;
import compas.device.Device;
import compas.device.DeviceRepository;
import compas.restservice.DeviceRestClient;
import compas.restservice.RestProperties;
import compas.webservice.SoapProperties;
import compas.webservice.WsClient;
import hellios.wsdl.SavedDevice;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class ApiContractTest {
    private static final String JSON = """
            {"macAddress":"mac","serial":"serial","retailer":"retailer","agency":"agency"}
            """;

    @Autowired private MockMvc mvc;
    @Autowired private ApplicationContext context;
    @Autowired private RestProperties restProperties;
    @Autowired private SoapProperties soapProperties;
    @MockitoBean private MongoClient mongoClient;
    @MockitoSpyBean private DeviceRepository repository;
    @MockitoBean private DeviceRestClient restClient;
    @MockitoBean private WsClient wsClient;

    @Test
    void startsWithoutDemoQueriesOrUpstreamCalls() {
        assertThat(context.getBeansOfType(CommandLineRunner.class)).isEmpty();
        verifyNoInteractions(repository, restClient, wsClient);
        assertThat(restProperties.baseUrl().toString()).isEqualTo("http://localhost:3000");
        assertThat(soapProperties.uri().toString()).isEqualTo("http://localhost:9000/codenotfound/ws/lct");
        assertThat(context.getEnvironment().getProperty("spring.mongodb.uri"))
                .isEqualTo("mongodb://127.0.0.1:27017/lct");
    }

    @Test
    void retainsDistinctGreetingsAndBillingProbeWithoutAmbiguousMappings() throws Exception {
        mvc.perform(get("/lct/rest")).andExpect(status().isOk())
                .andExpect(content().string("Hello Garrixx. RestController >>>>>>>>."));
        mvc.perform(get("/lct/rest/")).andExpect(status().isOk()).andExpect(content().string("ok"));
        mvc.perform(get("/lct/ws/")).andExpect(status().isOk()).andExpect(content().string("Hello Garrixx"));
        mvc.perform(get("/lct/ws")).andExpect(status().isOk()).andExpect(content().string("Hello Garrixx"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/lct/rest/getdevice", "/lct/ws/getdevice",
            "/lct/rest/getdevice/", "/lct/ws/getdevice/"})
    void preservesLookupJsonIncludingLegacyStringProperty(String path) throws Exception {
        Device device = new Device("mac", "serial", "retailer", "agency");
        doReturn(Optional.of(device)).when(repository).findFirstByMacAddress("mac");
        mvc.perform(get(path).param("mac", "mac")).andExpect(status().isOk())
                .andExpect(jsonPath("$.macAddress").value("mac"))
                .andExpect(jsonPath("$.serial").value("serial"))
                .andExpect(jsonPath("$.retailer").value("retailer"))
                .andExpect(jsonPath("$.agency").value("agency"))
                .andExpect(jsonPath("$.string").value(device.getString()))
                .andExpect(jsonPath("$.id").doesNotExist());
        verify(repository).findFirstByMacAddress("mac");
    }

    @ParameterizedTest
    @ValueSource(strings = {"/lct/rest/getdevice", "/lct/ws/getdevice"})
    void reportsMissingDeviceAs404(String path) throws Exception {
        doReturn(Optional.empty()).when(repository).findFirstByMacAddress("missing");
        mvc.perform(get(path).param("mac", "missing")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Device not found"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/lct/rest/getdevice", "/lct/ws/getdevice"})
    void requiresMacParameter(String path) throws Exception {
        mvc.perform(get(path)).andExpect(status().isBadRequest());
        verifyNoInteractions(repository);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/lct/rest/saveDevices", "/lct/ws/saveDevices",
            "/lct/rest/saveDevices/", "/lct/ws/saveDevices/"})
    void batchEndpointsRemainEchoes(String path) throws Exception {
        mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content("[" + JSON + "]"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].macAddress").value("mac"))
                .andExpect(jsonPath("$.length()").value(1));
        verifyNoInteractions(repository, restClient, wsClient);
    }

    @Test
    void preservesRestSaveResponseAndInvokesOnlyRestTransport() throws Exception {
        mvc.perform(post("/lct/rest/savedevice").contentType(MediaType.APPLICATION_JSON).content(JSON))
                .andExpect(status().isOk()).andExpect(jsonPath("$.macAddress").value("mac"))
                .andExpect(jsonPath("$.serial").value("serial"))
                .andExpect(jsonPath("$.string").isString());
        verify(restClient).saveDevice(any(Device.class));
        verifyNoInteractions(repository, wsClient);
    }

    @Test
    void returnsSoapResponseRatherThanEchoingRequest() throws Exception {
        SavedDevice saved = new SavedDevice();
        saved.setMacAddress("saved-mac");
        saved.setSerial("saved-serial");
        saved.setRetailer("saved-retailer");
        saved.setAgency("saved-agency");
        when(wsClient.saveDevice(any())).thenReturn(saved);
        mvc.perform(post("/lct/ws/savedevice").contentType(MediaType.APPLICATION_JSON).content(JSON))
                .andExpect(status().isOk()).andExpect(jsonPath("$.macAddress").value("saved-mac"))
                .andExpect(jsonPath("$.serial").value("saved-serial"))
                .andExpect(jsonPath("$.retailer").value("saved-retailer"))
                .andExpect(jsonPath("$.agency").value("saved-agency"));
        verifyNoInteractions(repository, restClient);
    }

    @Test
    void relaysConnectionTestResponse() throws Exception {
        when(restClient.testConnection()).thenReturn("upstream");
        mvc.perform(get("/lct/rest/test")).andExpect(status().isOk()).andExpect(content().string("upstream"));
    }

    @Test
    void restSaveFailureReturns502WithoutInternalDetails() throws Exception {
        doThrow(new UpstreamException("private details", new IOException("connection refused")))
                .when(restClient).saveDevice(any());
        mvc.perform(post("/lct/rest/savedevice").contentType(MediaType.APPLICATION_JSON).content(JSON))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.detail").value("Upstream service request failed"))
                .andExpect(jsonPath("$.stackTrace").doesNotExist());
    }

    @Test
    void soapFailureReturns502() throws Exception {
        when(wsClient.saveDevice(any())).thenThrow(new UpstreamException("SOAP failed", new IOException("fault")));
        mvc.perform(post("/lct/ws/savedevice").contentType(MediaType.APPLICATION_JSON).content(JSON))
                .andExpect(status().isBadGateway());
    }

    @Test
    void soapTimeoutReturns504() throws Exception {
        when(wsClient.saveDevice(any())).thenThrow(
                new UpstreamException("SOAP failed", new SocketTimeoutException("timeout")));
        mvc.perform(post("/lct/ws/savedevice").contentType(MediaType.APPLICATION_JSON).content(JSON))
                .andExpect(status().isGatewayTimeout());
    }

    @Test
    void restTimeoutReturns504() throws Exception {
        when(restClient.testConnection()).thenThrow(
                new UpstreamException("REST failed", new IOException(new SocketTimeoutException("timeout"))));
        mvc.perform(get("/lct/rest/test")).andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.detail").value("Upstream service timed out"));
    }

    @Test
    void preservesPeopleDataRestResourceAndDoesNotExportInternalLookup() throws Exception {
        Device device = new Device("mac", "serial", "retailer", "agency");
        ReflectionTestUtils.setField(device, "id", "stored-id");
        doReturn(new PageImpl<>(List.of(device))).when(repository).findAll(any(Pageable.class));
        mvc.perform(get("/people").accept("application/hal+json")).andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.people[0].macAddress").value("mac"))
                .andExpect(jsonPath("$._links.self.href").exists())
                .andExpect(jsonPath("$._embedded.people[0]._links.self.href").value("http://localhost/people/stored-id"));
        mvc.perform(get("/people/search/findFirstByMacAddress").param("macAddress", "mac"))
                .andExpect(status().isNotFound());
    }

    @Test
    void preservesPeopleCrudSave() throws Exception {
        doAnswer(invocation -> {
            Device device = invocation.getArgument(0);
            ReflectionTestUtils.setField(device, "id", "saved-id");
            return device;
        }).when(repository).save(any(Device.class));
        mvc.perform(post("/people").contentType(MediaType.APPLICATION_JSON)
                        .accept("application/hal+json").content(JSON))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.macAddress").value("mac"))
                .andExpect(jsonPath("$._links.self.href").value("http://localhost/people/saved-id"));
        verify(repository).save(any(Device.class));
        verifyNoInteractions(restClient, wsClient);
    }

    @Test
    void invalidJsonDoesNotReachUpstream() throws Exception {
        mvc.perform(post("/lct/rest/savedevice").contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(repository, restClient, wsClient);
    }
}
