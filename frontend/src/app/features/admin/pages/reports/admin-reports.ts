import { Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe, SlicePipe } from '@angular/common';
import { AdminSidebarComponent } from '../../components/sidebar/sidebar';
import { AdminService } from '../../../../core/services/admin.service';
import { AdminReport, PageResponse } from '../../../../core/models/admin.models';

type StatusFilter = 'PENDING' | 'DISMISSED' | 'RESOLVED' | '';

@Component({
  selector: 'app-admin-reports',
  standalone: true,
  imports: [AdminSidebarComponent, DatePipe, SlicePipe],
  templateUrl: './admin-reports.html',
  styleUrl: '../dashboard/admin-dashboard.scss',
})
export class AdminReports implements OnInit {
  private readonly adminSvc = inject(AdminService);

  page = signal<PageResponse<AdminReport> | null>(null);
  statusFilter = signal<StatusFilter>('PENDING');
  loading = signal(true);

  ngOnInit(): void {
    this.load();
  }

  setFilter(s: StatusFilter): void {
    this.statusFilter.set(s);
    this.load();
  }

  private load(): void {
    this.loading.set(true);
    const s = this.statusFilter();
    this.adminSvc.getReports(s || undefined).subscribe({
      next: (p) => {
        this.page.set(p);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  dismiss(id: string): void {
    this.adminSvc.resolveReport(id, 'DISMISS').subscribe({
      next: () => this.load(),
    });
  }

  deleteQuestion(id: string): void {
    this.adminSvc.resolveReport(id, 'DELETE_QUESTION').subscribe({
      next: () => this.load(),
    });
  }

  reasonLabel(r: string): string {
    return (
      {
        WRONG_ANSWER: 'Respuesta incorrecta',
        OUTDATED: 'Datos desactualizados',
        OFFENSIVE: 'Contenido ofensivo',
        OTHER: 'Otro motivo',
      }[r] ?? r
    );
  }

  filterPillClass(target: StatusFilter): string {
    return this.statusFilter() === target ? 'vs-pill--err' : 'vs-pill--mute';
  }
}
