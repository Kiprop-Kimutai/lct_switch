package compas.api;

import compas.device.DeviceNotFoundException;
import compas.device.DeviceRestController;
import compas.device.DeviceWsController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = {DeviceRestController.class, DeviceWsController.class})
public class ApiExceptionHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(DeviceNotFoundException.class)
    public ProblemDetail deviceNotFound(DeviceNotFoundException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(UpstreamException.class)
    public ProblemDetail upstreamFailure(UpstreamException exception) {
        LOGGER.error("{}", exception.getMessage(), exception);
        boolean timeout = exception.isTimeout();
        HttpStatus status = timeout ? HttpStatus.GATEWAY_TIMEOUT : HttpStatus.BAD_GATEWAY;
        return ProblemDetail.forStatusAndDetail(status,
                timeout ? "Upstream service timed out" : "Upstream service request failed");
    }
}
