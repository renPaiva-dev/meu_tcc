package br.com.reciclathe.comum;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.BindException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Converte as exceções da API em respostas RFC 9457 (application/problem+json).
 * Vale só para os @RestController: o painel (@Controller) mostra páginas de erro em HTML.
 */
@RestControllerAdvice(annotations = RestController.class)
public class ApiExceptionHandler {

    @ExceptionHandler(RecursoNaoEncontradoException.class)
    ProblemDetail naoEncontrado(RecursoNaoEncontradoException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(RegraNegocioException.class)
    ProblemDetail regraNegocio(RegraNegocioException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, e.getMessage());
    }

    @ExceptionHandler(ConflitoException.class)
    ProblemDetail conflito(ConflitoException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }

    /** Restrição única violada por duas requisições simultâneas (a verificação prévia não pega a corrida). */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail integridade(DataIntegrityViolationException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "A operação conflita com um registro já existente (ação repetida?). Atualize e tente novamente.");
    }

    @ExceptionHandler(CredenciaisInvalidasException.class)
    ProblemDetail credenciais(CredenciaisInvalidasException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, e.getMessage());
    }

    @ExceptionHandler(ContaBloqueadaException.class)
    ProblemDetail contaBloqueada(ContaBloqueadaException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, e.getMessage());
    }

    @ExceptionHandler(BindException.class)
    ProblemDetail validacao(BindException e) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Dados inválidos");
        Map<String, String> campos = new LinkedHashMap<>();
        e.getFieldErrors().forEach(erro -> campos.putIfAbsent(erro.getField(), erro.getDefaultMessage()));
        problema.setProperty("campos", campos);
        return problema;
    }
}
