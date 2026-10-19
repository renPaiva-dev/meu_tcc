package br.com.reciclathe.admin;

import java.security.Principal;
import java.util.List;

import jakarta.validation.Valid;

import org.springframework.core.io.Resource;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import br.com.reciclathe.comum.ArmazenamentoFotos;
import br.com.reciclathe.comum.RegraNegocioException;
import br.com.reciclathe.material.Material;
import br.com.reciclathe.material.MaterialService;
import br.com.reciclathe.ponto.DadosPonto;
import br.com.reciclathe.ponto.ImportacaoOsmService;
import br.com.reciclathe.ponto.PontoColeta;
import br.com.reciclathe.ponto.PontoService;
import br.com.reciclathe.ponto.StatusPonto;
import br.com.reciclathe.ponto.TipoPonto;

@Controller
@RequestMapping("/admin/pontos")
public class PontoAdminController {

    static final int POR_PAGINA = 50;

    private final PontoService pontos;
    private final MaterialService materiais;
    private final ArmazenamentoFotos fotos;
    private final ImportacaoOsmService importacaoOsm;

    public PontoAdminController(PontoService pontos, MaterialService materiais, ArmazenamentoFotos fotos,
                                ImportacaoOsmService importacaoOsm) {
        this.pontos = pontos;
        this.materiais = materiais;
        this.fotos = fotos;
        this.importacaoOsm = importacaoOsm;
    }

    @GetMapping
    public String listar(@RequestParam(required = false) StatusPonto status,
                         @RequestParam(defaultValue = "0") int pagina, Model model) {
        model.addAttribute("pontos", pontos.listar(status, PageRequest.of(Math.max(pagina, 0), POR_PAGINA)));
        model.addAttribute("status", status);
        model.addAttribute("pendentes", pontos.contar(StatusPonto.PENDENTE));
        model.addAttribute("urlPaginacao", status == null ? "/admin/pontos" : "/admin/pontos?status=" + status);
        return "admin/pontos";
    }

    @GetMapping("/novo")
    public String novo(Model model) {
        return formulario(model, null, new DadosPonto(null, TipoPonto.PEV, null, null, null, null, null, null, List.of()));
    }

    @GetMapping("/{id}")
    public String editar(@PathVariable Long id, Model model) {
        PontoColeta p = pontos.buscar(id);
        DadosPonto dados = new DadosPonto(p.getNome(), p.getTipo(), p.getEndereco(), p.getLatitude(),
                p.getLongitude(), p.getHorario(), p.getTelefone(), p.getFonte(),
                p.getMateriais().stream().map(Material::getCodigo).toList());
        return formulario(model, p, dados);
    }

    @PostMapping({"", "/{id}"})
    public String salvar(@PathVariable(required = false) Long id, @Valid @ModelAttribute("dados") DadosPonto dados,
                         BindingResult erros, Model model, RedirectAttributes redirect) {
        if (erros.hasErrors()) {
            model.addAttribute("erros", Validacao.mensagens(erros));
            return formulario(model, id == null ? null : pontos.buscar(id), dados);
        }
        PontoColeta salvo = pontos.salvar(id, dados);
        redirect.addFlashAttribute("mensagem", "Ponto \"" + salvo.getNome() + "\" salvo.");
        return "redirect:/admin/pontos";
    }

    @PostMapping("/{id}/aprovar")
    public String aprovar(@PathVariable Long id, Principal admin, RedirectAttributes redirect) {
        try {
            pontos.aprovar(id, admin.getName());
            redirect.addFlashAttribute("mensagem", "Ponto aprovado e publicado no mapa.");
        } catch (RegraNegocioException e) {
            redirect.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/admin/pontos?status=PENDENTE";
    }

    @PostMapping("/importar-osm")
    public String importarOsm(RedirectAttributes redirect) {
        try {
            ImportacaoOsmService.Resultado r = importacaoOsm.importar();
            redirect.addFlashAttribute("mensagem", ("OpenStreetMap: %d novos pontos, %d pendentes atualizados, "
                    + "%d já revisados ou excluídos (mantidos como estão), %d fora de Teresina ignorados. "
                    + "Os novos ficam pendentes: revise e informe os materiais antes de aprovar.")
                    .formatted(r.novos(), r.atualizados(), r.jaRevisados(), r.foraDaArea()));
        } catch (ImportacaoOsmService.FalhaImportacaoException e) {
            redirect.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/admin/pontos?status=PENDENTE";
    }

    @PostMapping("/{id}/recusar")
    public String recusar(@PathVariable Long id, @RequestParam(defaultValue = "") String motivo, Principal admin,
                          RedirectAttributes redirect) {
        try {
            pontos.recusar(id, motivo, admin.getName());
            redirect.addFlashAttribute("mensagem", "Ponto recusado.");
        } catch (RegraNegocioException e) {
            redirect.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/admin/pontos?status=PENDENTE";
    }

    @PostMapping("/{id}/excluir")
    public String excluir(@PathVariable Long id, RedirectAttributes redirect) {
        pontos.excluir(id);
        redirect.addFlashAttribute("mensagem", "Ponto excluído.");
        return "redirect:/admin/pontos";
    }

    @GetMapping("/{id}/foto")
    public ResponseEntity<Resource> foto(@PathVariable Long id) {
        Resource foto = fotos.carregar(pontos.buscar(id).getFotoArquivo());
        return ResponseEntity.ok()
                .contentType(MediaTypeFactory.getMediaType(foto).orElseThrow())
                .body(foto);
    }

    private String formulario(Model model, PontoColeta ponto, DadosPonto dados) {
        model.addAttribute("ponto", ponto);
        model.addAttribute("dados", dados);
        model.addAttribute("tipos", TipoPonto.values());
        model.addAttribute("todosMateriais", materiais.listar());
        model.addAttribute("motivoMaximo", PontoService.MOTIVO_MAXIMO);
        return "admin/ponto-form";
    }
}
