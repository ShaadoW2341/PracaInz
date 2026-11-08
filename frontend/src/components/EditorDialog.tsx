import { useEffect, useRef, type ReactNode } from 'react';

export function EditorDialog({ label, busy = false, onClose, children }: {
  label: string; busy?: boolean; onClose: () => void; children: ReactNode;
}) {
  const dialog = useRef<HTMLDialogElement>(null);
  useEffect(() => {
    const element = dialog.current!;
    const previousFocus = document.activeElement;
    const previousOverflow = document.body.style.overflow;
    element.showModal();
    document.body.style.overflow = 'hidden';
    element.querySelector<HTMLElement>('input:not([type="hidden"]), select, textarea')?.focus();
    return () => {
      element.close();
      document.body.style.overflow = previousOverflow;
      if (previousFocus instanceof HTMLElement && previousFocus.isConnected) previousFocus.focus();
    };
  }, []);

  return <dialog ref={dialog} className="editor-dialog" aria-label={label} aria-busy={busy}
    onCancel={event => { event.preventDefault(); if (!busy) onClose(); }}>
    <div className="editor-dialog-top"><span>MYCOMAP / TWÓJ LEŚNY DZIENNIK</span>
      <button type="button" className="dialog-close" aria-label="Zamknij okno" disabled={busy} onClick={onClose}>×</button>
    </div>
    {children}
  </dialog>;
}
