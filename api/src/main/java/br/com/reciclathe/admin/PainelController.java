package br.com.reciclathe.admin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import br.com.reciclathe.material.MaterialService;
import br.com.reciclathe.zona.ZonaService;

@Controller
public class PainelController {

    private static final Set<Integer> PERIODOS = Set.of(7, 30, 90, 365);

    private final PainelService painel;
    private final MaterialService materiais;
    private final ZonaService zonas;

    public PainelController(PainelService painel, MaterialService materiais, ZonaService zonas) {
        this.painel = painel;
        this.materiais = materiais;
        this.zonas = zonas;
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/")
    public String raiz() {
        return "redirect:/admin";
    }

    @GetMapping("/admin")
    public String painel(@RequestParam(defaultValue = "30") int dias, Model model) {
        int periodo = PERIODOS.contains(dias) ? dias : 30;
        Map<String, Map<String, Long>> matriz = new HashMap<>();
        List<PainelService.Celula> celulas = painel.materiaisPorZona(periodo);
        celulas.forEach(c -> matriz.computeIfAbsent(c.zona(), z -> new HashMap<>()).put(c.material(), c.total()));

        model.addAttribute("dias", periodo);
        model.addAttribute("resumo", painel.resumo(periodo));
        model.addAttribute("materiais", materiais.listar());
        model.addAttribute("zonas", zonas.listar());
        model.addAttribute("matriz", matriz);
        model.addAttribute("semZona", matriz.containsKey("SEM_ZONA"));
        model.addAttribute("acuracia", painel.acuraciaEmUso(periodo));
        return "admin/painel";
    }
}
