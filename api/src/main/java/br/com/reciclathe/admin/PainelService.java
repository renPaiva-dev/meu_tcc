package br.com.reciclathe.admin;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/** Indicadores agregados e anônimos para o painel administrativo. */
@Service
public class PainelService {

    private final JdbcClient jdbc;

    public PainelService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Resumo resumo(int dias) {
        OffsetDateTime desde = desde(dias);
        return jdbc.sql("""
                        SELECT
                          (SELECT count(*) FROM usuario WHERE perfil = 'CIDADAO') AS usuarios,
                          (SELECT count(*) FROM identificacao WHERE criado_em >= :desde) AS identificacoes,
                          (SELECT count(*) FROM ponto_coleta WHERE status = 'APROVADO') AS pontos,
                          (SELECT count(*) FROM ponto_coleta WHERE status = 'PENDENTE') AS pendentes
                        """)
                .param("desde", desde)
                .query((rs, i) -> new Resumo(rs.getLong("usuarios"), rs.getLong("identificacoes"),
                        rs.getLong("pontos"), rs.getLong("pendentes")))
                .single();
    }

    /** Quantidade de identificações por zona e material (material final: o corrigido, se houver). */
    public List<Celula> materiaisPorZona(int dias) {
        return jdbc.sql("""
                        SELECT COALESCE(i.zona_codigo, 'SEM_ZONA') AS zona,
                               COALESCE(i.material_corrigido, i.material_previsto) AS material,
                               count(*) AS total
                        FROM identificacao i
                        WHERE i.criado_em >= :desde
                        GROUP BY 1, 2
                        """)
                .param("desde", desde(dias))
                .query((rs, i) -> new Celula(rs.getString("zona"), rs.getString("material"), rs.getLong("total")))
                .list();
    }

    /**
     * Acurácia do modelo em uso real, por material previsto, considerando só as identificações
     * que receberam feedback. É o indicador que complementa a avaliação offline do modelo.
     */
    public List<AcuraciaMaterial> acuraciaEmUso(int dias) {
        return jdbc.sql("""
                        SELECT m.codigo, m.nome,
                               count(i.feedback_correto) AS avaliadas,
                               count(*) FILTER (WHERE i.feedback_correto) AS corretas
                        FROM material m
                        LEFT JOIN identificacao i ON i.material_previsto = m.codigo AND i.criado_em >= :desde
                        GROUP BY m.codigo, m.nome, m.ordem
                        ORDER BY m.ordem
                        """)
                .param("desde", desde(dias))
                .query((rs, i) -> new AcuraciaMaterial(rs.getString("codigo"), rs.getString("nome"),
                        rs.getLong("avaliadas"), rs.getLong("corretas")))
                .list();
    }

    /** OffsetDateTime porque o driver JDBC do PostgreSQL não converte Instant diretamente. */
    private static OffsetDateTime desde(int dias) {
        return OffsetDateTime.now(ZoneOffset.UTC).minus(dias, ChronoUnit.DAYS);
    }

    public record Resumo(long usuarios, long identificacoes, long pontosAprovados, long sugestoesPendentes) {
    }

    public record Celula(String zona, String material, long total) {
    }

    public record AcuraciaMaterial(String codigo, String nome, long avaliadas, long corretas) {
        public Double percentual() {
            return avaliadas == 0 ? null : 100.0 * corretas / avaliadas;
        }
    }
}
