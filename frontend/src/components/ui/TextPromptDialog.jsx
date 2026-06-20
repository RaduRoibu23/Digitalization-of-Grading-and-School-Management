import { createPortal } from 'react-dom'
import useModalDialog from '../../hooks/useModalDialog'

export default function TextPromptDialog({
  open,
  title,
  description,
  label = 'Mesaj',
  placeholder = '',
  confirmLabel = 'Confirma',
  cancelLabel = 'Renunta',
  tone = 'danger',
  value = '',
  onValueChange,
  maxLength = 255,
  requireValue = true,
  loading = false,
  onCancel,
  onConfirm,
}) {
  const dialogRef = useModalDialog({ open, onClose: loading ? undefined : onCancel })

  if (!open) {
    return null
  }

  return createPortal(
    <div className="modalOverlay" role="presentation">
      <div className="modalCard" role="dialog" aria-modal="true" aria-labelledby="text-prompt-title" tabIndex={-1} ref={dialogRef}>
        <div className="modalTitle" id="text-prompt-title">{title}</div>
        <div className="modalText">{description}</div>

        <div className="field" style={{ marginTop: 16 }}>
          <label className="label" htmlFor="text-prompt-input">{label}</label>
          <textarea
            id="text-prompt-input"
            className="input"
            rows={4}
            maxLength={maxLength}
            value={value}
            placeholder={placeholder}
            onChange={(event) => onValueChange?.(event.target.value)}
            disabled={loading}
          />
        </div>

        <div className="modalActions">
          <button className="btn" onClick={onCancel} disabled={loading}>{cancelLabel}</button>
          <button
            className={`btn ${tone}`}
            onClick={() => onConfirm(value.trim())}
            disabled={loading || (requireValue && value.trim().length === 0)}
          >
            {confirmLabel}
          </button>
        </div>
      </div>
    </div>,
    document.body
  )
}
