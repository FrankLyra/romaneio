import { Component, OnInit, OnDestroy, ElementRef, ViewChild, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RomaneioService } from './services/romaneio.service';
import { BaseFixa, ImportacaoResponse, Parada, RotaResponse } from './models/romaneio.model';
import * as L from 'leaflet';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './app.html',
  styleUrls: ['./app.scss']
})
export class App implements OnInit, OnDestroy {
  @ViewChild('fileInput') fileInput!: ElementRef<HTMLInputElement>;
  @ViewChild('mapContainer') mapContainer!: ElementRef<HTMLDivElement>;

  // Estado da aplicação
  baseFixa: BaseFixa | null = null;
  importacaoResponse: ImportacaoResponse | null = null;
  rotas: RotaResponse[] = [];
  rotaSelecionada: RotaResponse | null = null;
  paradaSelecionada: Parada | null = null;

  isLoading = false;
  isDragging = false;
  erroMensagem: string | null = null;
  arquivoSelecionado: File | null = null;

  // Mapa Leaflet
  private map: L.Map | null = null;
  private markersLayer: L.LayerGroup | null = null;
  private routePolyline: L.Polyline | null = null;

  constructor(
    private romaneioService: RomaneioService,
    private cdr: ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    this.carregarBaseFixa();
  }

  ngOnDestroy(): void {
    if (this.map) {
      this.map.remove();
    }
  }

  carregarBaseFixa(): void {
    this.romaneioService.getBaseFixa().subscribe({
      next: (base) => {
        this.baseFixa = base;
      },
      error: (err) => {
        console.error('Não foi possível obter dados da base fixa:', err);
      }
    });
  }

  onDragOver(event: DragEvent): void {
    event.preventDefault();
    event.stopPropagation();
    this.isDragging = true;
  }

  onDragLeave(event: DragEvent): void {
    event.preventDefault();
    event.stopPropagation();
    this.isDragging = false;
  }

