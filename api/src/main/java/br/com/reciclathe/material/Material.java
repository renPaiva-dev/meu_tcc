package br.com.reciclathe.material;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;

import org.hibernate.annotations.UpdateTimestamp;

/**
 * Categoria de resíduo reconhecida pelo modelo, com a orientação de descarte para Teresina.
 * O código é o mesmo rótulo que o classificador devolve no app.
 */
@Entity
public class Material {

    @Id
    private String codigo;

    @Column(nullable = false)
    private String nome;

    @Column(nullable = false)
    private String corHex;

    @Column(nullable = false)
    private String corNome;

    private boolean reciclavel;

    private int ordem;

    @Column(nullable = false)
    private String comoPreparar;

    @Column(nullable = false)
    private String ondeDescartar;

    private String observacoes;

    @Column(nullable = false)
    private String fonte;

    @UpdateTimestamp
    private Instant atualizadoEm;

    protected Material() {
    }

    public void atualizarOrientacao(String comoPreparar, String ondeDescartar, String observacoes, String fonte) {
        this.comoPreparar = comoPreparar;
        this.ondeDescartar = ondeDescartar;
        this.observacoes = observacoes;
        this.fonte = fonte;
    }

    public String getCodigo() {
        return codigo;
    }

    public String getNome() {
        return nome;
    }

    public String getCorHex() {
        return corHex;
    }

    public String getCorNome() {
        return corNome;
    }

    public boolean isReciclavel() {
        return reciclavel;
    }

    public int getOrdem() {
        return ordem;
    }

    public String getComoPreparar() {
        return comoPreparar;
    }

    public String getOndeDescartar() {
        return ondeDescartar;
    }

    public String getObservacoes() {
        return observacoes;
    }

    public String getFonte() {
        return fonte;
    }

    public Instant getAtualizadoEm() {
        return atualizadoEm;
    }
}
