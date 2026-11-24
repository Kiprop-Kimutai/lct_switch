package compas.device;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/lct/rest")
public class DeviceRestController {
    private final DeviceService service;

    public DeviceRestController(DeviceService service) {
        this.service = service;
    }

    @GetMapping
    public String sendGreeting() {
        return "Hello Garrixx. RestController >>>>>>>>.";
    }

    @GetMapping({"/getdevice", "/getdevice/"})
    public Device findDeviceByMac(@RequestParam("mac") String macAddress) {
        return service.findByMac(macAddress);
    }

    @PostMapping({"/saveDevices", "/saveDevices/"})
    public List<Device> saveDevices(@RequestBody List<Device> devices) {
        // This legacy endpoint echoes the request; it does not persist devices.
        return devices;
    }

    @PostMapping({"/savedevice", "/savedevice/"})
    public Device saveDevice(@RequestBody Device device) {
        return service.saveViaRest(device);
    }

    @GetMapping({"/test", "/test/"})
    public String testRestService() {
        return service.testRestConnection();
    }
}
