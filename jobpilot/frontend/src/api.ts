export type Fact = { id: string; section: string; text: string };
export type Candidate = { id: string; name: string; summary: string; careerGoals?: string[]; verifiedFacts: Fact[]; pendingFacts: Fact[]; documents: { documentId: string; originalName?: string; createdAt?: string }[] };
export type Job = { id: string; title: string; company: string; location: string; description: string; url: string; linkedinJobId?: string; workArrangement?: string; employmentType?: string; seniority?: string; salaryMin?: number; salaryMax?: number; currency?: string; salaryPeriod?: string; salarySource?: string; publishedAt?: string; availability: string; saved: boolean; createdAt: string; matchScore?: number; analysis?: Record<string, unknown>; applicationStatus?: string };
export type CV = { id: string; jobId: string; status: string; original: string; preview: string; changes: unknown[]; documentId?: string; createdAt?: string; factIds?: string[]; candidateSnapshot?: { verifiedFacts: Fact[] } };
export type Recruiter = { id: string; jobId?: string; name: string; position?: string; company: string; profileUrl?: string; evidenceUrl: string; confidence: string; verifiedAt?: string };
export type Application = { id: string; jobId: string; title?: string; company?: string; status: string; notes?: string; followUpDate?: string; interviewDate?: string; interviewNotes?: string; salaryExpectation?: string; offerDetails?: string };
export type Message = { id: string; type: string; text: string; tone?: string };
export const statuses = ['DISCOVERED','SAVED','CV_PREPARED','APPLIED','RECRUITER_CONTACTED','INTERVIEW','OFFER','REJECTED','WITHDRAWN'];
export async function api<T = unknown>(path: string, options: RequestInit = {}): Promise<T> {
  const response = await fetch(`/api${path}`, { signal: AbortSignal.timeout(180_000), ...options, headers: { ...(options.body instanceof FormData ? {} : { 'Content-Type': 'application/json' }), ...options.headers } });
  if (!response.ok) { let message = `Request failed (${response.status})`; try { const body = await response.json(); message = body.detail || body.message || body.error || message; } catch { /* Retain HTTP status for non-JSON errors. */ } throw new Error(message); }
  if (response.status === 204) return undefined as T;
  return response.json() as Promise<T>;
}
export const send = <T = unknown>(path: string, body: unknown, method = 'POST') => api<T>(path, { method, body: JSON.stringify(body) });
export async function runTask(path: string, body: unknown = {}, method = 'POST'): Promise<unknown> {
  const task = await send<{ id: string; status: string }>(path, body, method);
  if (!task.id || !['PENDING', 'QUEUED', 'RUNNING'].includes(task.status)) return task;
  for (let attempt = 0; attempt < 120; attempt++) {
    await new Promise(resolve => window.setTimeout(resolve, 1000));
    const result = await api<{ status: string; result?: unknown; error?: string }>(`/tasks/${task.id}`);
    if (['COMPLETED', 'SUCCEEDED', 'SUCCESS'].includes(result.status)) return result.result;
    if (['FAILED', 'CANCELLED'].includes(result.status)) throw new Error(result.error || 'The task did not complete.');
  }
  throw new Error('The task is still running. Refresh the page to check its result.');
}
export function salary(job: Job): string { if (job.salaryMin == null && job.salaryMax == null) return 'Salary not disclosed'; const format = (n: number) => n.toLocaleString(); return `${job.salaryMin != null ? format(job.salaryMin) : ''}${job.salaryMin != null && job.salaryMax != null ? ' – ' : job.salaryMin != null ? '+' : 'Up to '}${job.salaryMax != null ? format(job.salaryMax) : ''} ${job.currency || 'PLN'} / ${job.salaryPeriod?.toLowerCase() || 'month'}${job.salarySource && job.salarySource !== 'ADVERTISED' ? ' · estimated' : ''}`; }
