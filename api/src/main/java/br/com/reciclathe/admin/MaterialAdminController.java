package br.com.reciclathe.admin;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import br.com.reciclathe.material.Material;
import br.com.reciclathe.material.MaterialService;

/** Edição das orientações de descarte; o app recebe a mudança na próxima sincronização. */
@Controller
@RequestMapping("/admin/materiais")
public class MaterialAdminController {

    private final MaterialService materiais;

    public MaterialAdminController(MaterialService materiais) {
        this.materiais = materiais;
    }

    @GetMapping
    public String listar(Model model) {
        model.addAttribute("materiais", materiais.listar());
        return "admin/materiais";
    }

    @GetMapping("/{codigo}")
    public String editar(@PathVariable String codigo, Model model) {
        Material m = materiais.buscar(codigo);
        model.addAttribute("material", m);
        model.addAttribute("orientacao",
                new Orientacao(m.getComoPreparar(), m.getOndeDescartar(), m.getObservacoes(), m.getFonte()));
        return "admin/material-form";
    }

    @PostMapping("/{codigo}")
    public String salvar(@PathVariable String codigo, @Valid @ModelAttribute("orientacao") Orientacao orientacao,
                         BindingResult erros, Model model, RedirectAttributes redirect) {
        if (erros.hasErrors()) {
            model.addAttribute("material", materiais.buscar(codigo));
            model.addAttribute("erros", Validacao.mensagens(erros));
            return "admin/material-form";
        }
        materiais.atualizarOrientacao(codigo, orientacao.comoPreparar(), orientacao.ondeDescartar(),
                orientacao.observacoes(), orientacao.fonte());
        redirect.addFlashAttribute("mensagem", "Orientação atualizada.");
        return "redirect:/admin/materiais";
    }

    public record Orientacao(@NotBlank String comoPreparar, @NotBlank String ondeDescartar, String observacoes,
                             @NotBlank String fonte) {
    }
}
