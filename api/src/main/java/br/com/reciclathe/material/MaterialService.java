package br.com.reciclathe.material;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.reciclathe.comum.RecursoNaoEncontradoException;
import br.com.reciclathe.comum.RegraNegocioException;

@Service
public class MaterialService {

    private final MaterialRepository materiais;

    public MaterialService(MaterialRepository materiais) {
        this.materiais = materiais;
    }

    public List<Material> listar() {
        return materiais.findAllByOrderByOrdemAsc();
    }

    public Material buscar(String codigo) {
        return materiais.findById(codigo)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Material não encontrado: " + codigo));
    }

    /** Resolve uma lista de códigos, falhando se algum não existir. */
    public Set<Material> buscarTodos(Collection<String> codigos) {
        List<Material> encontrados = materiais.findAllById(codigos);
        if (encontrados.size() != new HashSet<>(codigos).size()) {
            throw new RegraNegocioException("Material desconhecido em " + codigos);
        }
        return new HashSet<>(encontrados);
    }

    @Transactional
    public void atualizarOrientacao(String codigo, String comoPreparar, String ondeDescartar,
                                    String observacoes, String fonte) {
        buscar(codigo).atualizarOrientacao(comoPreparar, ondeDescartar, observacoes, fonte);
    }
}
