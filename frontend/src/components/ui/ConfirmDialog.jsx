import { createPortal } from 'react-dom'
import useModalDialog from '../../hooks/useModalDialog'

export default function ConfirmDialog({
  open,
  title,
  description,
  confirmLabel = 'Confirma',
  cancelLabel = 'Renunta',
  tone = 'danger',
  onConfirm,
  onCancel,
  loading = false,
}) {
  const dialogRef = useModalDialog({ open, onClose: loading ? undefined : onCancel })

  if (!open) {
    return null
  }

  return createPortal(
    <div className="modalOverlay" role="presentation">
      <div className="modalCard" role="dialog" aria-modal="true" aria-labelledby="confirm-dialog-title" tabIndex={-1} ref={dialogRef}>
        <div className="modalTitle" id="confirm-dialog-title">{title}</div>
        <div className="modalText">{description}</div>
        <div className="modalActions">
          <button className="btn" onClick={onCancel} disabled={loading}>{cancelLabel}</button>
          <button className={`btn ${tone}`} onClick={onConfirm} disabled={loading}>{confirmLabel}</button>
        </div>
      </div>
    </div>,
    document.body
  )
}
