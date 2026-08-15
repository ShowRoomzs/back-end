package showroomz.global.service;

import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.exception.GeoIp2Exception;
import com.maxmind.geoip2.model.CityResponse;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;

/**
 * GeoIP 조회 서비스.
 *
 * <p>mmdb(63MB)는 더 이상 jar에 번들하지 않는다 — {@link #dbPath}가 가리키는 파일 경로에서 읽는다.
 * {@code File} 생성자는 기본적으로 메모리 매핑(mmap) 방식으로 로딩되어, {@code InputStream}
 * 생성자처럼 파일 전체를 힙에 올리지 않는다. 파일이 없으면(로컬 개발 등) 조회 불가로 처리하고
 * 애플리케이션 기동은 막지 않는다.
 */
@Service
@Slf4j
public class GeoLocationService {

    @Value("${geoip.db-path}")
    private String dbPath;

    private DatabaseReader databaseReader;

    @PostConstruct
    public void init() {
        File dbFile = new File(dbPath);
        if (!dbFile.exists()) {
            log.warn("GeoIP Database not found at {}. Location lookup is disabled.", dbPath);
            return;
        }
        try {
            databaseReader = new DatabaseReader.Builder(dbFile).build();
            log.info("GeoIP Database loaded from {}", dbPath);
        } catch (IOException e) {
            log.error("Failed to load GeoIP Database: {}", e.getMessage());
        }
    }

    public GeoLocation getLocation(String ip) {
        // 로컬호스트(127.0.0.1) 등 조회 불가능한 IP 처리
        if (databaseReader == null || ip == null || 
            ip.equals("127.0.0.1") || ip.equals("0:0:0:0:0:0:0:1")) {
            return new GeoLocation("Unknown", "Unknown");
        }

        try {
            InetAddress ipAddress = InetAddress.getByName(ip);
            CityResponse response = databaseReader.city(ipAddress);

            // 한글 이름 우선 조회 ("ko" 또는 기본 영문)
            String country = null;
            String city = null;

            // 국가 이름 조회
            if (response.getCountry() != null && response.getCountry().getNames() != null) {
                country = response.getCountry().getNames().get("ko");
                if (country == null || country.isEmpty()) {
                    country = response.getCountry().getName();
                }
            }

            // 도시 이름 조회
            if (response.getCity() != null && response.getCity().getNames() != null) {
                city = response.getCity().getNames().get("ko");
                if (city == null || city.isEmpty()) {
                    city = response.getCity().getName();
                }
            }

            // null 방지
            if (country == null || country.isEmpty()) country = "Unknown";
            if (city == null || city.isEmpty()) city = "Unknown";

            log.debug("IP: {}, Country: {}, City: {}", ip, country, city);

            return new GeoLocation(country, city);

        } catch (IOException | GeoIp2Exception e) {
            // 사설 IP(로컬호스트 등)이거나 DB에 없는 IP일 경우 발생
            log.debug("Failed to get location for IP: {}, error: {}", ip, e.getMessage());
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
