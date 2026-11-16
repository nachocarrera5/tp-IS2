package ar.edu.is2.scouting.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/", "/login", "/register", "/css/**", "/actuator/health").permitAll()
                        .requestMatchers("/players/import/**").hasRole("COORDINATOR")
                        .requestMatchers("/evaluations/**").hasRole("SCOUT")
                        .requestMatchers("/app/**", "/players/**", "/needs/**", "/rankings/**", "/candidates/**").authenticated()
                        .anyRequest().authenticated())
                .formLogin(form -> form.loginPage("/login").defaultSuccessUrl("/app", true).permitAll())
                .logout(logout -> logout.logoutSuccessUrl("/login?logout"))
                .exceptionHandling(exceptions -> exceptions.accessDeniedPage("/access-denied"));

        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

}

