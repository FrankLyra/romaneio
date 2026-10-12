import { Component, OnInit, ElementRef, ViewChild, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RomaneioService } from './services/romaneio.service';
import { BaseFixa, ImportacaoResponse, Parada, RotaResponse } from './models/romaneio.model';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './app.html',
  styleUrls: ['./app.scss']
})
export class App implements OnInit {
  @ViewChild('fileInput') fileInput!: ElementRef<HTMLInputElement>;

  baseFixa: BaseFixa | null = null;
  importacaoResponse: ImportacaoResponse | null = null;
  rotas: RotaResponse[] = [];
  rotaSelecionada: RotaResponse | null = null;
  rotasEmCalculo = new Set<string>();
  isLoading = false;
  isDragging = false;
  erroMensagem: string | null = null;
  erroCalculoRota: string | null = null;
  arquivoSelecionado: File | null = null;
  activeTab: 'overview' | 'import' | 'routes' | 'history' = 'overview';

  constructor(private romaneioService: RomaneioService, private cdr: ChangeDetectorRef) {}

  ngOnInit(): void {
    this.romaneioService.getBaseFixa().subscribe({ next: base => this.baseFixa = base });
  }

  setActiveTab(tab: 'overview' | 'import' | 'routes' | 'history'): void { this.activeTab = tab; }

  get paradasDaRotaSelecionada(): Parada[] {
    return (this.rotaSelecionada?.paradas || []).filter(p => p.tipo !== 'ORIGEM' && p.tipo !== 'BASE_ORIGEM');
  }
  get motivoSemRotas(): string {
    const avisos = this.importacaoResponse?.avisos || [];
    return avisos.find(aviso => aviso.includes('ROMANEIO_GOOGLE_MAPS_API_KEY')) || avisos[0] || '';
  }

