import { QuestionType } from './game.models';

export type ProposalStatus = 'PENDING' | 'APPROVED' | 'REJECTED';

export interface ProposeQuestionRequest {
  type: QuestionType;
  text: string;
  proposedAnswer: string;
  alternativeAnswer?: string | null;
  category: string;
  sourceUrl?: string | null;
}

export interface QuestionProposal {
  id: string;
  authorId: string;
  type: QuestionType;
  text: string;
  proposedAnswer: string;
  alternativeAnswer: string | null;
  category: string;
  status: ProposalStatus;
  reviewedBy: string | null;
  reviewedAt: string | null;
  rejectReason: string | null;
  sourceUrl: string | null;
  createdAt: string;
}

export interface QuestionProposalHistory {
  pendingCount: number;
  pendingLimit: number;
  proposals: QuestionProposal[];
}
