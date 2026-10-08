package br.com.reciclathe.admin;

import org.springframework.core.io.Resource;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import br.com.reciclathe.comum.ArmazenamentoFotos;
import br.com.reciclathe.identificacao.IdentificacaoService;

/** Classificações que os usuários apontaram como erradas: insumo para retreinar o modelo. */
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
    public String listar(Model model) {
        model.addAttribute("erros", identificacoes.errosRecentes(200));
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
