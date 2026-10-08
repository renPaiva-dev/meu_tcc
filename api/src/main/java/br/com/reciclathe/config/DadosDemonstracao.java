package br.com.reciclathe.config;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import br.com.reciclathe.ponto.DadosPonto;
import br.com.reciclathe.ponto.PontoService;
import br.com.reciclathe.ponto.TipoPonto;

/**
 * Pontos FICTÍCIOS para demonstração e testes de usabilidade (perfil "demo").
 * Os nomes começam com [DEMO] e os endereços não correspondem a pontos reais:
 * a base real deve ser cadastrada a partir de fontes públicas, com a fonte registrada.
 */
@Component
@Profile("demo")
@Order(10)
public class DadosDemonstracao implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DadosDemonstracao.class);

    private final PontoService pontos;

    public DadosDemonstracao(PontoService pontos) {
        this.pontos = pontos;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!pontos.listarAprovados().isEmpty()) {
            return;
        }
        String fonte = "Dado fictício de demonstração";
        List<DadosPonto> exemplos = List.of(
                new DadosPonto("[DEMO] PEV Centro", TipoPonto.PEV, "Exemplo — região central", -5.0892, -42.8016,
                        "Todos os dias", null, fonte, List.of("PLASTICO", "PAPEL", "METAL", "VIDRO")),
                new DadosPonto("[DEMO] Cooperativa de vidro", TipoPonto.COOPERATIVA, "Exemplo — zona Norte",
                        -5.0450, -42.7900, "Seg a sex, 8h às 17h", null, fonte, List.of("VIDRO")),
                new DadosPonto("[DEMO] Loja com coleta de pilhas", TipoPonto.LOGISTICA_REVERSA, "Exemplo — zona Leste",
                        -5.0800, -42.7700, "Horário comercial", null, fonte, List.of("ELETRONICO")),
                new DadosPonto("[DEMO] PEV Sul", TipoPonto.PEV, "Exemplo — zona Sul", -5.1350, -42.7950,
                        "Todos os dias", null, fonte, List.of("PLASTICO", "PAPEL", "METAL")),
                new DadosPonto("[DEMO] Ecoponto Sudeste", TipoPonto.ECOPONTO, "Exemplo — zona Sudeste",
                        -5.1000, -42.7400, "Seg a sáb, 7h às 18h", null, fonte,
                        List.of("PLASTICO", "PAPEL", "METAL", "VIDRO", "ELETRONICO")));
        exemplos.forEach(d -> pontos.salvar(null, d));
        log.info("{} pontos de demonstração criados.", exemplos.size());
    }
}
