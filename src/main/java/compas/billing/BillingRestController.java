package compas.billing;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/lct/rest")
public class BillingRestController {
    @GetMapping("/")
    public String testConnection() {
        return "ok";
    }
}
