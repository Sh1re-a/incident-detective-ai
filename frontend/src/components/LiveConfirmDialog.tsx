import { useRef } from "react";

import { useDialogFocusTrap } from "../lib/useDialogFocusTrap";

interface LiveConfirmDialogProps {
  open: boolean;
  onCancel: () => void;
  onConfirm: () => void;
}

export function LiveConfirmDialog({
  open,
  onCancel,
  onConfirm,
}: LiveConfirmDialogProps) {
  const cancelButtonRef = useRef<HTMLButtonElement>(null);
  const dialogRef = useRef<HTMLElement>(null);

  useDialogFocusTrap(open, dialogRef, cancelButtonRef, onCancel);

  if (!open) {
    return null;
  }

  return (
    <div className="modal-backdrop" role="presentation" onMouseDown={onCancel}>
      <section
        ref={dialogRef}
        className="modal-panel live-confirm"
        role="dialog"
        aria-modal="true"
        aria-labelledby="live-dialog-title"
        aria-describedby="live-dialog-description"
        onMouseDown={(event) => event.stopPropagation()}
      >
        <p className="section-kicker">Real model call</p>
        <h2 id="live-dialog-title">Run a live AI investigation?</h2>
        <p id="live-dialog-description" className="modal-intro">
          Gemini will choose read-only tools over synthetic data. The run has a
          45-second limit and may use a small amount of API credit.
        </p>

        <ul className="confirm-facts">
          <li>No company data leaves this demo.</li>
          <li>No rollback or remediation can be executed.</li>
          <li>The Java backend verifies the answer after the model finishes.</li>
          <li>A failure is never silently replaced by a replay.</li>
        </ul>

        <div className="modal-actions">
          <button
            ref={cancelButtonRef}
            className="button secondary-button"
            type="button"
            onClick={onCancel}
          >
            Cancel
          </button>
          <button
            className="button primary-button"
            type="button"
            onClick={onConfirm}
          >
            Confirm live run
          </button>
        </div>
      </section>
    </div>
  );
}
