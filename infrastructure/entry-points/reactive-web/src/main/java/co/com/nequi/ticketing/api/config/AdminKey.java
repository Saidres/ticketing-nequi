package co.com.nequi.ticketing.api.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

// Protects admin operations. The key comes from the ADMIN_API_KEY environment variable (Secrets Manager in AWS);
// when it is not configured, admin operations are disabled instead of open
@Component
public class AdminKey {

    private final byte[] expected;

    public AdminKey(@Value("${ticketing.admin-api-key:}") String configured) {
        this.expected = configured.getBytes(StandardCharsets.UTF_8);
    }

    // Constant-time comparison, so response timing does not reveal how much of a guess was right
    public boolean matches(String provided) {
        return expected.length > 0 && provided != null
                && MessageDigest.isEqual(expected, provided.getBytes(StandardCharsets.UTF_8));
    }
}
