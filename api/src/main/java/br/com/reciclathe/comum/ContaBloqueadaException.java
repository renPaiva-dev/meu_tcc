package br.com.reciclathe.comum;

public class ContaBloqueadaException extends RuntimeException {

    public ContaBloqueadaException() {
        super("Esta conta está bloqueada. Fale com a equipe do ReciclaTHE.");
    }
}
