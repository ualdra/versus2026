import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AdminSidebarComponent } from '../../components/sidebar/sidebar';
import { AdminService } from '../../../../core/services/admin.service';
import { AdminSpider } from '../../../../core/models/admin.models';

@Component({
  selector: 'app-admin-spiders',
  standalone: true,
  imports: [AdminSidebarComponent, FormsModule],
  templateUrl: './admin-spiders.html',
  styleUrl: '../dashboard/admin-dashboard.scss',
})
export class AdminSpiders implements OnInit {
  private readonly adminSvc = inject(AdminService);

  allSpiders = signal<AdminSpider[]>([]);
  filter = signal('');
  statusFilter = signal<string>('');
  running = signal<Set<string>>(new Set());
  loading = signal(true);

  filteredSpiders = computed(() => {
    const q = this.filter().toLowerCase();
    const s = this.statusFilter();
    return this.allSpiders().filter(
      (sp) =>
        (!q || sp.name.toLowerCase().includes(q)) &&
        (!s || sp.status === s),
    );
  });

  ngOnInit(): void {
    this.loadSpiders();
  }

  private loadSpiders(): void {
    this.loading.set(true);
    this.adminSvc.getSpiders().subscribe({
      next: (list) => {
        this.allSpiders.set(list);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  triggerRun(name: string): void {
    const current = new Set(this.running());
    current.add(name);
    this.running.set(current);

    this.adminSvc.triggerSpider(name).subscribe({
      next: () => {
        const s = new Set(this.running());
        s.delete(name);
        this.running.set(s);
        this.loadSpiders();
      },
      error: () => {
        const s = new Set(this.running());
        s.delete(name);
        this.running.set(s);
      },
    });
  }

  isRunning(name: string): boolean {
    return this.running().has(name);
  }

  setStatusFilter(s: string): void {
    this.statusFilter.set(this.statusFilter() === s ? '' : s);
  }

  pillClass(status: string): string {
    return { IDLE: 'vs-pill--mute', RUNNING: 'vs-pill--info', FAILED: 'vs-pill--err' }[status] ?? 'vs-pill--mute';
  }

  pillLabel(status: string): string {
    return { IDLE: 'INACTIVA', RUNNING: 'EJECUTANDO', FAILED: 'CAÍDA' }[status] ?? status;
  }

  dotClass(status: string): string {
    return { IDLE: 'idle', RUNNING: 'ok', FAILED: 'err' }[status] ?? 'idle';
  }

  lastRunLabel(spider: AdminSpider): string {
    if (!spider.lastRunAt) return '—';
    const d = new Date(spider.lastRunAt);
    const diff = Math.floor((Date.now() - d.getTime()) / 60000);
    if (diff < 60) return `hace ${diff} min`;
    return `hace ${Math.floor(diff / 60)} h`;
  }
}
