/**
 * Tipos do contrato da API ReciclaTHE (Spring Boot, /api/v1).
 * Mantidos em sincronia com os records Java em api/src/main/java/br/com/reciclathe.
 */

export const CODIGOS_MATERIAL = [
  'PLASTICO',
  'PAPEL',
  'METAL',
  'VIDRO',
  'ELETRONICO',
  'ORGANICO',
  'REJEITO',
] as const;
export type CodigoMaterial = (typeof CODIGOS_MATERIAL)[number];

export const CODIGOS_ZONA = ['CENTRO', 'NORTE', 'SUL', 'LESTE', 'SUDESTE'] as const;
export type CodigoZona = (typeof CODIGOS_ZONA)[number];

export const TIPOS_PONTO = [
  'PEV',
  'COOPERATIVA',
  'ECOPONTO',
  'CENTRO_RECICLAGEM',
  'LOGISTICA_REVERSA',
  'OUTRO',
] as const;
export type TipoPonto = (typeof TIPOS_PONTO)[number];

export const DESCRICAO_TIPO_PONTO: Record<TipoPonto, string> = {
  PEV: 'Ponto de Entrega Voluntária',
  COOPERATIVA: 'Cooperativa de reciclagem',
  ECOPONTO: 'Ecoponto',
  CENTRO_RECICLAGEM: 'Centro de reciclagem / sucata',
  LOGISTICA_REVERSA: 'Logística reversa (lojas, farmácias)',
  OUTRO: 'Outro',
};

export interface Usuario {
  id: number;
  nome: string;
  email: string;
  zona: string | null;
  pontos: number;
}

export interface RespostaToken {
  token: string;
  usuario: Usuario;
}

export interface Material {
  codigo: string;
  nome: string;
  corHex: string;
  corNome: string;
  reciclavel: boolean;
  comoPreparar: string | null;
  ondeDescartar: string | null;
  observacoes: string | null;
  fonte: string | null;
  atualizadoEm: string | null;
}

export interface Zona {
  codigo: string;
  nome: string;
  telefoneColeta: string | null;
  fonte: string | null;
}

export interface LocalizacaoZona {
  zona: Zona;
  bairro: string;
}

export interface PontoColeta {
  id: number;
  nome: string;
  tipo: TipoPonto;
  endereco: string;
  latitude: number;
  longitude: number;
  horario: string | null;
  telefone: string | null;
  fonte: string | null;
  materiais: string[];
  distanciaMetros: number | null;
}

export interface Identificacao {
  id: number;
  material: string;
  confianca: number;
  zona: string | null;
  feedbackCorreto: boolean | null;
  materialCorrigido: string | null;
  criadoEm: string;
}

export interface EventoPontuacao {
  tipo: 'SUGESTAO_APROVADA' | 'CHECKIN' | 'FEEDBACK' | string;
  pontos: number;
  referenciaId: number | null;
  criadoEm: string;
}

export interface Historico {
  identificacoes: Identificacao[];
  pontos: EventoPontuacao[];
}

export interface ItemRanking {
  posicao: number;
  nome: string;
  pontos: number;
}

export interface DadosCadastro {
  nome: string;
  email: string;
  senha: string;
  zona?: string | null;
}

export interface DadosIdentificacao {
  material: string;
  confianca: number;
  zona?: string | null;
  versaoModelo?: string | null;
}

/** Arquivo local (foto) a ser enviado em multipart. */
export interface ArquivoLocal {
  uri: string;
  nome: string;
  tipo: 'image/jpeg' | 'image/png' | 'image/webp';
}

export interface DadosFeedback {
  correto: boolean;
  materialCorreto?: string | null;
  consentimentoFoto: boolean;
  foto?: ArquivoLocal | null;
}

export interface DadosSugestao {
  nome: string;
  tipo: TipoPonto;
  endereco: string;
  latitude: number;
  longitude: number;
  horario?: string;
  telefone?: string;
  fonte?: string;
  materiais: string[];
  foto?: ArquivoLocal | null;
}
