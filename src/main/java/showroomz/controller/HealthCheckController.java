package showroomz.controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthCheckController {

    @GetMapping("/")
    public String healthCheck() {
        return "Showroomz server is running! CI/CD Success! 🚀";
    }
}