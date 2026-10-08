package br.com.reciclathe.ponto;

public enum TipoPonto {
    PEV("Ponto de Entrega Voluntária"),
    COOPERATIVA("Cooperativa de reciclagem"),
    ECOPONTO("Ecoponto"),
    CENTRO_RECICLAGEM("Centro de reciclagem / sucata"),
    LOGISTICA_REVERSA("Logística reversa (lojas, farmácias)"),
    OUTRO("Outro");

    private final String descricao;

    TipoPonto(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }
}
