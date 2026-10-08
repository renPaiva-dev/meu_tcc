package br.com.reciclathe.identificacao;

import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

import org.hibernate.annotations.CreationTimestamp;

import br.com.reciclathe.material.Material;
import br.com.reciclathe.usuario.Usuario;
import br.com.reciclathe.zona.Zona;

/**
 * Registro de uma classificação feita no celular. A foto NÃO é enviada na identificação:
 * ela só chega ao servidor se o usuário der feedback e consentir explicitamente (LGPD).
 * A localização é guardada apenas como zona, nunca como coordenada.
 */
@Entity
public class Identificacao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @ManyToOne(optional = false)
    @JoinColumn(name = "material_previsto")
    private Material materialPrevisto;

    private float confianca;

    @ManyToOne
    @JoinColumn(name = "zona_codigo")
    private Zona zona;

    private String versaoModelo;

    /** null = sem feedback; true = o modelo acertou; false = errou (ver materialCorrigido). */
    private Boolean feedbackCorreto;

    @ManyToOne
    @JoinColumn(name = "material_corrigido")
    private Material materialCorrigido;

    private String fotoArquivo;

    @CreationTimestamp
    private Instant criadoEm;

    private Instant feedbackEm;

    protected Identificacao() {
    }

    public Identificacao(Usuario usuario, Material materialPrevisto, float confianca, Zona zona, String versaoModelo) {
        this.usuario = usuario;
        this.materialPrevisto = materialPrevisto;
        this.confianca = confianca;
        this.zona = zona;
        this.versaoModelo = versaoModelo;
    }

    public boolean temFeedback() {
        return feedbackCorreto != null;
    }

    public void registrarFeedback(boolean correto, Material materialCorrigido, String fotoArquivo) {
        this.feedbackCorreto = correto;
        this.materialCorrigido = correto ? null : materialCorrigido;
        this.fotoArquivo = fotoArquivo;
        this.feedbackEm = Instant.now();
    }

    public boolean pertenceA(Long usuarioId) {
        return usuario != null && usuario.getId().equals(usuarioId);
    }

    public Long getId() {
        return id;
    }

    public Usuario getUsuario() {
        return usuario;
    }

    public Material getMaterialPrevisto() {
        return materialPrevisto;
    }

    public float getConfianca() {
        return confianca;
    }

    public Zona getZona() {
        return zona;
    }

    public String getVersaoModelo() {
        return versaoModelo;
    }

    public Boolean getFeedbackCorreto() {
        return feedbackCorreto;
    }

    public Material getMaterialCorrigido() {
        return materialCorrigido;
    }

    public String getFotoArquivo() {
        return fotoArquivo;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }

    public Instant getFeedbackEm() {
        return feedbackEm;
    }
}
