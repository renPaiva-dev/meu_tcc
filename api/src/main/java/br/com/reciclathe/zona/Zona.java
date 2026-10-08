package br.com.reciclathe.zona;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/** Zona da coleta seletiva de Teresina (Centro, Norte, Sul, Leste, Sudeste). */
@Entity
public class Zona {

    @Id
    private String codigo;

    @Column(nullable = false)
    private String nome;

    private String telefoneColeta;

    private String fonte;

    protected Zona() {
    }

    public String getCodigo() {
        return codigo;
    }

    public String getNome() {
        return nome;
    }

    public String getTelefoneColeta() {
        return telefoneColeta;
    }

    public void setTelefoneColeta(String telefoneColeta) {
        this.telefoneColeta = telefoneColeta;
    }

    public String getFonte() {
        return fonte;
    }

    public void setFonte(String fonte) {
        this.fonte = fonte;
    }
}
