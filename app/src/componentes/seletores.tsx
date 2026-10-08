import type { Material, Zona } from '../api/tipos';
import { CODIGOS_MATERIAL } from '../api/tipos';
import { encontrarMaterial, zonasOuPadrao } from '../utilitarios/materiais';
import { Chip, GrupoChips } from './basicos';

interface PropsZona {
  zonas: Zona[];
  valor: string | null;
  aoMudar: (zona: string) => void;
}

/** Escolha única da zona de Teresina. */
export function SeletorZona({ zonas, valor, aoMudar }: PropsZona) {
  return (
    <GrupoChips rotulo="Zona de Teresina" unico>
      {zonasOuPadrao(zonas).map((z) => (
        <Chip
          key={z.codigo}
          papel="radio"
          rotulo={z.nome}
          selecionado={valor === z.codigo}
          aoPressionar={() => aoMudar(z.codigo)}
        />
      ))}
    </GrupoChips>
  );
}

/** Códigos na ordem do cache (ou a ordem padrão das 7 classes). */
function codigos(materiais: Material[]): string[] {
  return materiais.length > 0 ? materiais.map((m) => m.codigo) : [...CODIGOS_MATERIAL];
}

interface PropsMaterialUnico {
  materiais: Material[];
  valor: string | null;
  aoMudar: (codigo: string | null) => void;
  /** Mostra a opção "Todos" (valor null). */
  permitirTodos?: boolean;
  /** Restringe às opções informadas. */
  opcoes?: string[];
}

export function SeletorMaterial({ materiais, valor, aoMudar, permitirTodos, opcoes }: PropsMaterialUnico) {
  const lista = opcoes ?? codigos(materiais);
  return (
    <GrupoChips rotulo="Material" unico>
      {permitirTodos ? (
        <Chip papel="radio" rotulo="Todos" selecionado={valor == null} aoPressionar={() => aoMudar(null)} />
      ) : null}
      {lista.map((codigo) => {
        const m = encontrarMaterial(materiais, codigo);
        return (
          <Chip
            key={codigo}
            papel="radio"
            rotulo={m.nome}
            cor={m.corHex}
            selecionado={valor === codigo}
            aoPressionar={() => aoMudar(codigo)}
          />
        );
      })}
    </GrupoChips>
  );
}

interface PropsMateriais {
  materiais: Material[];
  valor: string[];
  aoMudar: (codigos: string[]) => void;
}

/** Seleção múltipla (materiais aceitos em um ponto). */
export function SeletorMateriais({ materiais, valor, aoMudar }: PropsMateriais) {
  return (
    <GrupoChips rotulo="Materiais aceitos">
      {codigos(materiais).map((codigo) => {
        const m = encontrarMaterial(materiais, codigo);
        const marcado = valor.includes(codigo);
        return (
          <Chip
            key={codigo}
            rotulo={m.nome}
            cor={m.corHex}
            selecionado={marcado}
            aoPressionar={() => aoMudar(marcado ? valor.filter((c) => c !== codigo) : [...valor, codigo])}
          />
        );
      })}
    </GrupoChips>
  );
}
