export interface Coordenada {
  latitude: number;
  longitude: number;
}

export interface Encomenda {
  codigo: string;
  cep?: string;
  logradouro?: string;
  numero?: string;
  complemento?: string;
  bairro?: string;
  cidade?: string;
  uf?: string;
  corredor?: string;
  latitude?: number;
  longitude?: number;
  enderecoCompleto?: string;
}

export interface Parada {
  sequencia: number;
  tipo: 'BASE_ORIGEM' | 'ENTREGA' | string;
  cep?: string;
  logradouro?: string;
  numero?: string;
  bairro?: string;
  cidade?: string;
  uf?: string;
  enderecoCompleto: string;
  latitude: number;
  longitude: number;
  distanciaTrechoKm: number;
  distanciaTrechoFormatada: string;
  distanciaAcumuladaKm: number;
  distanciaAcumuladaFormatada: string;
  encomendas: Encomenda[];
  totalVolumes: number;
}

export interface RotaResponse {
  id?: number;
  letra?: string;
  nome?: string;
  descricao?: string;
  principal?: boolean;
  posicao?: number;
  estrategia?: string;
  recomendada?: boolean;
  distanciaTotalKm: number;
  distanciaTotalFormatada: string;
  tempoEstimadoFormatado: string;
  tempoEstimadoMinutos: number;
  quantidadeParadas?: number;
  totalParadas?: number;
  quantidadeEncomendas?: number;
  totalEncomendas?: number;
  paradas: Parada[];
  origem?: Parada;
  ultimaParada?: Parada;
}

export interface ImportacaoResponse {
  sucesso: boolean;
  mensagem: string;
  nomeArquivoOriginal: string;
  totalLinhasLidas: number;
  totalValidas: number;
  totalInvalidas: number;
  totalDuplicadas: number;
  totalParadasAgrupadas: number;
  avisos: string[];
  rotas: RotaResponse[];
}

export interface BaseFixa {
  nome: string;
  enderecoCompleto: string;
  logradouro: string;
  numero: string;
  bairro: string;
  cidade: string;
  uf: string;
  cep: string;
  latitude: number;
  longitude: number;
}
