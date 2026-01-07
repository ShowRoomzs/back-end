package showroomz.api.seller.auth.refreshToken;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "ADMIN_REFRESH_TOKEN")
public class SellerRefreshToken {
    @JsonIgnore
    @Id
    @Column(name = "REFRESH_TOKEN_SEQ")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long refreshTokenSeq;

    @Column(name = "ADMIN_EMAIL", length = 512, unique = true)
    @NotNull
    @Size(max = 512)
    private String adminEmail;

    @Column(name = "REFRESH_TOKEN", length = 256)
    @NotNull
    @Size(max = 256)
    private String refreshToken;

    public SellerRefreshToken(String adminEmail, String refreshToken) {
        this.adminEmail = adminEmail;
        this.refreshToken = refreshToken;
    }
}

