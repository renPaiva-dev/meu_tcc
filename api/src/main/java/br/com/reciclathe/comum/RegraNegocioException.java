package br.com.reciclathe.comum;

/** Requisição válida no formato, mas que viola uma regra do sistema (ex.: check-in longe do ponto). */
public class RegraNegocioException extends RuntimeException {

    public RegraNegocioException(String mensagem) {
        super(mensagem);
    }
}
