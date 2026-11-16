package ar.edu.is2.scouting.domain.user;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.Locale;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserAccountService implements UserDetailsService {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;

    public UserAccountService(JdbcTemplate jdbc, PasswordEncoder encoder) {
        this.jdbc = jdbc;
        this.encoder = encoder;
    }

    @Override
    public org.springframework.security.core.userdetails.UserDetails loadUserByUsername(String email) {
        return jdbc.query(
                        "select email, password_hash, role, active from user_account where lower(email)=lower(?)",
                        (rs, row) -> User.withUsername(rs.getString("email"))
                                .password(rs.getString("password_hash"))
                                .roles(rs.getString("role"))
                                .disabled(!rs.getBoolean("active"))
                                .build(),
                        email)
                .stream().findFirst()
                .orElseThrow(() -> new UsernameNotFoundException("Usuario inexistente"));
    }

    @Transactional
    public long register(String fullName, String email, String password, UserRole role) {
        if (fullName == null || fullName.isBlank()) throw new IllegalArgumentException("El nombre es obligatorio");
        if (email == null || !email.contains("@")) throw new IllegalArgumentException("El correo no es válido");
        if (password == null || password.length() < 8) throw new IllegalArgumentException("La contraseña debe tener al menos 8 caracteres");
        var key = new GeneratedKeyHolder();
        try {
            jdbc.update(connection -> {
                PreparedStatement ps = connection.prepareStatement(
                        "insert into user_account(full_name,email,password_hash,role) values (?,?,?,?)",
                        Statement.RETURN_GENERATED_KEYS);
                ps.setString(1, fullName.trim());
                ps.setString(2, email.trim().toLowerCase(Locale.ROOT));
                ps.setString(3, encoder.encode(password));
                ps.setString(4, role.name());
                return ps;
            }, key);
        } catch (DuplicateKeyException exception) {
            throw new IllegalArgumentException("Ya existe una cuenta con ese correo");
        }
        return ((Number) key.getKeys().get("id")).longValue();
    }

    public long idByEmail(String email) {
        return jdbc.queryForObject("select id from user_account where lower(email)=lower(?)", Long.class, email);
    }
}
