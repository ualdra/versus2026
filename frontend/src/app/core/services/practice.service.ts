import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { PracticeAnswerRequest, PracticeAnswerResponse } from '../models/game.models';

@Injectable({ providedIn: 'root' })
export class PracticeService {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiBaseUrl;

  submitAnswer(req: PracticeAnswerRequest): Observable<PracticeAnswerResponse> {
    return this.http.post<PracticeAnswerResponse>(`${this.base}/practice/answer`, req);
  }
}
