package compas.webservice;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;
import org.springframework.ws.client.core.WebServiceTemplate;
import org.springframework.ws.transport.http.HttpUrlConnectionMessageSender;

@Configuration(proxyBeanMethods = false)
public class ClientConfig {
    @Bean
    public WebServiceTemplate webServiceTemplate(Jaxb2Marshaller marshaller, SoapProperties properties) {
        HttpUrlConnectionMessageSender sender = new HttpUrlConnectionMessageSender();
        sender.setConnectionTimeout(properties.connectTimeout());
        sender.setReadTimeout(properties.readTimeout());
        WebServiceTemplate template = new WebServiceTemplate();
        template.setMarshaller(marshaller);
        template.setUnmarshaller(marshaller);
        template.setDefaultUri(properties.uri().toString());
        template.setMessageSender(sender);
        return template;
    }

    @Bean
    public Jaxb2Marshaller jaxb2Marshaller() {
        Jaxb2Marshaller marshaller = new Jaxb2Marshaller();
        marshaller.setContextPath("hellios.wsdl");
        return marshaller;
    }
}
