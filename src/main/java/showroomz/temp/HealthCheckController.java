package showroomz.temp;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Hidden;

@RestController
public class HealthCheckController {

    @GetMapping("/")
    @Hidden
    public String healthCheck() {
        return "Showroomz server is running! CI/CD Success! 🚀";
    }
}