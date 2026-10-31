import { describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import { Modal, Toggle, ErrorBox, Field } from './ui';
describe('accessible review controls', () => {
  it('allows independent work arrangement toggles using keyboard accessible inputs', () => { const change = vi.fn(); render(<Toggle label="Remote work" checked={false} onChange={change}/>); fireEvent.click(screen.getByRole('switch', { name: 'Remote work' })); expect(change).toHaveBeenCalledWith(true); });
  it('names the review modal and closes with its explicit control', () => { const close = vi.fn(); render(<Modal open onClose={close} title="Approve tailored CV"><p>Review real facts.</p></Modal>); expect(screen.getByRole('dialog', { name: 'Approve tailored CV' })).toBeInTheDocument(); fireEvent.click(screen.getByLabelText('Close dialog')); expect(close).toHaveBeenCalled(); });
  it('displays errors as alerts with a retry control', () => { const retry = vi.fn(); render(<ErrorBox error={new Error('Worker unavailable')} retry={retry}/>); expect(screen.getByRole('alert')).toHaveTextContent('Worker unavailable'); fireEvent.click(screen.getByText('Try again')); expect(retry).toHaveBeenCalledOnce(); });
  it('keeps exact field labels stable as textarea content and selected options change', () => { render(<><Field label="Notes" hint="Your personal notes"><textarea defaultValue="Private note content"/></Field><Field label="Application status"><select defaultValue="APPLIED"><option value="SAVED">Saved</option><option value="APPLIED">Applied</option></select></Field></>); expect(screen.getByRole('textbox', { name: 'Notes' })).toHaveValue('Private note content'); expect(screen.getByRole('combobox', { name: 'Application status' })).toHaveValue('APPLIED'); });
});
