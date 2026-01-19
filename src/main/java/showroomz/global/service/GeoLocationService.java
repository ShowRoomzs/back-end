package showroomz.global.service;

import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.exception.GeoIp2Exception;
import com.maxmind.geoip2.model.CityResponse;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.InetAddress;

@Service
@Slf4j
public class GeoLocationService {

    private DatabaseReader databaseReader;

    @PostConstruct
    public void init() {
        try {
            ClassPathResource resource = new ClassPathResource("GeoLite2-City.mmdb");
            databaseReader = new DatabaseReader.Builder(resource.getInputStream()).build();
            log.info("GeoIP Database loaded successfully.");
        } catch (IOException e) {
            log.error("Failed to load GeoIP Database: {}", e.getMessage());
        }
    }

    public GeoLocation getLocation(String ip) {
        if (databaseReader == null || ip == null) {
            return new GeoLocation("Unknown", "Unknown");
        }

        try {
            InetAddress ipAddress = InetAddress.getByName(ip);
            CityResponse response = databaseReader.city(ipAddress);

            String country = (response.getCountry().getName() != null) ? response.getCountry().getName() : "Unknown";
            String city = (response.getCity().getName() != null) ? response.getCity().getName() : "Unknown";

            return new GeoLocation(country, city);

        } catch (IOException | GeoIp2Exception e) {
            // 사설 IP(로컬호스트 등)이거나 DB에 없는 IP일 경우 발생
            return new GeoLocation("Unknown", "Unknown");
        }
    }

    @Getter
    public static class GeoLocation {
        private final String country;
        private final String city;

        public GeoLocation(String country, String city) {
            this.country = country;
            this.city = city;
        }
    }
}
