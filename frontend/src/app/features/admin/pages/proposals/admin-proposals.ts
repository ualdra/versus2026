import { DatePipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AdminSidebarComponent } from '../../components/sidebar/sidebar';
import { AdminService } from '../../../../core/services/admin.service';
import { PageResponse } from '../../../../core/models/admin.models';
import { ProposalStatus, QuestionProposal } from '../../../../core/models/question-proposal.models';

type StatusFilter = ProposalStatus | '';

@Component({
  selector: 'app-admin-proposals',
  standalone: true,
  imports: [AdminSidebarComponent, DatePipe, FormsModule],
  templateUrl: './admin-proposals.html',
  styleUrl: '../dashboard/admin-dashboard.scss',
})
export class AdminProposals implements OnInit {
  private readonly adminSvc = inject(AdminService);

  page = signal<PageResponse<QuestionProposal> | null>(null);
  statusFilter = signal<StatusFilter>('PENDING');
  loading = signal(true);
  actioningId = signal<string | null>(null);
  rejectingId = signal<string | null>(null);
  rejectReason = '';
  errorMessage = signal<string | null>(null);

  ngOnInit(): void {
    this.load();
  }

  setFilter(status: StatusFilter): void {
    this.statusFilter.set(status);
    this.rejectingId.set(null);
    this.load();
  }

  approve(id: string): void {
    if (this.actioningId()) return;
    this.actioningId.set(id);
    this.errorMessage.set(null);
    this.adminSvc.approveProposal(id).subscribe({
      next: () => {
        this.actioningId.set(null);
        this.load();
      },
      error: (err) => {
        this.actioningId.set(null);
        this.errorMessage.set(err?.error?.message ?? 'No se pudo aprobar la propuesta.');
      },
    });
  }

  startReject(id: string): void {
    this.rejectingId.set(this.rejectingId() === id ? null : id);
    this.rejectReason = '';
    this.errorMessage.set(null);
  }

  reject(id: string): void {
    const reason = this.rejectReason.trim();
    if (!reason || this.actioningId()) return;
    this.actioningId.set(id);
    this.errorMessage.set(null);
    this.adminSvc.rejectProposal(id, reason).subscribe({
      next: () => {
        this.actioningId.set(null);
        this.rejectingId.set(null);
        this.rejectReason = '';
        this.load();
      },
      error: (err) => {
        this.actioningId.set(null);
        this.errorMessage.set(err?.error?.message ?? 'No se pudo rechazar la propuesta.');
      },
    });
  }

  answerLabel(proposal: QuestionProposal): string {
    if (proposal.type === 'BINARY') {
      return `${proposal.proposedAnswer} / ${proposal.alternativeAnswer ?? '-'}`;
    }
    return proposal.proposedAnswer;
  }

  filterPillClass(target: StatusFilter): string {
    return this.statusFilter() === target ? 'vs-pill--info' : 'vs-pill--mute';
  }

  statusPillClass(status: ProposalStatus): string {
    if (status === 'APPROVED') return 'vs-pill vs-pill--ok';
    if (status === 'REJECTED') return 'vs-pill vs-pill--err';
    return 'vs-pill vs-pill--warn';
  }

  private load(): void {
    this.loading.set(true);
    const status = this.statusFilter();
    this.adminSvc.getProposals(status || undefined).subscribe({
      next: (page) => {
        this.page.set(page);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }
}
