package br.com.reciclathe.admin;

import java.io.IOException;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import br.com.reciclathe.comum.RegraNegocioException;
import br.com.reciclathe.zona.ZonaService;

@Controller
@RequestMapping("/admin/zonas")
public class ZonaAdminController {

    private final ZonaService zonas;

    public ZonaAdminController(ZonaService zonas) {
        this.zonas = zonas;
    }

    @GetMapping
    public String listar(Model model) {
        model.addAttribute("zonas", zonas.listar());
        model.addAttribute("bairros", zonas.contarBairrosPorZona());
        return "admin/zonas";
    }

    @PostMapping("/{codigo}")
    public String atualizarTelefone(@PathVariable String codigo, @RequestParam String telefone,
                                    RedirectAttributes redirect) {
        zonas.atualizarTelefone(codigo, telefone);
        redirect.addFlashAttribute("mensagem", "Telefone atualizado.");
        return "redirect:/admin/zonas";
    }

    @PostMapping("/importar")
    public String importar(@RequestParam MultipartFile arquivo, @RequestParam String propriedadeNome,
                           @RequestParam String propriedadeZona, RedirectAttributes redirect) throws IOException {
        try {
            int total = zonas.importarBairros(arquivo.getBytes(), propriedadeNome, propriedadeZona);
            redirect.addFlashAttribute("mensagem", total + " bairros importados.");
        } catch (RegraNegocioException e) {
            redirect.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/admin/zonas";
    }
}
