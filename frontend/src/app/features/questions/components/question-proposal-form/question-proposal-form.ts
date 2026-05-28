import { HttpErrorResponse } from '@angular/common/http';
import { Component, EventEmitter, OnInit, Output, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { QuestionType } from '../../../../core/models/game.models';
import { QuestionProposal } from '../../../../core/models/question-proposal.models';
import { QuestionProposalService } from '../../../../core/services/question-proposal.service';

@Component({
  selector: 'app-question-proposal-form',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './question-proposal-form.html',
  styleUrl: './question-proposal-form.scss',
})
export class QuestionProposalForm implements OnInit {
  private readonly proposalSvc = inject(QuestionProposalService);

  @Output() submitted = new EventEmitter<QuestionProposal>();

  type: QuestionType = 'BINARY';
  text = '';
  proposedAnswer: string | number = '';
  alternativeAnswer = '';
  category = '';
  sourceUrl = '';

  readonly pendingCount = signal(0);
  readonly pendingLimit = signal(5);
  readonly loadingLimit = signal(true);
  readonly submitting = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly successMessage = signal<string | null>(null);

  ngOnInit(): void {
    this.refreshLimit();
  }

  setType(type: QuestionType): void {
    this.type = type;
    this.proposedAnswer = '';
    this.alternativeAnswer = '';
    this.errorMessage.set(null);
    this.successMessage.set(null);
  }

  setProposedAnswer(value: string | number | null): void {
    this.proposedAnswer = this.toInputText(value);
  }

  submit(): void {
    if (!this.canSubmit) return;
    this.submitting.set(true);
    this.errorMessage.set(null);
    this.successMessage.set(null);

    this.proposalSvc.propose({
      type: this.type,
      text: this.text.trim(),
      proposedAnswer: this.proposedAnswerText,
      alternativeAnswer: this.type === 'BINARY' ? this.alternativeAnswerText : null,
      category: this.category.trim(),
      sourceUrl: this.sourceUrl.trim() || null,
    }).subscribe({
      next: (proposal) => {
        this.submitting.set(false);
        this.pendingCount.update((count) => Math.min(this.pendingLimit(), count + 1));
        this.successMessage.set('Propuesta enviada a moderacion.');
        this.submitted.emit(proposal);
        this.resetFields();
      },
      error: (err: HttpErrorResponse) => {
        this.submitting.set(false);
        this.errorMessage.set(this.errorText(err));
        this.refreshLimit();
      },
    });
  }

  get pendingLimitReached(): boolean {
    return this.pendingCount() >= this.pendingLimit();
  }

  get canSubmit(): boolean {
    const answer = this.proposedAnswerText;
    const alternative = this.alternativeAnswerText;

    return !this.submitting()
      && !this.pendingLimitReached
      && this.text.trim().length > 0
      && answer.length > 0
      && (this.type !== 'BINARY' || alternative.length > 0)
      && (this.type !== 'BINARY' || answer.toLowerCase() !== alternative.toLowerCase())
      && this.category.trim().length > 0
      && (this.type !== 'NUMERIC' || Number.isFinite(Number(answer)));
  }

  get proposedAnswerText(): string {
    return this.toInputText(this.proposedAnswer);
  }

  get alternativeAnswerText(): string {
    return this.toInputText(this.alternativeAnswer);
  }

  get numericAnswerPreview(): string {
    return this.proposedAnswerText || '0';
  }

  private refreshLimit(): void {
    this.loadingLimit.set(true);
    this.proposalSvc.mine().subscribe({
      next: (history) => {
        this.pendingCount.set(history.pendingCount);
        this.pendingLimit.set(history.pendingLimit);
        this.loadingLimit.set(false);
      },
      error: () => this.loadingLimit.set(false),
    });
  }

  private resetFields(): void {
    this.text = '';
    this.category = '';
    this.sourceUrl = '';
    this.proposedAnswer = '';
    this.alternativeAnswer = '';
  }

  private toInputText(value: string | number | null | undefined): string {
    return String(value ?? '').trim();
  }

  private errorText(err: HttpErrorResponse): string {
    if (err?.error?.message) {
      return err.error.message;
    }
    if (typeof err?.error === 'string' && err.error.trim().length > 0) {
      return err.error;
    }
    if (err?.status === 0) {
      return 'No se pudo conectar con el servidor. Comprueba que el backend este arrancado.';
    }
    if (err?.status === 401) {
      return 'Tu sesion ha caducado. Vuelve a iniciar sesion.';
    }
    if (err?.status === 500) {
      return 'El servidor no pudo guardar la propuesta. Reinicia el backend para aplicar el nuevo campo de respuesta alternativa.';
    }
    return 'No se pudo enviar la propuesta.';
  }
}
