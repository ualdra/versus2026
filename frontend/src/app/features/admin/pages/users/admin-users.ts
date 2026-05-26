import { Component, OnInit, inject, signal } from '@angular/core';
import { SlicePipe } from '@angular/common';
import { AdminSidebarComponent } from '../../components/sidebar/sidebar';
import { AvatarComponent } from '../../../../shared/components/ui/avatar/avatar.component';
import { AdminService } from '../../../../core/services/admin.service';
import { AdminUser } from '../../../../core/models/admin.models';
import { Role } from '../../../../core/models/auth.models';

@Component({
  selector: 'app-admin-users',
  standalone: true,
  imports: [AdminSidebarComponent, SlicePipe, AvatarComponent],
  templateUrl: './admin-users.html',
  styleUrl: '../dashboard/admin-dashboard.scss',
})
export class AdminUsers implements OnInit {
  private readonly adminService = inject(AdminService);

  users = signal<AdminUser[]>([]);
  totalElements = signal(0);
  totalPages = signal(0);
  page = signal(0);
  readonly size = 20;

  search = signal('');
  roleFilter = signal<Role | null>(null);
  activeFilter = signal<boolean | null>(null);

  editingRoleId = signal<string | null>(null);

  private searchTimeout: ReturnType<typeof setTimeout> | null = null;

  ngOnInit(): void {
    this.load();
  }

  onSearch(event: Event): void {
    const value = (event.target as HTMLInputElement).value;
    this.search.set(value);
    this.page.set(0);
    if (this.searchTimeout) clearTimeout(this.searchTimeout);
    this.searchTimeout = setTimeout(() => this.load(), 300);
  }

  onRoleFilter(event: Event): void {
    const value = (event.target as HTMLSelectElement).value;
    this.roleFilter.set(value ? (value as Role) : null);
    this.page.set(0);
    this.load();
  }

  onActiveFilter(event: Event): void {
    const value = (event.target as HTMLSelectElement).value;
    this.activeFilter.set(value === '' ? null : value === 'true');
    this.page.set(0);
    this.load();
  }

  goToPage(p: number): void {
    this.page.set(p);
    this.load();
  }

  toggleStatus(user: AdminUser): void {
    this.adminService.updateUserStatus(user.id, !user.isActive).subscribe(updated => {
      this.users.update(list => list.map(u => u.id === updated.id ? updated : u));
    });
  }

  startEditRole(userId: string): void {
    this.editingRoleId.set(userId);
  }

  applyRole(user: AdminUser, event: Event): void {
    const role = (event.target as HTMLSelectElement).value as Role;
    this.editingRoleId.set(null);
    if (role === user.role) return;
    this.adminService.updateUserRole(user.id, role).subscribe(updated => {
      this.users.update(list => list.map(u => u.id === updated.id ? updated : u));
    });
  }

  cancelEditRole(): void {
    this.editingRoleId.set(null);
  }

  roleColor(r: Role): string {
    const colors: Partial<Record<Role, string>> = { ADMIN: 'var(--vs-accent-red)', MODERATOR: 'var(--vs-accent-gold)' };
    return colors[r] ?? 'var(--vs-accent-blue)';
  }

  roleBg(r: Role): string {
    const bgs: Partial<Record<Role, string>> = { ADMIN: 'rgba(230,57,70,0.12)', MODERATOR: 'rgba(244,197,66,0.12)' };
    return bgs[r] ?? 'rgba(67,97,238,0.12)';
  }

  get pages(): number[] {
    return Array.from({ length: this.totalPages() }, (_, i) => i);
  }

  private load(): void {
    this.adminService.listUsers({
      page: this.page(),
      size: this.size,
      search: this.search() || undefined,
      role: this.roleFilter() ?? undefined,
      active: this.activeFilter() ?? undefined,
    }).subscribe(result => {
      this.users.set(result.items);
      this.totalElements.set(result.totalElements);
      this.totalPages.set(result.totalPages);
    });
  }
}
