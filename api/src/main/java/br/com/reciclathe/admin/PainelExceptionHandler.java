package br.com.reciclathe.admin;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.ModelAndView;

import br.com.reciclathe.comum.RecursoNaoEncontradoException;

/** Erros das telas do painel viram páginas HTML, e não JSON como na API. */
@ControllerAdvice(basePackageClasses = PainelExceptionHandler.class)
class PainelExceptionHandler {

    @ExceptionHandler(RecursoNaoEncontradoException.class)
    ModelAndView naoEncontrado(RecursoNaoEncontradoException e) {
        ModelAndView pagina = new ModelAndView("error/404", HttpStatus.NOT_FOUND);
        pagina.addObject("detalhe", e.getMessage());
        return pagina;
    }
}
