/**
 * Sessão do usuário: token JWT (SecureStore) e dados do perfil (AsyncStorage).
 *
 * - `sair()` (saída explícita) e a exclusão da conta apagam a fila offline e as fotos dela.
 * - Token expirado (401) encerra a sessão mas guarda a fila: ela é enviada se o mesmo usuário
 *   entrar de novo e descartada se outro usuário entrar.
 */
import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';

import { ErroApi } from '../api/cliente';
import { aoExpirarSessao, api, definirToken } from '../api/instancia';
import type { DadosCadastro, RespostaToken, Usuario } from '../api/tipos';
import { CHAVES, gravarJson, lerJson, remover } from '../armazenamento/armazenamento';
import { associarFilaAoUsuario, definirUsuarioFila, limparFila } from '../armazenamento/fila';
import { gravarToken, lerToken, removerToken } from '../armazenamento/token';
import { limparFotosOrfas } from '../utilitarios/fotos';

interface ValorSessao {
  carregando: boolean;
  usuario: Usuario | null;
  autenticado: boolean;
  /** true depois que o token expirou (a tela de entrada mostra o aviso). */
  sessaoExpirada: boolean;
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
  const [sessaoExpirada, setSessaoExpirada] = useState(false);

  const guardarUsuario = useCallback(async (u: Usuario) => {
    setUsuario(u);
    await gravarJson(CHAVES.usuario, u);
  }, []);

  const aplicarLogin = useCallback(
    async (r: RespostaToken) => {
      // Fila de outro usuário (sessão anterior expirada) é descartada junto com as fotos.
      if (await associarFilaAoUsuario(r.usuario.id)) limparFotosOrfas([]);
      definirToken(r.token);
      await gravarToken(r.token);
      await guardarUsuario(r.usuario);
      setSessaoExpirada(false);
      setToken(r.token);
    },
    [guardarUsuario],
  );

  /** Encerra a sessão local. `manterFila` = sessão expirada (os itens esperam o mesmo usuário). */
  const encerrar = useCallback(async (manterFila: boolean) => {
    definirToken(null);
    definirUsuarioFila(null);
    setToken(null);
    setUsuario(null);
    await removerToken();
    await remover(CHAVES.usuario);
    if (!manterFila) {
      await limparFila();
      limparFotosOrfas([]);
    }
  }, []);

  const sair = useCallback(() => encerrar(false), [encerrar]);

  const expirar = useCallback(async () => {
    setSessaoExpirada(true);
    await encerrar(true);
  }, [encerrar]);

  const recarregarUsuario = useCallback(async () => {
    try {
      await guardarUsuario(await api.eu());
    } catch {
      // offline ou token expirado (tratado em aoExpirarSessao)
    }
  }, [guardarUsuario]);

  useEffect(() => {
    aoExpirarSessao(() => {
      void expirar();
    });
    return () => aoExpirarSessao(null);
  }, [expirar]);

  // Restaura a sessão salva ao abrir o app (funciona offline).
  useEffect(() => {
    let ativo = true;
    (async () => {
      const [t, u] = await Promise.all([lerToken(), lerJson<Usuario>(CHAVES.usuario)]);
      if (!ativo) return;
      if (t) {
        if (u) await associarFilaAoUsuario(u.id);
        definirToken(t);
        setToken(t);
        setUsuario(u);
      }
      setCarregando(false);
      if (t) {
        try {
          const atualizado = await api.eu();
          if (ativo) await guardarUsuario(atualizado);
          if (ativo && !u) await associarFilaAoUsuario(atualizado.id);
        } catch (e) {
          // O 401 já chamou aoExpirarSessao; aqui só garantimos o mesmo efeito.
          if (e instanceof ErroApi && e.status === 401 && ativo) await expirar();
        }
      }
    })();
    return () => {
      ativo = false;
    };
  }, [guardarUsuario, expirar]);

  const valor = useMemo<ValorSessao>(
    () => ({
      carregando,
      usuario,
      autenticado: token != null,
      sessaoExpirada,
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
    [carregando, usuario, token, sessaoExpirada, aplicarLogin, sair, recarregarUsuario, guardarUsuario],
  );

  return <SessaoContexto.Provider value={valor}>{children}</SessaoContexto.Provider>;
}

export function useSessao(): ValorSessao {
  const v = useContext(SessaoContexto);
  if (!v) throw new Error('useSessao fora do SessaoProvider');
  return v;
}
