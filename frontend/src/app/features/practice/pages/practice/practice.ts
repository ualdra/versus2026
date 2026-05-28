import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { UpperCasePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { QuestionService } from '../../../../core/services/question.service';
import { PracticeService } from '../../../../core/services/practice.service';
import { QuestionProposalForm } from '../../../questions/components/question-proposal-form/question-proposal-form';
import {
  Question,
  QuestionBinary,
  QuestionNumeric,
  QuestionType,
  PracticeAnswerResponse,
} from '../../../../core/models/game.models';

type Phase = 'setup' | 'loading' | 'question' | 'feedback';

@Component({
  selector: 'app-practice',
  standalone: true,
  imports: [RouterLink, UpperCasePipe, FormsModule, QuestionProposalForm],
  templateUrl: './practice.html',
  styleUrl: './practice.scss',
})
export class Practice implements OnInit {
  private readonly questionSvc = inject(QuestionService);
  private readonly practiceSvc = inject(PracticeService);

  phase = signal<Phase>('setup');
  categories = signal<string[]>([]);
  selectedCategory = signal<string | null>(null);
  selectedType = signal<QuestionType | null>(null);

  question = signal<Question | null>(null);
  pickedOptionId = signal<string | null>(null);
  enteredValue: number | null = null;
  result = signal<PracticeAnswerResponse | null>(null);
  errorMessage = signal<string | null>(null);
  proposalOpen = signal(false);

  streak = signal(0);
  correctCount = signal(0);
  totalAnswered = signal(0);

  accuracy = signal(0);

  ngOnInit(): void {
    this.questionSvc.categories().subscribe({
      next: (cats) => this.categories.set(cats),
      error: () => {},
    });
  }

  selectCategory(cat: string): void {
    this.selectedCategory.set(this.selectedCategory() === cat ? null : cat);
  }

  selectType(type: QuestionType | null): void {
    this.selectedType.set(type);
  }

  start(): void {
    this.loadNext();
  }

  loadNext(): void {
    this.phase.set('loading');
    this.pickedOptionId.set(null);
    this.enteredValue = null;
    this.result.set(null);
    this.errorMessage.set(null);

    const type = this.selectedType() ?? undefined;
    const category = this.selectedCategory() ?? undefined;

    this.questionSvc.random(type, category).subscribe({
      next: (q) => {
        this.question.set(q);
        this.phase.set('question');
      },
      error: () => {
        this.errorMessage.set('No se encontró ninguna pregunta para los filtros seleccionados.');
        this.phase.set('setup');
      },
    });
  }

  pickOption(optionId: string): void {
    if (this.phase() !== 'question') return;
    this.pickedOptionId.set(optionId);
    this.submitBinary(this.question()!.id, optionId);
  }

  submitNumeric(): void {
    if (this.phase() !== 'question') return;
    const val = this.enteredValue;
    if (val === null) return;
    this.submitNumericAnswer(this.question()!.id, val);
  }

  changeFilters(): void {
    this.streak.set(0);
    this.correctCount.set(0);
    this.totalAnswered.set(0);
    this.accuracy.set(0);
    this.question.set(null);
    this.result.set(null);
    this.phase.set('setup');
    this.proposalOpen.set(false);
  }

  toggleProposalForm(): void {
    this.proposalOpen.update((open) => !open);
  }

  asBinary(q: Question): QuestionBinary {
    return q as QuestionBinary;
  }

  asNumeric(q: Question): QuestionNumeric {
    return q as QuestionNumeric;
  }

  optionState(optionId: string): 'idle' | 'correct' | 'wrong' | 'muted' {
    const res = this.result();
    if (!res) return 'idle';
    const picked = this.pickedOptionId();
    if (optionId === res.correctOptionId) return 'correct';
    if (optionId === picked) return 'wrong';
    return 'muted';
  }

  optionClass(optionId: string): string {
    const state = this.optionState(optionId);
    return [
      'vs-bopt',
      state === 'idle' ? 'vs-bopt--idle' : '',
      state === 'correct' ? 'vs-bopt--correct' : '',
      state === 'wrong' ? 'vs-bopt--wrong' : '',
      state === 'muted' ? 'vs-bopt--muted' : '',
    ].filter(Boolean).join(' ');
  }

  private submitBinary(questionId: string, optionId: string): void {
    this.phase.set('loading');
    this.practiceSvc.submitAnswer({ questionId, optionId }).subscribe({
      next: (res) => this.applyResult(res),
      error: () => {
        this.pickedOptionId.set(null);
        this.phase.set('question');
        this.errorMessage.set('No se pudo evaluar la respuesta. Inténtalo de nuevo.');
      },
    });
  }

  private submitNumericAnswer(questionId: string, value: number): void {
    this.phase.set('loading');
    this.practiceSvc.submitAnswer({ questionId, value }).subscribe({
      next: (res) => this.applyResult(res),
      error: () => {
        this.phase.set('question');
        this.errorMessage.set('No se pudo evaluar la respuesta. Inténtalo de nuevo.');
      },
    });
  }

  private applyResult(res: PracticeAnswerResponse): void {
    this.result.set(res);
    this.totalAnswered.update(n => n + 1);
    if (res.correct) {
      this.correctCount.update(n => n + 1);
      this.streak.update(n => n + 1);
    } else {
      this.streak.set(0);
    }
    const total = this.totalAnswered();
    this.accuracy.set(total > 0 ? Math.round((this.correctCount() / total) * 100) : 0);
    this.phase.set('feedback');
  }
}
