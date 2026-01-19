package showroomz.api.admin.history.DTO;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class LocationFilterResponse {
    private String country;
    private List<String> cities;
}