  onDragOver(event: DragEvent): void { event.preventDefault(); event.stopPropagation(); this.isDragging = true; }
  onDragLeave(event: DragEvent): void { event.preventDefault(); event.stopPropagation(); this.isDragging = false; }
  onDrop(event: DragEvent): void {
    event.preventDefault(); event.stopPropagation(); this.isDragging = false;
    if (event.dataTransfer?.files.length) this.processarArquivo(event.dataTransfer.files[0]);
  }
  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (input.files?.length) this.processarArquivo(input.files[0]);
  }
  acionarSeletorArquivo(): void { this.fileInput.nativeElement.click(); }

  processarArquivo(file: File): void {
    const ext = file.name.substring(file.name.lastIndexOf('.')).toLowerCase();
    if (!['.xlsx', '.xls', '.csv'].includes(ext)) {
      this.erroMensagem = 'Selecione uma planilha Excel (.xlsx/.xls) ou CSV (.csv).';
      return;
    }
    this.arquivoSelecionado = file;
    this.erroMensagem = null;
    this.erroCalculoRota = null;
    this.isLoading = true;
    this.rotas = [];
    this.rotaSelecionada = null;
    this.rotasEmCalculo.clear();
    this.importacaoResponse = null;

    this.romaneioService.processarPlanilhaCompleta(file).subscribe({
      next: response => {
        this.isLoading = false;
        this.importacaoResponse = response;
        this.rotas = (response.rotas || []).map((r, index) => {
          const paradas = ((r.paradas || []) as Array<any>).map((p, paradaIndex) => {
            const trechoKm = p.distanciaDoPontoAnteriorKm ?? p.distanciaTrechoKm;
            const acumuladaKm = p.distanciaAcumuladaKm;
            return {
              ...p,
              sequencia: p.sequencia ?? p.ordem ?? paradaIndex,
              tipo: p.tipo === 'ORIGEM' ? 'BASE_ORIGEM' : p.tipo,
              enderecoCompleto: p.enderecoCompleto ?? p.endereco ?? '',
              distanciaTrechoKm: trechoKm,
              distanciaTrechoFormatada: p.distanciaTrechoFormatada ?? (trechoKm == null ? '' : `${trechoKm.toFixed(2)} km`),
              distanciaAcumuladaKm: acumuladaKm,
              distanciaAcumuladaFormatada: p.distanciaAcumuladaFormatada ?? (acumuladaKm == null ? '' : `${acumuladaKm.toFixed(2)} km`),
              encomendas: p.encomendas || [],
              totalVolumes: p.totalVolumes ?? p.quantidadeEncomendas ?? p.encomendas?.length ?? 0
            };
          });
          const identificador = r.identificador || r.letra || String.fromCharCode(65 + index);
          return {
            ...r,
            identificador,
            letra: r.letra || identificador.split('-')[0],
            posicao: index + 1,
            recomendada: index === 0,
            estrategia: r.nome || `Rota ${identificador}`,
            totalParadas: r.quantidadeParadas ?? paradas.filter(p => p.tipo !== 'BASE_ORIGEM').length,
            totalEncomendas: r.quantidadeEncomendas ?? 0,
            paradas
          };
        });
        this.rotaSelecionada = null;
        this.rotasEmCalculo.clear();
        this.erroCalculoRota = null;
        this.cdr.detectChanges();
      },
      error: err => {
        this.isLoading = false;
        this.erroMensagem = err.status === 0
          ? 'Não foi possível conectar ao servidor do Romaneio (porta 8080). Inicie o backend e tente novamente.'
          : err.error?.mensagem || 'Não foi possível processar a planilha. Confira o formato e tente novamente.';
        this.cdr.detectChanges();
      }
    });
  }

  selecionarRota(rota: RotaResponse): void {
    this.rotaSelecionada = rota;
    this.erroCalculoRota = null;
    const identificador = rota.identificador || '';
    if (rota.distanciaTotalKm != null || !identificador || this.rotasEmCalculo.has(identificador)) return;

    const idImportacao = this.importacaoResponse?.idImportacao;
    if (!idImportacao) {
      this.erroCalculoRota = 'A importação atual não está disponível. Importe a planilha novamente.';
      return;
    }

    this.rotasEmCalculo.add(identificador);
    this.romaneioService.calcularRotaSelecionada(idImportacao, identificador).subscribe({
      next: calculada => {
        if (this.importacaoResponse?.idImportacao !== idImportacao) return;
        const rotaAtualizada = this.normalizarRotaCalculada(calculada, rota);
        this.rotas = this.rotas.map(item => item.identificador === identificador ? rotaAtualizada : item);
        if (this.rotaSelecionada?.identificador === identificador) this.rotaSelecionada = rotaAtualizada;
        this.rotasEmCalculo.delete(identificador);
        this.cdr.detectChanges();
      },
      error: err => {
        if (this.importacaoResponse?.idImportacao !== idImportacao) return;
        this.rotasEmCalculo.delete(identificador);
        if (this.rotaSelecionada?.identificador === identificador) {
          this.erroCalculoRota = err.error?.mensagem || 'Não foi possível calcular a rota. Verifique a chave do Google Maps e os endereços.';
        }
        this.cdr.detectChanges();
      }
    });
  }

  private normalizarRotaCalculada(calculada: RotaResponse, anterior: RotaResponse): RotaResponse {
    const paradas = ((calculada.paradas || []) as Array<any>).map((p, paradaIndex) => {
      const trechoKm = p.distanciaDoPontoAnteriorKm ?? p.distanciaTrechoKm;
      const acumuladaKm = p.distanciaAcumuladaKm;
      return {
        ...p,
        sequencia: p.sequencia ?? p.ordem ?? paradaIndex,
        tipo: p.tipo === 'ORIGEM' ? 'BASE_ORIGEM' : p.tipo,
        enderecoCompleto: p.enderecoCompleto ?? p.endereco ?? '',
        distanciaTrechoKm: trechoKm,
        distanciaTrechoFormatada: p.distanciaTrechoFormatada ?? (trechoKm == null ? '' : `${trechoKm.toFixed(2)} km`),
        distanciaAcumuladaKm: acumuladaKm,
        distanciaAcumuladaFormatada: p.distanciaAcumuladaFormatada ?? (acumuladaKm == null ? '' : `${acumuladaKm.toFixed(2)} km`),
        encomendas: p.encomendas || [],
        totalVolumes: p.totalVolumes ?? p.quantidadeEncomendas ?? p.encomendas?.length ?? 0
      };
    });
    return {
      ...anterior,
      ...calculada,
      identificador: calculada.identificador || anterior.identificador,
      letra: calculada.letra || anterior.letra,
      totalParadas: paradas.filter(p => p.tipo !== 'BASE_ORIGEM').length,
      totalEncomendas: calculada.quantidadeEncomendas ?? anterior.totalEncomendas ?? 0,
      paradas
    };
  }

  possuiCodigosRastreio(parada: Parada): boolean { return parada.encomendas.some(encomenda => !!encomenda.codigo?.trim()); }

  sequencesDaParada(parada: Parada): string[] {
    return parada.encomendas
      .map(encomenda => String(encomenda.sequence ?? '').trim())
      .filter(sequence => sequence.length > 0);
  }

  exportarRotaComoCsv(): void {
    if (!this.rotaSelecionada) return;
    const linhas = [['Rota', 'Ponto', 'Endereço', 'Complemento', 'Bairro', 'CEP', 'Objetos', 'Códigos']];
    this.paradasDaRotaSelecionada.forEach((p, index) => {
      const codigos = p.encomendas.map(e => e.codigo).join('; ');
      linhas.push([
        this.rotaSelecionada!.identificador || '', String(index + 1), p.enderecoCompleto || '',
        p.complemento || '', p.bairro || '', p.cep || '', String(p.totalVolumes), codigos
      ]);
    });
    const csv = linhas.map(linha => linha.map(valor => `"${String(valor).replace(/"/g, '""')}"`).join(';')).join('\r\n');
    const url = URL.createObjectURL(new Blob(['\ufeff', csv], { type: 'text/csv;charset=utf-8;' }));
    const link = document.createElement('a');
    link.href = url;
    link.download = `romaneio_${this.rotaSelecionada.identificador || 'rota'}.csv`;
    link.click();
    URL.revokeObjectURL(url);
  }

  reiniciarImportacao(): void {
    this.arquivoSelecionado = null;
    this.importacaoResponse = null;
    this.rotas = [];
    this.rotaSelecionada = null;
    this.erroMensagem = null;
    this.erroCalculoRota = null;
    this.rotasEmCalculo.clear();
    if (this.fileInput) this.fileInput.nativeElement.value = '';
  }
}
