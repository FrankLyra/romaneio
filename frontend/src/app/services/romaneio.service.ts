import { Injectable } from '@angular/core';
import { HttpClient, HttpEvent, HttpEventType, HttpRequest } from '@angular/common/http';
import { Observable } from 'rxjs';
import { BaseFixa, ImportacaoResponse, RotaResponse } from '../models/romaneio.model';

@Injectable({
  providedIn: 'root'
})
export class RomaneioService {
  private readonly baseUrl = 'http://localhost:8080/api/romaneios';

  constructor(private http: HttpClient) {}

  /**
   * Obtém os dados da base fixa de origem (Itaipuaçu / Maricá)
   */
  getBaseFixa(): Observable<BaseFixa> {
    return this.http.get<BaseFixa>(`${this.baseUrl}/base-fixa`);
  }

  /**
   * Importa a planilha e retorna diretamente a lista das 5 rotas calculadas
   */
  importarPlanilha(file: File): Observable<RotaResponse[]> {
    const formData = new FormData();
    formData.append('file', file);
    return this.http.post<RotaResponse[]>(`${this.baseUrl}/importar`, formData);
  }

  /**
   * Importa sem geocodificar; classifica rotas pelo número de pontos de entrega únicos.
   */
  processarPlanilhaCompleta(file: File): Observable<ImportacaoResponse> {
    const formData = new FormData();
    formData.append('file', file);
    return this.http.post<ImportacaoResponse>(`${this.baseUrl}/processar-completo`, formData);
  }

  calcularRotaSelecionada(idImportacao: string, identificador: string): Observable<RotaResponse> {
    const rota = encodeURIComponent(identificador);
    return this.http.post<RotaResponse>(`${this.baseUrl}/${idImportacao}/rotas/${rota}/calcular`, {});
  }
}
