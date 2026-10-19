package br.com.reciclathe.ponto;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;

import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import br.com.reciclathe.material.Material;
import br.com.reciclathe.usuario.Usuario;

/**
 * Local onde o cidadão pode entregar resíduos. A coluna geográfica "localizacao" existe só no
 * banco (gerada a partir de latitude/longitude) e é usada nas buscas por proximidade.
 *
 * <p>As associações são LAZY: cada consulta do repositório diz o que precisa carregar (@EntityGraph),
 * já que as respostas são montadas fora da transação (spring.jpa.open-in-view=false).
 */
@Entity
public class PontoColeta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nome;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoPonto tipo;

    @Column(nullable = false)
    private String endereco;

    private double latitude;

    private double longitude;

    private String horario;

    private String telefone;

    /** De onde veio a informação (site da prefeitura, notícia, sugestão de usuário...). */
    private String fonte;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusPonto status;

    private String motivoRecusa;

    private String fotoArquivo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sugerido_por")
    private Usuario sugeridoPor;

    /** Administrador que aprovou ou recusou o ponto pela última vez (auditoria). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "revisado_por")
    private Usuario revisadoPor;

    private Instant revisadoEm;

    /** Elemento de origem no OpenStreetMap ("node", "way" ou "relation"), se foi importado de lá. */
    private String osmTipo;

    private Long osmId;

    @ManyToMany(fetch = FetchType.LAZY)
    @BatchSize(size = 100)
    @JoinTable(name = "ponto_material",
            joinColumns = @JoinColumn(name = "ponto_id"),
            inverseJoinColumns = @JoinColumn(name = "material_codigo"))
    private Set<Material> materiais = new HashSet<>();

    @CreationTimestamp
    private Instant criadoEm;

    @UpdateTimestamp
    private Instant atualizadoEm;

    protected PontoColeta() {
    }

    public PontoColeta(StatusPonto status) {
        this.status = status;
    }

    public void atualizar(String nome, TipoPonto tipo, String endereco, double latitude, double longitude,
                          String horario, String telefone, String fonte, Set<Material> materiais) {
        this.nome = nome;
        this.tipo = tipo;
        this.endereco = endereco;
        this.latitude = latitude;
        this.longitude = longitude;
        this.horario = horario;
        this.telefone = telefone;
        this.fonte = fonte;
        this.materiais.clear();
        this.materiais.addAll(materiais);
    }

    public void aprovar(Usuario revisor) {
        this.status = StatusPonto.APROVADO;
        this.motivoRecusa = null;
        registrarRevisao(revisor);
    }

    public void recusar(String motivo, Usuario revisor) {
        this.status = StatusPonto.RECUSADO;
        this.motivoRecusa = motivo;
        registrarRevisao(revisor);
    }

    private void registrarRevisao(Usuario revisor) {
        this.revisadoPor = revisor;
        this.revisadoEm = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public TipoPonto getTipo() {
        return tipo;
    }

    public String getEndereco() {
        return endereco;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public String getHorario() {
        return horario;
    }

    public String getTelefone() {
        return telefone;
    }

    public String getFonte() {
        return fonte;
    }

    public StatusPonto getStatus() {
        return status;
    }

    public String getMotivoRecusa() {
        return motivoRecusa;
    }

    public String getFotoArquivo() {
        return fotoArquivo;
    }

    public void setFotoArquivo(String fotoArquivo) {
        this.fotoArquivo = fotoArquivo;
    }

    public Usuario getSugeridoPor() {
        return sugeridoPor;
    }

    public void setSugeridoPor(Usuario sugeridoPor) {
        this.sugeridoPor = sugeridoPor;
    }

    public Usuario getRevisadoPor() {
        return revisadoPor;
    }

    public Instant getRevisadoEm() {
        return revisadoEm;
    }

    public void setOrigemOsm(String osmTipo, Long osmId) {
        this.osmTipo = osmTipo;
        this.osmId = osmId;
    }

    public String getOsmTipo() {
        return osmTipo;
    }

    public Long getOsmId() {
        return osmId;
    }

    public Set<Material> getMateriais() {
        return materiais;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }

    public Instant getAtualizadoEm() {
        return atualizadoEm;
    }
}
