import {
  Component,
  ElementRef,
  OnInit,
  ViewChild,
  computed,
  inject,
  signal,
} from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { TopbarComponent } from '../../../../shared/components/layout/topbar/topbar';
import { AvatarComponent } from '../../../../shared/components/ui/avatar/avatar.component';
import { AuthService } from '../../../../core/services/auth.service';
import { UserService } from '../../../../core/services/user.service';
import { StatsService } from '../../../../core/services/stats.service';
import { RankingService } from '../../../../core/services/ranking.service';
import { AchievementService } from '../../../../core/services/achievement.service';
import { QuestionProposalService } from '../../../../core/services/question-proposal.service';
import { Achievement } from '../../../../core/models/achievement.models';
import { UserMe } from '../../../../core/models/auth.models';
import { RankingSummary } from '../../../../core/models/ranking.models';
import { QuestionProposal } from '../../../../core/models/question-proposal.models';
import {
  GameMode,
  MatchHistoryItem,
  PlayerStatsOverview,
} from '../../../../core/models/game.models';
import { MatchDetailModal } from './match-detail-modal/match-detail-modal';
import { QuestionProposalForm } from '../../../questions/components/question-proposal-form/question-proposal-form';

const MODE_LABEL: Record<GameMode, string> = {
  SURVIVAL: 'Supervivencia',
  PRECISION: 'Precisión',
  BINARY_DUEL: 'Duelo binario',
  PRECISION_DUEL: 'Duelo de precisión',
  SABOTAGE: 'Sabotaje',
};

const SINGLEPLAYER_MODES: GameMode[] = ['SURVIVAL', 'PRECISION'];
const MULTIPLAYER_MODES: GameMode[] = ['BINARY_DUEL', 'PRECISION_DUEL', 'SABOTAGE'];

@Component({
  selector: 'app-profile',
  standalone: true,
  imports: [RouterLink, TopbarComponent, MatchDetailModal, DatePipe, AvatarComponent, QuestionProposalForm],
  templateUrl: './profile.html',
  styleUrl: './profile.scss',
})
export class Profile implements OnInit {
  private readonly auth = inject(AuthService);
  private readonly users = inject(UserService);
  private readonly statsApi = inject(StatsService);
  private readonly rankingApi = inject(RankingService);
  private readonly achievementsApi = inject(AchievementService);
  private readonly proposalApi = inject(QuestionProposalService);

  @ViewChild('chartCanvas') chartCanvas!: ElementRef<HTMLCanvasElement>;

  readonly me = signal<UserMe | null>(null);
  readonly overview = signal<PlayerStatsOverview | null>(null);
  readonly history = signal<MatchHistoryItem[]>([]);
  readonly historyPage = signal(0);
  readonly historyHasMore = signal(false);
  readonly historyLoading = signal(false);
  readonly selectedMatchId = signal<string | null>(null);
  readonly modeFilter = signal<GameMode | undefined>(undefined);
  readonly achievements = signal<Achievement[]>([]);
  readonly competitiveRankings = signal<RankingSummary[]>([]);
  readonly competitiveLoading = signal(false);
  readonly proposals = signal<QuestionProposal[]>([]);
  readonly proposalsPendingCount = signal(0);
  readonly proposalsPendingLimit = signal(5);
  readonly proposalsLoading = signal(false);

  private chartDrawn = false;

  readonly username = computed(() => this.me()?.username ?? this.auth.user()?.username ?? '—');

  readonly joined = computed(() => {
    const iso = this.me()?.createdAt;
    if (!iso) return '—';
    return new Date(iso).toLocaleDateString('es-ES', { month: 'long', year: 'numeric' });
  });

  readonly totalGames = computed(() =>
    this.overview()?.byMode.reduce((s, x) => s + x.gamesPlayed, 0) ?? 0
  );

  readonly bestStreakOverall = computed(() =>
    this.overview()?.byMode.reduce((m, x) => Math.max(m, x.bestStreak), 0) ?? 0
  );

  readonly favoriteMode = computed(() => {
    const fm = this.overview()?.favoriteMode;
    return fm ? (MODE_LABEL[fm as GameMode] ?? fm) : null;
  });

