import { createPortal } from 'react-dom'

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
  loading = false,
  onCancel,
  onConfirm,
}) {
  if (!open) {
    return null
  }

  return createPortal(
    <div className="modalOverlay" role="presentation">
      <div className="modalCard" role="dialog" aria-modal="true" aria-labelledby="text-prompt-title">
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
            disabled={loading || value.trim().length === 0}
          >
            {confirmLabel}
          </button>
        </div>
      </div>
    </div>,
    document.body
  )
}
