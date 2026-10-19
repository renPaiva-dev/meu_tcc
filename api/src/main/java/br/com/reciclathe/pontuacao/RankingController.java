package br.com.reciclathe.pontuacao;

import java.util.List;
import java.util.stream.IntStream;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.com.reciclathe.zona.ZonaService;

@RestController
@RequestMapping("/api/v1/ranking")
public class RankingController {

    private final PontuacaoService pontuacao;
    private final ZonaService zonas;

    public RankingController(PontuacaoService pontuacao, ZonaService zonas) {
        this.pontuacao = pontuacao;
        this.zonas = zonas;
    }

    /** Top 10 geral ou de uma zona (404 se a zona não existir). Só expõe o nome abreviado (ex.: "Maria S."). */
    @GetMapping
    public List<ItemRanking> ranking(@RequestParam(required = false) String zona) {
        String codigo = zona == null || zona.isBlank() ? null : zonas.buscar(zona.strip()).getCodigo();
        List<PosicaoRanking> posicoes = pontuacao.ranking(codigo, 10);
        return IntStream.range(0, posicoes.size())
                .mapToObj(i -> new ItemRanking(i + 1, nomePublico(posicoes.get(i).nome()), posicoes.get(i).pontos()))
                .toList();
    }

    static String nomePublico(String nome) {
        String[] partes = nome.strip().split("\\s+");
        return partes.length == 1 ? partes[0] : partes[0] + " " + partes[partes.length - 1].charAt(0) + ".";
    }

    public record ItemRanking(int posicao, String nome, long pontos) {
    }
}
