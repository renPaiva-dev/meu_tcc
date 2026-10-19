package br.com.reciclathe.admin;

import org.springframework.core.io.Resource;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import br.com.reciclathe.comum.ArmazenamentoFotos;
import br.com.reciclathe.identificacao.IdentificacaoService;
import br.com.reciclathe.identificacao.IdentificacaoService.FiltroFeedback;

/**
 * Feedback dos usuários sobre as classificações. Os erros mostram onde o modelo falha; os acertos
 * com foto consentida também servem de dado rotulado para o próximo retreino.
 */
@Controller
@RequestMapping("/admin/erros-modelo")
public class FeedbackAdminController {

    private final IdentificacaoService identificacoes;
    private final ArmazenamentoFotos fotos;

    public FeedbackAdminController(IdentificacaoService identificacoes, ArmazenamentoFotos fotos) {
        this.identificacoes = identificacoes;
        this.fotos = fotos;
    }

    @GetMapping
    public String listar(@RequestParam(defaultValue = "ERROS") FiltroFeedback filtro,
                         @RequestParam(defaultValue = "false") boolean comFoto,
                         @RequestParam(defaultValue = "0") int pagina, Model model) {
        model.addAttribute("feedbacks", identificacoes.feedbacks(filtro, comFoto,
                PageRequest.of(Math.max(pagina, 0), PontoAdminController.POR_PAGINA)));
        model.addAttribute("filtro", filtro);
        model.addAttribute("comFoto", comFoto);
        model.addAttribute("urlPaginacao", "/admin/erros-modelo?filtro=" + filtro + "&comFoto=" + comFoto);
        return "admin/erros-modelo";
    }

    @GetMapping("/{id}/foto")
    public ResponseEntity<Resource> foto(@PathVariable Long id) {
        Resource foto = fotos.carregar(identificacoes.buscar(id).getFotoArquivo());
        return ResponseEntity.ok()
                .contentType(MediaTypeFactory.getMediaType(foto).orElseThrow())
                .body(foto);
    }
}