  readonly totalPlayTime = computed(() => {
    const secs = this.overview()?.totalPlayTimeSeconds ?? 0;
    const h = Math.floor(secs / 3600);
    const m = Math.floor((secs % 3600) / 60);
    if (h > 0) return `${h}h ${m}m`;
    if (m > 0) return `${m}m`;
    return '<1m';
  });

  readonly unlockedAchievements = computed(() =>
    this.achievements().filter((a) => a.unlocked)
  );

  readonly unlockedCount = computed(() => this.unlockedAchievements().length);
  readonly totalAchievements = computed(() => this.achievements().length);

  readonly singleplayerStats = computed(() =>
    (this.overview()?.byMode ?? [])
      .filter((s) => SINGLEPLAYER_MODES.includes(s.mode))
      .map((s) => ({
        mode: MODE_LABEL[s.mode] ?? s.mode,
        games: s.gamesPlayed,
        wins: s.gamesWon,
        acc: s.gamesPlayed === 0 ? '—' : `${s.winRate}%`,
        best: s.bestStreak,
        avgScore: s.avgScore ?? '—',
        avgDev: s.avgDeviation != null ? `${s.avgDeviation.toFixed(1)}%` : '—',
      }))
  );

  readonly multiplayerStats = computed(() =>
    (this.overview()?.byMode ?? [])
      .filter((s) => MULTIPLAYER_MODES.includes(s.mode))
      .map((s) => ({
        mode: MODE_LABEL[s.mode] ?? s.mode,
        games: s.gamesPlayed,
        wins: s.gamesWon,
        acc: s.gamesPlayed === 0 ? '—' : `${s.winRate}%`,
        best: s.bestStreak,
        avgScore: s.avgScore ?? '—',
        avgDev: '—',
      }))
  );

  readonly competitiveRows = computed(() =>
    this.competitiveRankings()
      .filter((ranking) => MULTIPLAYER_MODES.includes(ranking.mode))
      .map((ranking) => {
        const total = ranking.wins + ranking.losses;
        return {
          mode: MODE_LABEL[ranking.mode] ?? ranking.mode,
          rating: ranking.rating,
          winRate: total === 0 ? '0%' : `${Math.round((ranking.wins / total) * 100)}%`,
          record: `${ranking.wins}/${ranking.losses}`,
          winStreak: ranking.winStreak,
          rank: ranking.rank,
        };
      })
  );

  readonly chartScores = computed(() =>
    this.history()
      .slice()
      .reverse()
      .map((h) => h.score)
  );

  ngOnInit(): void {
    this.users.me().subscribe({
      next: (u) => {
        this.me.set(u);
        this.loadCompetitiveRankings(u.id);
      },
      error: () => {
        const fallbackId = this.auth.user()?.id;
        if (fallbackId) this.loadCompetitiveRankings(fallbackId);
      },
    });
    this.statsApi.mine().subscribe({
      next: (o) => this.overview.set(o),
      error: () => {},
    });
    this.achievementsApi.list().subscribe({
      next: (list) => this.achievements.set(list ?? []),
      error: () => this.achievements.set([]),
    });
    this.loadProposals();
    this.loadHistory(0);
  }

  loadProposals(): void {
    this.proposalsLoading.set(true);
    this.proposalApi.mine().subscribe({
      next: (history) => {
        this.proposals.set(history.proposals ?? []);
        this.proposalsPendingCount.set(history.pendingCount);
        this.proposalsPendingLimit.set(history.pendingLimit);
        this.proposalsLoading.set(false);
      },
      error: () => this.proposalsLoading.set(false),
    });
  }

  private loadCompetitiveRankings(userId: string): void {
    this.competitiveLoading.set(true);
    this.rankingApi.userRanking(userId).subscribe({
      next: (data) => {
        this.competitiveRankings.set(data.rankings ?? []);
        this.competitiveLoading.set(false);
      },
      error: () => this.competitiveLoading.set(false),
    });
  }

  loadHistory(page: number): void {
    this.historyLoading.set(true);
    this.statsApi.history(page, 10, this.modeFilter()).subscribe({
      next: (r) => {
        if (page === 0) {
          this.history.set(r.content);
        } else {
          this.history.update((prev) => [...prev, ...r.content]);
        }
        this.historyPage.set(r.number);
        this.historyHasMore.set(!r.last);
        this.historyLoading.set(false);
        setTimeout(() => this.drawChart(), 0);
      },
      error: () => this.historyLoading.set(false),
    });
  }

