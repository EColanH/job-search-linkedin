import { afterEach, describe, expect, it, vi } from 'vitest';
import { api, runTask, salary, type Job } from './api';

afterEach(() => { vi.unstubAllGlobals(); vi.useRealTimers(); });
describe('API errors and request contracts', () => {
  it('surfaces the server validation error instead of treating a rejected import as success', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({ detail: 'Only LinkedIn job URLs are accepted' }), { status: 400 })));
    await expect(api('/jobs/import', { method: 'POST', body: '{}' })).rejects.toThrow('Only LinkedIn');
  });
  it('leaves multipart headers to the browser so file upload boundaries remain valid', async () => {
    const fetch = vi.fn().mockResolvedValue(new Response(JSON.stringify({ pendingFacts: [] }), { status: 200 })); vi.stubGlobal('fetch', fetch);
    const body = new FormData(); body.append('file', new File(['synthetic'], 'candidate.pdf'));
    await api('/candidates/cv', { method: 'POST', body });
    expect(fetch.mock.calls[0][1].headers).not.toHaveProperty('Content-Type');
  });
  it('polls asynchronous jobs and surfaces their underlying failure', async () => {
    vi.useFakeTimers(); const fetch = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({ id: 'task-1', status: 'QUEUED' }), { status: 202 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ status: 'FAILED', error: 'No verified facts' }), { status: 200 }));
    vi.stubGlobal('fetch', fetch); const task = runTask('/jobs/1/tailored-cv'); const check = expect(task).rejects.toThrow('No verified facts'); await vi.advanceTimersByTimeAsync(1100); await check;
  });
  it('returns completed output after polling without pretending the queued response is complete', async () => {
    vi.useFakeTimers(); vi.stubGlobal('fetch', vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({ id: 'task-2', status: 'RUNNING' }), { status: 202 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ status: 'COMPLETED', result: { score: 47 } }), { status: 200 })));
    const task = runTask('/jobs/1/analyze'); await vi.advanceTimersByTimeAsync(1100); expect(await task).toEqual({ score: 47 });
  });
});
describe('salary presentation', () => {
  const base = { currency: 'PLN', salaryPeriod: 'MONTHLY' } as Job;
  it('never invents undisclosed salary', () => expect(salary(base)).toBe('Salary not disclosed'));
  it('handles an advertised upper bound without making up a lower bound', () => expect(salary({ ...base, salaryMax: 18000 })).toMatch(/Up to .*18.*000 PLN/));
  it('marks estimated salary distinctly', () => expect(salary({ ...base, salaryMin: 15000, salarySource: 'ESTIMATED' })).toContain('estimated'));
});
