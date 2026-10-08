package br.com.reciclathe.pontuacao;

import java.util.List;
import java.util.stream.IntStream;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ranking")
public class RankingController {

    private final PontuacaoService pontuacao;

    public RankingController(PontuacaoService pontuacao) {
        this.pontuacao = pontuacao;
    }

    /** Top 10 geral ou de uma zona. Só expõe o nome abreviado (ex.: "Maria S."). */
    @GetMapping
    public List<ItemRanking> ranking(@RequestParam(required = false) String zona) {
        List<PosicaoRanking> posicoes = pontuacao.ranking(zona, 10);
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
