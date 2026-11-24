package compas.webservice;

import compas.api.UpstreamException;
import hellios.wsdl.Device;
import hellios.wsdl.DeviceInfo;
import hellios.wsdl.DeviceToSave;
import hellios.wsdl.Greeting;
import hellios.wsdl.Person;
import hellios.wsdl.SavedDevice;
import org.springframework.oxm.UnmarshallingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.ws.InvalidXmlException;
import org.springframework.ws.client.WebServiceClientException;
import org.springframework.ws.client.core.WebServiceTemplate;
import org.springframework.ws.soap.SoapMessageCreationException;

@Component
public class WsClient {
    private final WebServiceTemplate template;

    public WsClient(WebServiceTemplate template) {
        this.template = template;
    }

    public Greeting sayHello(String firstName, String lastName) {
        Person person = new Person();
        person.setFirstName(firstName);
        person.setLastName(lastName);
        return send(person, Greeting.class);
    }

    public DeviceInfo queryDevice(String macAddress, String serial) {
        Device device = new Device();
        device.setMacAddress(macAddress);
        device.setSNO(serial);
        return send(device, DeviceInfo.class);
    }

    public SavedDevice saveDevice(DeviceToSave device) {
        return send(device, SavedDevice.class);
    }

    private <T> T send(Object request, Class<T> responseType) {
        Object response;
        try {
            response = template.marshalSendAndReceive(request);
        } catch (WebServiceClientException | UnmarshallingFailureException
                | InvalidXmlException | SoapMessageCreationException exception) {
            throw new UpstreamException("SOAP request failed", exception);
        }
        if (!responseType.isInstance(response)) {
            throw new UpstreamException("SOAP response did not match the operation",
                    new IllegalStateException("Expected " + responseType.getSimpleName()));
        }
        return responseType.cast(response);
    }
}
