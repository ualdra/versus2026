import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Question, QuestionBinary, QuestionNumeric, QuestionType } from '../models/game.models';

@Injectable({ providedIn: 'root' })
export class QuestionService {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiBaseUrl;

  random(type?: QuestionType, category?: string): Observable<Question> {
    let params = new HttpParams();
    if (type) params = params.set('type', type);
    if (category) params = params.set('category', category);
    return this.http.get<Question>(`${this.base}/questions/random`, { params });
  }

  randomBinary(category?: string): Observable<QuestionBinary> {
    return this.random('BINARY', category) as Observable<QuestionBinary>;
  }

  randomNumeric(category?: string): Observable<QuestionNumeric> {
    return this.random('NUMERIC', category) as Observable<QuestionNumeric>;
  }

  categories(): Observable<string[]> {
    return this.http.get<string[]>(`${this.base}/questions/categories`);
  }
}