  onDrop(event: DragEvent): void {
    event.preventDefault();
    event.stopPropagation();
    this.isDragging = false;

    if (event.dataTransfer && event.dataTransfer.files.length > 0) {
      const file = event.dataTransfer.files[0];
      this.processarArquivo(file);
    }
  }

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (input.files && input.files.length > 0) {
      this.processarArquivo(input.files[0]);
    }
  }

  acionarSeletorArquivo(): void {
    this.fileInput.nativeElement.click();
  }

  processarArquivo(file: File): void {
    const ext = file.name.substring(file.name.lastIndexOf('.')).toLowerCase();
    if (!['.xlsx', '.xls', '.csv'].includes(ext)) {
      this.erroMensagem = 'Por favor, selecione um arquivo válido no formato Excel (.xlsx/.xls) ou CSV (.csv).';
      return;
    }

    this.arquivoSelecionado = file;
    this.erroMensagem = null;
    this.isLoading = true;
    this.rotas = [];
    this.rotaSelecionada = null;
    this.paradaSelecionada = null;
    this.importacaoResponse = null;

    this.romaneioService.processarPlanilhaCompleta(file).subscribe({
      next: (response) => {
        this.isLoading = false;
        this.importacaoResponse = response;
        this.rotas = (response.rotas || []).map((r, index) => {
          const letra = r.letra || String.fromCharCode(65 + index);
          return {
            ...r,
            letra: letra,
            posicao: r.posicao || r.id || (index + 1),
            estrategia: r.estrategia || r.nome || `Rota ${letra}`,
            recomendada: r.recomendada !== undefined ? r.recomendada : (r.principal !== undefined ? r.principal : index === 0),
            totalParadas: r.totalParadas || r.quantidadeParadas || (r.paradas ? r.paradas.length : 0),
            totalEncomendas: r.totalEncomendas || r.quantidadeEncomendas || 0
          };
        });

        if (this.rotas.length > 0) {
          // Seleciona a rota principal (Rota A)
          this.selecionarRota(this.rotas[0]);
        }
        this.cdr.detectChanges();
      },
      error: (err) => {
        this.isLoading = false;
        console.error('Erro ao processar planilha:', err);
        this.erroMensagem = err.error?.mensagem || 'Erro ao processar e otimizar rotas da planilha. Verifique o formato do arquivo e tente novamente.';
        this.cdr.detectChanges();
      }
    });
  }

  selecionarRota(rota: RotaResponse): void {
    this.rotaSelecionada = rota;
    this.paradaSelecionada = null;
    this.cdr.detectChanges();

    setTimeout(() => {
      this.atualizarMapaComRota(rota);
    }, 100);
  }

  selecionarParada(parada: Parada): void {
    this.paradaSelecionada = parada;
    if (this.map && parada.latitude && parada.longitude) {
      this.map.setView([parada.latitude, parada.longitude], 16, { animate: true });
    }
  }

  private initMapIfNeeded(): void {
    if (!this.mapContainer) return;

    if (!this.map) {
      const defaultLat = this.baseFixa?.latitude || -22.95156;
      const defaultLon = this.baseFixa?.longitude || -42.98361;

      this.map = L.map(this.mapContainer.nativeElement, {
        zoomControl: true,
        attributionControl: true
      }).setView([defaultLat, defaultLon], 13);

      L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
        maxZoom: 19,
        attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
      }).addTo(this.map);

      this.markersLayer = L.layerGroup().addTo(this.map);
    }
  }

  private atualizarMapaComRota(rota: RotaResponse): void {
    this.initMapIfNeeded();
    if (!this.map || !this.markersLayer) return;

    this.markersLayer.clearLayers();
    if (this.routePolyline) {
      this.map.removeLayer(this.routePolyline);
      this.routePolyline = null;
    }

    const latLngs: L.LatLngExpression[] = [];

    // Adiciona marcadores para cada parada
    rota.paradas.forEach((parada, idx) => {
      if (parada.latitude && parada.longitude) {
        const latLng: [number, number] = [parada.latitude, parada.longitude];
        latLngs.push(latLng);

        let pinClass = 'custom-pin';
        let label = `${parada.sequencia}`;

        if (parada.tipo === 'BASE_ORIGEM') {
          pinClass += ' base-pin';
          label = '🏠';
        } else if (idx === 1) {
          pinClass += ' first-stop';
        } else if (idx === rota.paradas.length - 1) {
          pinClass += ' last-stop';
        }

        const customIcon = L.divIcon({
          className: pinClass,
          html: `<span>${label}</span>`,
          iconSize: [28, 28],
          iconAnchor: [14, 14],
          popupAnchor: [0, -14]
        });

        const popupContent = `
          <div style="font-family: inherit; font-size: 13px; line-height: 1.4;">
            <div style="font-weight: 700; color: #1e293b; margin-bottom: 4px;">
              ${parada.tipo === 'BASE_ORIGEM' ? 'Base Fixa (Origem)' : `Parada #${parada.sequencia}`}
            </div>
            <div style="color: #475569; margin-bottom: 6px;">
              ${parada.enderecoCompleto}
            </div>
            <div style="display: flex; gap: 8px; font-size: 11px; color: #64748b;">
              <span>📦 <strong>${parada.totalVolumes}</strong> ${parada.totalVolumes === 1 ? 'volume' : 'volumes'}</span>
              <span>📏 +${parada.distanciaTrechoFormatada}</span>
            </div>
          </div>
        `;

        const marker = L.marker(latLng, { icon: customIcon })
          .bindPopup(popupContent)
          .addTo(this.markersLayer!);

        marker.on('click', () => {
          this.selecionarParada(parada);
          this.cdr.detectChanges();
        });
      }
    });

    // Traçado da rota poligonal
    if (latLngs.length > 1) {
      this.routePolyline = L.polyline(latLngs, {
        color: '#2563eb',
        weight: 4,
        opacity: 0.8,
        dashArray: '8, 8',
        lineCap: 'round'
      }).addTo(this.map);

      // Ajusta zoom para enquadrar todas as paradas
      const bounds = L.latLngBounds(latLngs);
      this.map.fitBounds(bounds, { padding: [40, 40] });
    }
  }

  exportarRotaComoCsv(): void {
    if (!this.rotaSelecionada) return;

    let csvContent = 'Sequencia,Tipo,Endereco,Bairro,Cidade,UF,CEP,DistanciaTrecho,DistanciaAcumulada,Volumes,CodigosEncomendas\n';

    this.rotaSelecionada.paradas.forEach((p) => {
      const codigos = p.encomendas.map(e => e.codigo).join(';');
      const linha = [
        p.sequencia,
        p.tipo,
        `"${p.enderecoCompleto.replace(/"/g, '""')}"`,
        `"${p.bairro || ''}"`,
        `"${p.cidade || ''}"`,
        `"${p.uf || ''}"`,
        `"${p.cep || ''}"`,
        p.distanciaTrechoFormatada,
        p.distanciaAcumuladaFormatada,
        p.totalVolumes,
        `"${codigos}"`
      ].join(',');
      csvContent += linha + '\n';
    });

    const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.setAttribute('href', url);
    const ident = this.rotaSelecionada.letra || this.rotaSelecionada.posicao;
    link.setAttribute('download', `romaneio_rota_${ident}.csv`);
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
  }

  reiniciarImportacao(): void {
    this.arquivoSelecionado = null;
    this.importacaoResponse = null;
    this.rotas = [];
    this.rotaSelecionada = null;
    this.paradaSelecionada = null;
    this.erroMensagem = null;
    if (this.fileInput) {
      this.fileInput.nativeElement.value = '';
    }
  }
}
