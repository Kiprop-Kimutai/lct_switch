package compas.webservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.ws.test.client.RequestMatchers.connectionTo;
import static org.springframework.ws.test.client.RequestMatchers.payload;
import static org.springframework.ws.test.client.ResponseCreators.withException;
import static org.springframework.ws.test.client.ResponseCreators.withPayload;
import static org.springframework.ws.test.client.ResponseCreators.withServerOrReceiverFault;

import compas.api.UpstreamException;
import hellios.wsdl.DeviceInfo;
import hellios.wsdl.DeviceToSave;
import hellios.wsdl.SavedDevice;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.time.Duration;
import java.util.Locale;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;
import org.springframework.oxm.UnmarshallingFailureException;
import org.springframework.ws.client.core.WebServiceTemplate;
import org.springframework.ws.soap.client.SoapFaultClientException;
import org.springframework.ws.test.client.MockWebServiceServer;
import org.springframework.xml.transform.StringSource;

class WsClientTest {
    private static final String ENDPOINT = "https://upstream.example/lct";
    private MockWebServiceServer server;
    private WsClient client;

    @BeforeEach
    void setUp() throws Exception {
        ClientConfig config = new ClientConfig();
        Jaxb2Marshaller marshaller = config.jaxb2Marshaller();
        marshaller.afterPropertiesSet();
        WebServiceTemplate template = config.webServiceTemplate(marshaller,
                new SoapProperties(URI.create(ENDPOINT), Duration.ofSeconds(1), Duration.ofSeconds(2)));
        server = MockWebServiceServer.createServer(template);
        client = new WsClient(template);
    }

    @Test
    void sendsGreetingWithExistingNamespace() {
        server.expect(connectionTo(ENDPOINT)).andExpect(payload(new StringSource("""
                <person xmlns="http://codenotfound.com/types/lct">
                  <firstName>First</firstName><lastName>Last</lastName>
                </person>
                """))).andRespond(withPayload(new StringSource("""
                <greeting xmlns="http://codenotfound.com/types/lct"><greeting>Hello</greeting></greeting>
                """)));
        assertThat(client.sayHello("First", "Last").getGreeting()).isEqualTo("Hello");
        server.verify();
    }

    @Test
    void preservesDifferentSerialElementSpellingsInQueryAndResponse() {
        server.expect(payload(new StringSource("""
                <device xmlns="http://codenotfound.com/types/lct">
                  <macAddress>mac</macAddress><sNO>request-serial</sNO>
                </device>
                """))).andRespond(withPayload(new StringSource("""
                <deviceInfo xmlns="http://codenotfound.com/types/lct">
                  <macAddress>mac</macAddress><sNo>response-serial</sNo>
                  <retailer>retailer</retailer><agency>agency</agency>
                </deviceInfo>
                """)));
        DeviceInfo device = client.queryDevice("mac", "request-serial");
        assertThat(device.getMacAddress()).isEqualTo("mac");
        assertThat(device.getSNo()).isEqualTo("response-serial");
        assertThat(device.getRetailer()).isEqualTo("retailer");
        assertThat(device.getAgency()).isEqualTo("agency");
        server.verify();
    }

    @Test
    void savesDeviceUsingUnchangedWsdlPayload() {
        DeviceToSave request = new DeviceToSave();
        request.setMacAddress("mac");
        request.setSerial("serial");
        request.setRetailer("retailer");
        request.setAgency("agency");
        server.expect(payload(new StringSource("""
                <deviceToSave xmlns="http://codenotfound.com/types/lct">
                  <macAddress>mac</macAddress><serial>serial</serial>
                  <retailer>retailer</retailer><agency>agency</agency>
                </deviceToSave>
                """))).andRespond(withPayload(new StringSource("""
                <savedDevice xmlns="http://codenotfound.com/types/lct">
                  <macAddress>saved-mac</macAddress><serial>saved-serial</serial>
                  <retailer>saved-retailer</retailer><agency>saved-agency</agency>
                </savedDevice>
                """)));
        SavedDevice saved = client.saveDevice(request);
        assertThat(saved.getMacAddress()).isEqualTo("saved-mac");
        assertThat(saved.getSerial()).isEqualTo("saved-serial");
        assertThat(saved.getRetailer()).isEqualTo("saved-retailer");
        assertThat(saved.getAgency()).isEqualTo("saved-agency");
        server.verify();
    }

    @Test
    void retainsSoapFaultAsAnUpstreamFailure() {
        server.expect(connectionTo(ENDPOINT)).andRespond(withServerOrReceiverFault("upstream fault", Locale.ENGLISH));
        assertThatThrownBy(() -> client.sayHello("First", "Last"))
                .isInstanceOf(UpstreamException.class)
                .hasCauseInstanceOf(SoapFaultClientException.class);
        server.verify();
    }

    @Test
    void rejectsAResponseForTheWrongOperation() {
        server.expect(connectionTo(ENDPOINT)).andRespond(withPayload(new StringSource("""
                <greeting xmlns="http://codenotfound.com/types/lct"><greeting>Hello</greeting></greeting>
                """)));
        assertThatThrownBy(() -> client.saveDevice(new DeviceToSave())).isInstanceOf(UpstreamException.class);
        server.verify();
    }

    @Test
    void rejectsAnUnknownResponseElement() {
        server.expect(connectionTo(ENDPOINT)).andRespond(withPayload(new StringSource("""
                <unknown xmlns="http://codenotfound.com/types/lct"/>
                """)));
        assertThatThrownBy(() -> client.sayHello("First", "Last")).isInstanceOf(UpstreamException.class)
                .hasCauseInstanceOf(UnmarshallingFailureException.class);
        server.verify();
    }

    @Test
    void preservesSoapTransportTimeoutCause() {
        server.expect(connectionTo(ENDPOINT)).andRespond(withException(new SocketTimeoutException("timeout")));
        assertThatThrownBy(() -> client.sayHello("First", "Last"))
                .isInstanceOfSatisfying(UpstreamException.class,
                        exception -> assertThat(exception.isTimeout()).isTrue());
        server.verify();
    }
}
