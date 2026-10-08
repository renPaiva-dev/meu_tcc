/**
 * Dados públicos em cache (materiais, zonas, pontos) + estado da sincronização e da fila.
 * Sincroniza ao abrir o app, quando a conexão volta e no pull-to-refresh das telas.
 */
import NetInfo from '@react-native-community/netinfo';
import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from 'react';

import { mensagemDeErro } from '../api/cliente';
import { api } from '../api/instancia';
import type { Material, PontoColeta, Zona } from '../api/tipos';
import { lerCache } from '../armazenamento/cache';
import { contarPendentes, lerFila } from '../armazenamento/fila';
import { baixarDadosPublicos, enviarPendentes } from '../sincronizacao/sincronizar';
import { limparFotosOrfas } from '../utilitarios/fotos';
import { useSessao } from './SessaoContexto';

interface ValorDados {
  materiais: Material[];
  zonas: Zona[];
  pontos: PontoColeta[];
  ultimaSincronizacao: string | null;
  sincronizando: boolean;
  /** Mensagem da última falha de sincronização (null se deu certo). */
  erroSincronizacao: string | null;
  /** Itens aguardando envio na fila offline. */
  pendentes: number;
  sincronizar: () => Promise<void>;
  atualizarPendentes: () => Promise<void>;
}

const DadosContexto = createContext<ValorDados | null>(null);

export function DadosProvider({ children }: { children: ReactNode }) {
  const { autenticado, recarregarUsuario } = useSessao();
  const [materiais, setMateriais] = useState<Material[]>([]);
  const [zonas, setZonas] = useState<Zona[]>([]);
  const [pontos, setPontos] = useState<PontoColeta[]>([]);
  const [ultimaSincronizacao, setUltima] = useState<string | null>(null);
  const [sincronizando, setSincronizando] = useState(false);
  const [erroSincronizacao, setErro] = useState<string | null>(null);
  const [pendentes, setPendentes] = useState(0);
  const emAndamento = useRef(false);
  const repetir = useRef(false);

  const atualizarPendentes = useCallback(async () => {
    setPendentes(await contarPendentes());
  }, []);

  const sincronizar = useCallback(async () => {
    if (emAndamento.current) {
      // Ex.: a sessão foi restaurada durante a primeira sincronização; roda de novo ao terminar.
      repetir.current = true;
      return;
    }
    emAndamento.current = true;
    repetir.current = false;
    setSincronizando(true);
    try {
      try {
        const dados = await baixarDadosPublicos(api);
        setMateriais(dados.materiais);
        setZonas(dados.zonas);
        setPontos(dados.pontos);
        setUltima(dados.ultimaSincronizacao);
        setErro(null);
      } catch (e) {
        setErro(mensagemDeErro(e));
      }
      // A fila é enviada mesmo se o download dos dados públicos falhar.
      if (autenticado) {
        const r = await enviarPendentes(api);
        if (r.enviados > 0) await recarregarUsuario();
        if (r.enviados > 0 || r.descartados > 0) {
          const { itens } = await lerFila();
          limparFotosOrfas(itens.flatMap((i) => (i.tipo === 'feedback' && i.dados.foto ? [i.dados.foto.uri] : [])));
        }
      }
    } catch {
      // falha ao processar a fila: os itens continuam pendentes
    } finally {
      await atualizarPendentes();
      emAndamento.current = false;
      setSincronizando(false);
    }
    if (repetir.current) void sincronizarRef.current();
  }, [autenticado, recarregarUsuario, atualizarPendentes]);
  const sincronizarRef = useRef(sincronizar);
  sincronizarRef.current = sincronizar;

  // 1) Carrega o cache local imediatamente (modo offline).
  useEffect(() => {
    void (async () => {
      const cache = await lerCache();
      setMateriais((atual) => (atual.length ? atual : cache.materiais));
      setZonas((atual) => (atual.length ? atual : cache.zonas));
      setPontos((atual) => (atual.length ? atual : cache.pontos));
      setUltima((atual) => atual ?? cache.ultimaSincronizacao);
      await atualizarPendentes();
    })();
  }, [atualizarPendentes]);

  // 2) Sincroniza ao abrir o app e quando a sessão muda (login envia a fila).
  useEffect(() => {
    void sincronizar();
  }, [sincronizar]);

  // 3) Sincroniza quando a conexão volta.
  useEffect(() => {
    let estavaConectado: boolean | null = null;
    const cancelar = NetInfo.addEventListener((estado) => {
      const conectado = estado.isConnected === true && estado.isInternetReachable !== false;
      if (estavaConectado === false && conectado) void sincronizar();
      estavaConectado = conectado;
    });
    return cancelar;
  }, [sincronizar]);

  const valor = useMemo<ValorDados>(
    () => ({
      materiais,
      zonas,
      pontos,
      ultimaSincronizacao,
      sincronizando,
      erroSincronizacao,
      pendentes,
      sincronizar,
      atualizarPendentes,
    }),
    [materiais, zonas, pontos, ultimaSincronizacao, sincronizando, erroSincronizacao, pendentes, sincronizar, atualizarPendentes],
  );

  return <DadosContexto.Provider value={valor}>{children}</DadosContexto.Provider>;
}

export function useDados(): ValorDados {
  const v = useContext(DadosContexto);
  if (!v) throw new Error('useDados fora do DadosProvider');
  return v;
}
