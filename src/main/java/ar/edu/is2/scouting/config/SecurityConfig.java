package ar.edu.is2.scouting.config;

import static ar.edu.is2.scouting.domain.user.UserRole.COORDINATOR;
import static ar.edu.is2.scouting.domain.user.UserRole.DIRECTOR;
import static ar.edu.is2.scouting.domain.user.UserRole.SCOUT;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/", "/login", "/css/**", "/actuator/health").permitAll()
                        .anyRequest().authenticated())
                .formLogin(form -> form.loginPage("/login").defaultSuccessUrl("/", true).permitAll())
                .logout(logout -> logout.logoutSuccessUrl("/"));

        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    UserDetailsService demoUsers(PasswordEncoder passwordEncoder) {
        var password = passwordEncoder.encode("demo1234");

        return new InMemoryUserDetailsManager(
                User.withUsername("dt@demo.local")
                        .password(password)
                        .roles(DIRECTOR.securityName())
                        .build(),
                User.withUsername("coordinador@demo.local")
                        .password(password)
                        .roles(COORDINATOR.securityName())
                        .build(),
                User.withUsername("scout@demo.local")
                        .password(password)
                        .roles(SCOUT.securityName())
                        .build());
    }
}

