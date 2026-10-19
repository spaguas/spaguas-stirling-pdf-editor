package stirling.software.community;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Internal PDF calls use scoped, versioned credentials; Community never exposes the signing key.
 */
@Service
public class CommunityApiKeys {
    private final CommunityStore store;
    private final byte[] secret;

    public CommunityApiKeys(CommunityStore store, PlatformTransactionManager transactions) {
        this.store = store;
        String value =
                new TransactionTemplate(transactions)
                        .execute(
                                status -> {
                                    store.lock();
                                    return store.setting("api-signing-key")
                                            .orElseGet(
                                                    () -> {
                                                        byte[] bytes = new byte[32];
                                                        new SecureRandom().nextBytes(bytes);
                                                        String encoded =
                                                                Base64.getEncoder()
                                                                        .encodeToString(bytes);
                                                        store.setting("api-signing-key", encoded);
                                                        return encoded;
                                                    });
                                });
        this.secret = Base64.getDecoder().decode(value);
    }

    public String forUsername(String username) {
        if (stirling.software.common.model.enumeration.Role.INTERNAL_API_USER
                .getRoleId()
                .equals(username)) {
            return token("internal", 0);
        }
        return store.byUsername(username)
                .filter(u -> u.enabled() && !u.mustChangePassword())
                .map(u -> token(u.id(), u.version()))
                .orElse(null);
    }

    public Optional<Authentication> authenticate(String credential) {
        if (credential == null || credential.length() > 300) {
            return Optional.empty();
        }
        String[] parts = credential.split("\\.");
        if (parts.length != 4 || !"c1".equals(parts[0])) {
            return Optional.empty();
        }
        String payload = parts[0] + "." + parts[1] + "." + parts[2];
        if (!MessageDigest.isEqual(
                signature(payload).getBytes(StandardCharsets.US_ASCII),
                parts[3].getBytes(StandardCharsets.US_ASCII))) {
            return Optional.empty();
        }
        if ("internal".equals(parts[1]) && "0".equals(parts[2])) {
            var auth =
                    UsernamePasswordAuthenticationToken.authenticated(
                            "community-internal",
                            null,
                            AuthorityUtils.createAuthorityList("ROLE_USER"));
            auth.setDetails("community-api-key");
            return Optional.of(auth);
        }
        return store.user(parts[1])
                .filter(
                        u ->
                                u.enabled()
                                        && !u.mustChangePassword()
                                        && Integer.toString(u.version()).equals(parts[2]))
                .map(
                        u -> {
                            var auth =
                                    UsernamePasswordAuthenticationToken.authenticated(
                                            u, null, u.getAuthorities());
                            auth.setDetails("community-api-key");
                            return auth;
                        });
    }

    private String token(String id, int version) {
        String payload = "c1." + id + "." + version;
        return payload + "." + signature(payload);
    }

    private String signature(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.US_ASCII)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HMAC-SHA256 is unavailable", exception);
        }
    }
}
