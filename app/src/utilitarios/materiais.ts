/**
 * Dados de apoio de materiais e zonas usados apenas quando o cache ainda está vazio
 * (primeira abertura sem internet). A fonte oficial é a API (GET /materiais e /zonas).
 */
import type { Material, Zona } from '../api/tipos';

/** Nomes e cores da Resolução CONAMA nº 275/2001 (fallback). */
const PADRAO: Record<string, { nome: string; corHex: string; corNome: string; reciclavel: boolean }> = {
  PLASTICO: { nome: 'Plástico', corHex: '#D32F2F', corNome: 'Vermelho', reciclavel: true },
  PAPEL: { nome: 'Papel e papelão', corHex: '#1565C0', corNome: 'Azul', reciclavel: true },
  METAL: { nome: 'Metal', corHex: '#F9A825', corNome: 'Amarelo', reciclavel: true },
  VIDRO: { nome: 'Vidro', corHex: '#2E7D32', corNome: 'Verde', reciclavel: true },
  ELETRONICO: { nome: 'Eletrônico', corHex: '#EF6C00', corNome: 'Laranja', reciclavel: true },
  ORGANICO: { nome: 'Orgânico', corHex: '#6D4C41', corNome: 'Marrom', reciclavel: false },
  REJEITO: { nome: 'Rejeito', corHex: '#757575', corNome: 'Cinza', reciclavel: false },
};

export function materialPadrao(codigo: string): Material {
  const p = PADRAO[codigo] ?? { nome: codigo, corHex: '#757575', corNome: '—', reciclavel: false };
  return {
    codigo,
    ...p,
    comoPreparar: null,
    ondeDescartar: null,
    observacoes: 'Orientação detalhada indisponível: conecte-se à internet uma vez para baixar os dados.',
    fonte: null,
    atualizadoEm: null,
  };
}

/** Busca o material no cache; se não houver, usa o fallback local. */
export function encontrarMaterial(materiais: Material[], codigo: string): Material {
  return materiais.find((m) => m.codigo === codigo) ?? materialPadrao(codigo);
}

export function nomeMaterial(materiais: Material[], codigo: string): string {
  return encontrarMaterial(materiais, codigo).nome;
}

export const ZONAS_PADRAO: Zona[] = [
  { codigo: 'CENTRO', nome: 'Centro', telefoneColeta: null, fonte: null },
  { codigo: 'NORTE', nome: 'Norte', telefoneColeta: null, fonte: null },
  { codigo: 'SUL', nome: 'Sul', telefoneColeta: null, fonte: null },
  { codigo: 'LESTE', nome: 'Leste', telefoneColeta: null, fonte: null },
  { codigo: 'SUDESTE', nome: 'Sudeste', telefoneColeta: null, fonte: null },
];

export function zonasOuPadrao(zonas: Zona[]): Zona[] {
  return zonas.length > 0 ? zonas : ZONAS_PADRAO;
}

/** Cor de texto (preto ou branco) com melhor contraste sobre o fundo informado. */
export function corTextoSobre(hex: string): '#000000' | '#FFFFFF' {
  const m = /^#?([0-9a-f]{6})$/i.exec(hex.trim());
  if (!m) return '#FFFFFF';
  const n = parseInt(m[1], 16);
  const canal = (v: number) => {
    const c = v / 255;
    return c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4;
  };
  const lum = 0.2126 * canal((n >> 16) & 255) + 0.7152 * canal((n >> 8) & 255) + 0.0722 * canal(n & 255);
  // Contraste com preto = (L+0.05)/0.05; com branco = 1.05/(L+0.05).
  return (lum + 0.05) / 0.05 >= 1.05 / (lum + 0.05) ? '#000000' : '#FFFFFF';
}
