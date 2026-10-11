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
   * Processa a planilha retornando o resultado completo com metadados, validações e as rotas
   */
  processarPlanilhaCompleta(file: File): Observable<ImportacaoResponse> {
    const formData = new FormData();
    formData.append('file', file);
    return this.http.post<ImportacaoResponse>(`${this.baseUrl}/processar-completo`, formData);
  }
}
