/** Sessão do usuário: token JWT e dados do perfil, persistidos no AsyncStorage. */
import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';

import { ErroApi } from '../api/cliente';
import { aoExpirarSessao, api, definirToken } from '../api/instancia';
import type { DadosCadastro, RespostaToken, Usuario } from '../api/tipos';
import { CHAVES, gravarJson, lerJson, remover } from '../armazenamento/armazenamento';
import { limparFila } from '../armazenamento/fila';

interface ValorSessao {
  carregando: boolean;
  usuario: Usuario | null;
  autenticado: boolean;
  entrar: (email: string, senha: string) => Promise<void>;
  cadastrar: (dados: DadosCadastro) => Promise<void>;
  sair: () => Promise<void>;
  /** Recarrega o perfil (pontos) do servidor; ignora falhas de rede. */
  recarregarUsuario: () => Promise<void>;
  alterarZona: (zona: string | null) => Promise<void>;
  excluirConta: () => Promise<void>;
}

const SessaoContexto = createContext<ValorSessao | null>(null);

export function SessaoProvider({ children }: { children: ReactNode }) {
  const [carregando, setCarregando] = useState(true);
  const [token, setToken] = useState<string | null>(null);
  const [usuario, setUsuario] = useState<Usuario | null>(null);

  const guardarUsuario = useCallback(async (u: Usuario) => {
    setUsuario(u);
    await gravarJson(CHAVES.usuario, u);
  }, []);

  const aplicarLogin = useCallback(
    async (r: RespostaToken) => {
      definirToken(r.token);
      setToken(r.token);
      await gravarJson(CHAVES.token, r.token);
      await guardarUsuario(r.usuario);
    },
    [guardarUsuario],
  );

  const sair = useCallback(async () => {
    definirToken(null);
    setToken(null);
    setUsuario(null);
    await remover(CHAVES.token, CHAVES.usuario);
    // Itens pendentes pertencem ao usuário que saiu; não devem ser enviados com outra conta.
    await limparFila();
  }, []);

  const recarregarUsuario = useCallback(async () => {
    try {
      await guardarUsuario(await api.eu());
    } catch {
      // offline ou token expirado (tratado em aoExpirarSessao)
    }
  }, [guardarUsuario]);

  useEffect(() => {
    aoExpirarSessao(() => {
      void sair();
    });
    return () => aoExpirarSessao(null);
  }, [sair]);

  // Restaura a sessão salva ao abrir o app (funciona offline).
  useEffect(() => {
    let ativo = true;
    (async () => {
      const [t, u] = await Promise.all([lerJson<string>(CHAVES.token), lerJson<Usuario>(CHAVES.usuario)]);
      if (!ativo) return;
      if (t) {
        definirToken(t);
        setToken(t);
        setUsuario(u);
      }
      setCarregando(false);
      if (t) {
        try {
          const atualizado = await api.eu();
          if (ativo) await guardarUsuario(atualizado);
        } catch (e) {
          if (e instanceof ErroApi && e.status === 401 && ativo) await sair();
        }
      }
    })();
    return () => {
      ativo = false;
    };
  }, [guardarUsuario, sair]);

  const valor = useMemo<ValorSessao>(
    () => ({
      carregando,
      usuario,
      autenticado: token != null,
      entrar: async (email, senha) => aplicarLogin(await api.entrar(email.trim(), senha)),
      cadastrar: async (dados) => aplicarLogin(await api.cadastrar({ ...dados, email: dados.email.trim() })),
      sair,
      recarregarUsuario,
      alterarZona: async (zona) => guardarUsuario(await api.alterarZona(zona)),
      excluirConta: async () => {
        await api.excluirConta();
        await sair();
      },
    }),
    [carregando, usuario, token, aplicarLogin, sair, recarregarUsuario, guardarUsuario],
  );

  return <SessaoContexto.Provider value={valor}>{children}</SessaoContexto.Provider>;
}

export function useSessao(): ValorSessao {
  const v = useContext(SessaoContexto);
  if (!v) throw new Error('useSessao fora do SessaoProvider');
  return v;
}
