import { Children, cloneElement, createContext, isValidElement, useContext, useId, useState, type ReactElement, type ReactNode } from 'react';
import * as Dialog from '@radix-ui/react-dialog';
import { AlertCircle, Check, Inbox, LoaderCircle, X } from 'lucide-react';
import { clsx } from 'clsx';
import { twMerge } from 'tailwind-merge';

export const cn = (...values: Parameters<typeof clsx>) => twMerge(clsx(...values));
export function Badge({ children, tone = 'neutral' }: { children: ReactNode; tone?: string }) { return <span className={`badge ${tone}`}>{children}</span>; }
export function Spinner({ label = 'Loading your workspace…' }: { label?: string }) { return <div className="loading" role="status"><LoaderCircle className="spin" size={21}/>{label}</div>; }
export function Empty({ title, children, action }: { title: string; children?: ReactNode; action?: ReactNode }) { return <div className="empty"><div className="empty-icon"><Inbox size={25}/></div><h3>{title}</h3><p>{children}</p>{action}</div>; }
export function ErrorBox({ error, retry }: { error: unknown; retry?: () => void }) { return <div role="alert" className="error-box"><AlertCircle size={18}/><div><strong>Something needs attention</strong><p>{error instanceof Error ? error.message : String(error)}</p>{retry && <button className="btn small" onClick={retry}>Try again</button>}</div></div>; }
export function Modal({ open, onClose, title, description, children }: { open: boolean; onClose: () => void; title: string; description?: string; children: ReactNode }) { return <Dialog.Root open={open} onOpenChange={v => !v && onClose()}><Dialog.Portal><Dialog.Overlay className="modal-overlay"/><Dialog.Content className="modal"><div className="modal-heading"><div><Dialog.Title>{title}</Dialog.Title><Dialog.Description>{description || 'Review the information below.'}</Dialog.Description></div><Dialog.Close className="icon-button" aria-label="Close dialog"><X size={20}/></Dialog.Close></div>{children}</Dialog.Content></Dialog.Portal></Dialog.Root>; }
export function Field({ label, children, hint }: { label: string; children: ReactNode; hint?: string }) {
  const generatedId = useId(); let controlId = generatedId; let linked = false;
  const bind = (nodes: ReactNode): ReactNode => Children.map(nodes, node => {
    if (!isValidElement(node)) return node;
    const element = node as ReactElement<Record<string, unknown>>;
    if (!linked && typeof element.type === 'string' && ['input', 'select', 'textarea'].includes(element.type)) {
      linked = true; controlId = String(element.props.id || generatedId);
      return cloneElement(element, { id: controlId, ...(hint ? { 'aria-describedby': `${generatedId}-hint` } : {}) });
    }
    return element.props.children ? cloneElement(element, { children: bind(element.props.children as ReactNode) }) : element;
  });
  const controls = bind(children);
  return <div className="field"><label htmlFor={controlId}>{label}</label>{controls}{hint && <small id={`${generatedId}-hint`}>{hint}</small>}</div>;
}
export function Toggle({ checked, onChange, label }: { checked: boolean; onChange: (v: boolean) => void; label: string }) { return <label className="toggle-label"><span>{label}</span><input type="checkbox" role="switch" checked={checked} onChange={e => onChange(e.target.checked)}/><span className="toggle" aria-hidden="true"/></label>; }
const ToastContext = createContext<(message: string, error?: boolean) => void>(() => {});
export const useToast = () => useContext(ToastContext);
export function ToastProvider({ children }: { children: ReactNode }) { const [toasts, setToasts] = useState<{ id: number; text: string; error: boolean }[]>([]); const notify = (text: string, error = false) => { const id = Date.now(); setToasts(t => [...t, { id, text, error }]); window.setTimeout(() => setToasts(t => t.filter(v => v.id !== id)), 5500); }; return <ToastContext.Provider value={notify}>{children}<div className="toast-stack" aria-live="polite">{toasts.map(t => <div className={`toast ${t.error ? 'toast-error' : ''}`} key={t.id}>{t.error ? <AlertCircle size={18}/> : <Check size={18}/>}<span>{t.text}</span><button aria-label="Dismiss notification" onClick={() => setToasts(v => v.filter(x => x.id !== t.id))}><X size={15}/></button></div>)}</div></ToastContext.Provider>; }
export function PageHeading({ eyebrow, title, children, actions }: { eyebrow?: string; title: string; children?: ReactNode; actions?: ReactNode }) { return <div className="page-heading"><div>{eyebrow && <div className="eyebrow">{eyebrow}</div>}<h1>{title}</h1>{children && <p>{children}</p>}</div>{actions && <div className="page-actions">{actions}</div>}</div>; }
export const human = (v?: string) => v ? v.replaceAll('_', ' ').toLowerCase().replace(/\b\w/g, c => c.toUpperCase()) : 'Not provided';
export const day = (v?: string) => v ? new Date(v).toLocaleDateString(undefined, { day: 'numeric', month: 'short', year: 'numeric' }) : 'Not provided';
export const initials = (v?: string) => (v || '?').split(/\s+/).slice(0, 2).map(s => s[0]).join('').toUpperCase();
