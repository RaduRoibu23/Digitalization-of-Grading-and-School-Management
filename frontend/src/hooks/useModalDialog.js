import { useEffect, useRef } from 'react'

const FOCUSABLE_SELECTOR = [
  'a[href]',
  'button:not([disabled])',
  'textarea:not([disabled])',
  'input:not([disabled])',
  'select:not([disabled])',
  '[tabindex]:not([tabindex="-1"])',
].join(', ')

/**
 * Accessibility helper for modal dialogs rendered in a portal.
 * Handles Escape-to-close, initial focus, a focus trap (Tab / Shift+Tab),
 * and restoring focus to the previously focused element on close.
 *
 * Returns a ref that must be attached to the dialog container element.
 */
export default function useModalDialog({ open, onClose }) {
  const dialogRef = useRef(null)
  const onCloseRef = useRef(onClose)

  useEffect(() => {
    onCloseRef.current = onClose
  })

  useEffect(() => {
    if (!open) {
      return undefined
    }

    const dialogNode = dialogRef.current
    const previouslyFocused = document.activeElement

    const getFocusable = () => {
      if (!dialogNode) {
        return []
      }
      return Array.from(dialogNode.querySelectorAll(FOCUSABLE_SELECTOR)).filter(
        (element) => element.offsetParent !== null || element === document.activeElement
      )
    }

    const focusable = getFocusable()
    if (focusable.length > 0) {
      focusable[0].focus()
    } else if (dialogNode) {
      dialogNode.focus()
    }

    const handleKeyDown = (event) => {
      if (event.key === 'Escape') {
        event.stopPropagation()
        onCloseRef.current?.()
        return
      }

      if (event.key !== 'Tab' || !dialogNode) {
        return
      }

      const items = getFocusable()
      if (items.length === 0) {
        event.preventDefault()
        dialogNode.focus()
        return
      }

      const firstItem = items[0]
      const lastItem = items[items.length - 1]
      const active = document.activeElement

      if (event.shiftKey) {
        if (active === firstItem || !dialogNode.contains(active)) {
          event.preventDefault()
          lastItem.focus()
        }
      } else if (active === lastItem || !dialogNode.contains(active)) {
        event.preventDefault()
        firstItem.focus()
      }
    }

    document.addEventListener('keydown', handleKeyDown, true)

    return () => {
      document.removeEventListener('keydown', handleKeyDown, true)
      if (previouslyFocused && typeof previouslyFocused.focus === 'function') {
        previouslyFocused.focus()
      }
    }
  }, [open])

  return dialogRef
}
