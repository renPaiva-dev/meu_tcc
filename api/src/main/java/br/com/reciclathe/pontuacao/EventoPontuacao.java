package br.com.reciclathe.pontuacao;

import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

import org.hibernate.annotations.CreationTimestamp;

import br.com.reciclathe.usuario.Usuario;

@Entity
public class EventoPontuacao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoEvento tipo;

    private int pontos;

    /** Id do ponto de coleta ou da identificação que originou o evento. */
    private Long referenciaId;

    @CreationTimestamp
    private Instant criadoEm;

    /** Dia do evento no fuso de Teresina: base das regras diárias e da restrição única do check-in. */
    @Column(nullable = false)
    private LocalDate dia;

    protected EventoPontuacao() {
    }

    public EventoPontuacao(Usuario usuario, TipoEvento tipo, Long referenciaId, LocalDate dia) {
        this.usuario = usuario;
        this.tipo = tipo;
        this.pontos = tipo.getPontos();
        this.referenciaId = referenciaId;
        this.dia = dia;
    }

    public Long getId() {
        return id;
    }

    public TipoEvento getTipo() {
        return tipo;
    }

    public int getPontos() {
        return pontos;
    }

    public Long getReferenciaId() {
        return referenciaId;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }

    public LocalDate getDia() {
        return dia;
    }
}
