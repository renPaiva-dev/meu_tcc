package br.com.reciclathe.comum;

/** O dado enviado conflita com um registro já existente (ex.: e-mail já cadastrado). Vira HTTP 409. */
public class ConflitoException extends RuntimeException {

    public ConflitoException(String mensagem) {
        super(mensagem);
    }
}
