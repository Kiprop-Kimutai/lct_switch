package compas.device;

public class DeviceNotFoundException extends RuntimeException {
    public DeviceNotFoundException() {
        super("Device not found");
    }
}