  loadMore(): void {
    this.loadHistory(this.historyPage() + 1);
  }

  applyFilter(mode: GameMode | undefined): void {
    this.modeFilter.set(mode);
    this.loadHistory(0);
  }

  openDetail(matchId: string): void {
    this.selectedMatchId.set(matchId);
  }

  closeDetail(): void {
    this.selectedMatchId.set(null);
  }

  modeLabel(mode: GameMode): string {
    return MODE_LABEL[mode] ?? mode;
  }

  resultClass(result: string | null): string {
    if (result === 'WIN') return 'vs-result--win';
    if (result === 'LOSS') return 'vs-result--loss';
    return 'vs-result--draw';
  }

  achievementIcon(iconKey: string): string {
    const labels: Record<string, string> = {
      first: '1', win: 'W', streak5: '5', streak10: '10', streak20: '20',
      precision: 'P', sniper: 'P', target: 'P', survival: 'S', shield: 'S',
      perfect: 'S', duel: 'D', arena: 'D', sabotage: 'SB', friends: 'F',
      invite: 'I', collector: 'C', lock: '?',
    };
    return labels[iconKey] ?? '?';
  }

  achievementDate(achievement: Achievement): string {
    if (!achievement.unlockedAt) return 'Bloqueado';
    return new Date(achievement.unlockedAt).toLocaleDateString('es-ES', {
      day: '2-digit',
      month: 'short',
      year: 'numeric',
    });
  }

  proposalStatusLabel(status: QuestionProposal['status']): string {
    return (
      {
        PENDING: 'Pendiente',
        APPROVED: 'Aprobada',
        REJECTED: 'Rechazada',
      }[status] ?? status
    );
  }

  proposalStatusClass(status: QuestionProposal['status']): string {
    if (status === 'APPROVED') return 'vs-proposal-status vs-proposal-status--approved';
    if (status === 'REJECTED') return 'vs-proposal-status vs-proposal-status--rejected';
    return 'vs-proposal-status vs-proposal-status--pending';
  }

  proposalAnswerLabel(proposal: QuestionProposal): string {
    if (proposal.type === 'BINARY') {
      return `${proposal.proposedAnswer} / ${proposal.alternativeAnswer ?? '-'}`;
    }
    return proposal.proposedAnswer;
  }

  private drawChart(): void {
    const scores = this.chartScores();
    if (scores.length < 2) return;
    if (!this.chartCanvas?.nativeElement) return;

    const canvas = this.chartCanvas.nativeElement;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    const W = canvas.clientWidth || 400;
    const H = canvas.clientHeight || 100;
    canvas.width = W;
    canvas.height = H;

    const pad = 12;
    const minY = Math.min(...scores);
    const maxY = Math.max(...scores);
    const rangeY = maxY - minY || 1;
    const n = scores.length;

    ctx.clearRect(0, 0, W, H);

    ctx.strokeStyle = 'rgba(255,255,255,0.06)';
    ctx.lineWidth = 1;
    ctx.beginPath();
    ctx.moveTo(0, H / 2);
    ctx.lineTo(W, H / 2);
    ctx.stroke();

    ctx.strokeStyle = '#f0c060';
    ctx.lineWidth = 2;
    ctx.lineJoin = 'round';
    ctx.beginPath();
    scores.forEach((v, i) => {
      const x = pad + (i / (n - 1)) * (W - 2 * pad);
      const y = H - pad - ((v - minY) / rangeY) * (H - 2 * pad);
      if (i === 0) ctx.moveTo(x, y);
      else ctx.lineTo(x, y);
    });
    ctx.stroke();

    scores.forEach((v, i) => {
      const x = pad + (i / (n - 1)) * (W - 2 * pad);
      const y = H - pad - ((v - minY) / rangeY) * (H - 2 * pad);
      ctx.beginPath();
      ctx.arc(x, y, 3, 0, Math.PI * 2);
      ctx.fillStyle = '#f0c060';
      ctx.fill();
    });

    this.chartDrawn = true;
  }
}
