package br.com.reciclathe.admin;

import java.security.Principal;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.data.domain.PageRequest;
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

import br.com.reciclathe.comum.ConflitoException;
import br.com.reciclathe.comum.RegraNegocioException;
import br.com.reciclathe.usuario.UsuarioService;

/** Contas: lista, novos administradores, bloqueio e a troca da senha do próprio administrador. */
@Controller
@RequestMapping("/admin")
public class UsuarioAdminController {

    private final UsuarioService usuarios;

    public UsuarioAdminController(UsuarioService usuarios) {
        this.usuarios = usuarios;
    }

    @GetMapping("/usuarios")
    public String listar(@RequestParam(defaultValue = "0") int pagina, Principal admin, Model model) {
        return tela(model, admin, pagina, new NovoAdmin(null, null, null));
    }

    @PostMapping("/usuarios")
    public String criarAdmin(@Valid @ModelAttribute("novoAdmin") NovoAdmin novo, BindingResult erros,
                             Principal admin, Model model, RedirectAttributes redirect) {
        if (erros.hasErrors()) {
            model.addAttribute("erros", Validacao.mensagens(erros));
            return tela(model, admin, 0, novo);
        }
        try {
            usuarios.criarAdmin(novo.nome(), novo.email(), novo.senha());
        } catch (ConflitoException | RegraNegocioException e) {
            model.addAttribute("erros", List.of(e.getMessage()));
            return tela(model, admin, 0, novo);
        }
        redirect.addFlashAttribute("mensagem", "Administrador " + novo.email() + " criado.");
        return "redirect:/admin/usuarios";
    }

    @PostMapping("/usuarios/{id}/bloquear")
    public String bloquear(@PathVariable Long id, Principal admin, RedirectAttributes redirect) {
        return alterarAtivo(id, false, admin, redirect);
    }

    @PostMapping("/usuarios/{id}/desbloquear")
    public String desbloquear(@PathVariable Long id, Principal admin, RedirectAttributes redirect) {
        return alterarAtivo(id, true, admin, redirect);
    }

    @GetMapping("/conta")
    public String conta() {
        return "admin/conta";
    }

    @PostMapping("/conta/senha")
    public String trocarSenha(@RequestParam String senhaAtual, @RequestParam String novaSenha,
                              @RequestParam String confirmacao, Principal admin, RedirectAttributes redirect) {
        try {
            if (!novaSenha.equals(confirmacao)) {
                throw new RegraNegocioException("A confirmação não confere com a nova senha");
            }
            usuarios.trocarSenha(admin.getName(), senhaAtual, novaSenha);
            redirect.addFlashAttribute("mensagem", "Senha alterada.");
        } catch (RegraNegocioException e) {
            redirect.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/admin/conta";
    }

    private String alterarAtivo(Long id, boolean ativo, Principal admin, RedirectAttributes redirect) {
        try {
            var usuario = usuarios.alterarAtivo(id, ativo, admin.getName());
            redirect.addFlashAttribute("mensagem",
                    usuario.getEmail() + (ativo ? " desbloqueado." : " bloqueado: não entra mais no app nem no painel."));
        } catch (RegraNegocioException e) {
            redirect.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/admin/usuarios";
    }

    private String tela(Model model, Principal admin, int pagina, NovoAdmin novo) {
        model.addAttribute("usuarios", usuarios.listar(PageRequest.of(Math.max(pagina, 0), PontoAdminController.POR_PAGINA)));
        model.addAttribute("emailLogado", admin.getName());
        model.addAttribute("novoAdmin", novo);
        model.addAttribute("senhaMinima", UsuarioService.SENHA_MINIMA);
        return "admin/usuarios";
    }

    public record NovoAdmin(@NotBlank @Size(max = 120) String nome,
                            @NotBlank @Email @Size(max = 160) String email,
                            @NotBlank @Size(min = UsuarioService.SENHA_MINIMA, max = 72) String senha) {
    }
}
