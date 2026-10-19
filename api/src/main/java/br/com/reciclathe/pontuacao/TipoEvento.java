package br.com.reciclathe.pontuacao;

/**
 * Ações que geram pontos. Só pontuam ações que o sistema consegue validar ou limitar:
 * fotografar um item não pontua, porque não prova que houve descarte.
 */
public enum TipoEvento {
    /** Ponto de coleta sugerido pelo usuário e aprovado por um administrador. */
    SUGESTAO_APROVADA(20),
    /** Presença confirmada por GPS em um ponto de coleta (1 por ponto por dia, com teto diário). */
    CHECKIN(5),
    /** Feedback sobre uma classificação, certa ou errada (limite diário). */
    FEEDBACK(2);

    private final int pontos;

    TipoEvento(int pontos) {
        this.pontos = pontos;
    }

    public int getPontos() {
        return pontos;
    }
}
