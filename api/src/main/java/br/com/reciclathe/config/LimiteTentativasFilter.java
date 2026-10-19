package br.com.reciclathe.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Limite simples, em memória, de tentativas de login e cadastro por IP (janela fixa de um minuto),
 * contra força bruta e cadastro em massa. Cada instância da API conta separadamente, o que basta
 * para o porte do projeto. Atrás de proxy, o IP vem do X-Forwarded-For (server.forward-headers-strategy).
 */
public class LimiteTentativasFilter extends OncePerRequestFilter {

    private static final String LOGIN_PAINEL = "/login";
    private static final Set<String> ROTAS = Set.of("/api/v1/auth/login", "/api/v1/auth/cadastro", LOGIN_PAINEL);
    private static final int MAXIMO_DE_JANELAS = 10_000;

    private final int limitePorMinuto;
    private final Clock relogio;
    private final Map<String, Janela> janelas = new ConcurrentHashMap<>();

    public LimiteTentativasFilter(int limitePorMinuto, Clock relogio) {
        this.limitePorMinuto = limitePorMinuto;
        this.relogio = relogio;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equals(request.getMethod()) || !ROTAS.contains(rota(request));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long minuto = relogio.millis() / 60_000;
        String rota = rota(request);
        Janela janela = janelas.compute(request.getRemoteAddr() + " " + rota,
                (chave, atual) -> atual == null || atual.minuto != minuto ? new Janela(minuto) : atual);
        if (janelas.size() > MAXIMO_DE_JANELAS) {
            janelas.values().removeIf(j -> j.minuto != minuto);
        }
        if (janela.tentativas.incrementAndGet() <= limitePorMinuto) {
            chain.doFilter(request, response);
            return;
        }
        if (LOGIN_PAINEL.equals(rota)) {
            response.sendRedirect(request.getContextPath() + "/login?limite");
            return;
        }
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader(HttpHeaders.RETRY_AFTER, "60");
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("""
                {"type":"about:blank","title":"Too Many Requests","status":429,\
                "detail":"Muitas tentativas. Aguarde um minuto e tente novamente.","instance":"%s"}"""
                .formatted(rota));
    }

    private static String rota(HttpServletRequest request) {
        return request.getRequestURI().substring(request.getContextPath().length());
    }

    private static final class Janela {
        private final long minuto;
        private final AtomicInteger tentativas = new AtomicInteger();

        private Janela(long minuto) {
            this.minuto = minuto;
        }
    }
}
