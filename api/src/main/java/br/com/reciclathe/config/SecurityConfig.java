package br.com.reciclathe.config;

import java.nio.charset.StandardCharsets;
import java.time.Clock;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import com.nimbusds.jose.jwk.source.ImmutableSecret;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.SecurityFilterChain;

import br.com.reciclathe.usuario.TokenService;
import br.com.reciclathe.usuario.UsuarioRepository;

/**
 * Duas cadeias de segurança: a API do app (stateless, token JWT) e o painel web
 * do administrador (sessão com login por formulário).
 */
@Configuration
public class SecurityConfig {

    /** Antes da cadeia do Spring Security (ordem -100), que é quem trata o POST /login do painel. */
    private static final int ORDEM_LIMITE_TENTATIVAS = -110;

    @Bean
    @Order(1)
    SecurityFilterChain apiFilterChain(HttpSecurity http) throws Exception {
        http.securityMatcher("/api/**")
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        .requestMatchers(HttpMethod.GET,
                                "/api/v1/materiais/**", "/api/v1/zonas/**", "/api/v1/pontos/**", "/api/v1/ranking")
                        .permitAll()
                        .anyRequest().authenticated())
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .oauth2ResourceServer(o -> o.jwt(Customizer.withDefaults()));
        return http.build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain painelFilterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(auth -> auth
                        .requestMatchers("/login", "/css/**", "/error", "/actuator/health", "/actuator/health/**",
                                "/v3/api-docs", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**")
                        .permitAll()
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .formLogin(form -> form.loginPage("/login").defaultSuccessUrl("/admin", true).permitAll())
                .logout(logout -> logout.logoutSuccessUrl("/login?saiu").permitAll());
        return http.build();
    }

    @Bean
    FilterRegistrationBean<LimiteTentativasFilter> limiteTentativas(ReciclatheProperties props) {
        var registro = new FilterRegistrationBean<>(
                new LimiteTentativasFilter(props.limites().tentativasPorMinuto(), Clock.systemUTC()));
        registro.setOrder(ORDEM_LIMITE_TENTATIVAS);
        return registro;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    JwtEncoder jwtEncoder(ReciclatheProperties props) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(chave(props)));
    }

    /**
     * Além da assinatura e da validade, confere o emissor e se a conta ainda existe e está ativa:
     * conta bloqueada ou excluída perde o acesso na hora, sem esperar o token expirar.
     */
    @Bean
    JwtDecoder jwtDecoder(ReciclatheProperties props, UsuarioRepository usuarios) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(chave(props)).macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(TokenService.EMISSOR),
                jwt -> contaAtiva(jwt, usuarios)));
        return decoder;
    }

    private static OAuth2TokenValidatorResult contaAtiva(Jwt jwt, UsuarioRepository usuarios) {
        try {
            if (usuarios.existsByIdAndAtivoTrue(Long.valueOf(jwt.getSubject()))) {
                return OAuth2TokenValidatorResult.success();
            }
        } catch (NumberFormatException e) {
            // subject inválido: cai na recusa abaixo
        }
        return OAuth2TokenValidatorResult.failure(
                new OAuth2Error("invalid_token", "Conta inexistente ou bloqueada", null));
    }

    private static SecretKey chave(ReciclatheProperties props) {
        return new SecretKeySpec(props.jwt().segredo().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }
}
