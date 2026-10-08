package br.com.reciclathe.admin;

import java.util.List;

import org.springframework.validation.BindingResult;

final class Validacao {

    private Validacao() {
    }

    /** Mensagens legíveis ("latitude: fora de Teresina") para exibir no topo do formulário. */
    static List<String> mensagens(BindingResult erros) {
        return erros.getFieldErrors().stream()
                .map(e -> e.getField() + ": " + (e.isBindingFailure() ? "valor inválido" : e.getDefaultMessage()))
                .toList();
    }
}
