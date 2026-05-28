import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  ProposeQuestionRequest,
  QuestionProposal,
  QuestionProposalHistory,
} from '../models/question-proposal.models';

@Injectable({ providedIn: 'root' })
export class QuestionProposalService {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiBaseUrl;

  propose(req: ProposeQuestionRequest): Observable<QuestionProposal> {
    return this.http.post<QuestionProposal>(`${this.base}/questions/propose`, req);
  }

  mine(): Observable<QuestionProposalHistory> {
    return this.http.get<QuestionProposalHistory>(`${this.base}/questions/proposals/me`);
  }
}
