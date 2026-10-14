import React, { useLayoutEffect, useRef, useState } from 'react';
import { createPortal } from 'react-dom';

export default function PopupLayer({ anchorRef, open, onClose, className, children, align = 'right', overlayKind, overlayData, onOverlayAction, closeAfterAction = false }) {
  const popupRef = useRef(null);
  const overlayId = useRef(globalThis.crypto?.randomUUID?.() || `popup-${Date.now()}-${Math.random().toString(36).slice(2)}`);
  const overlayDataRef = useRef(overlayData);
  const overlayActionRef = useRef(onOverlayAction);
  const closeRef = useRef(onClose);
  overlayDataRef.current = overlayData;
  overlayActionRef.current = onOverlayAction;
  closeRef.current = onClose;
  const [position, setPosition] = useState({ top: -10000, left: -10000 });
  const payloadKey = JSON.stringify(overlayData || {});

  useLayoutEffect(() => {
    if (!open) return undefined;
    let frame = 0;
    const place = () => {
      cancelAnimationFrame(frame);
      frame = requestAnimationFrame(() => {
        const anchor = anchorRef.current?.getBoundingClientRect();
        const popup = popupRef.current?.getBoundingClientRect();
        if (!anchor || !popup) return;
        const margin = 8;
        const left = align === 'left' ? anchor.left : anchor.right - popup.width;
        const clampedLeft = Math.max(margin, Math.min(left, window.innerWidth - popup.width - margin));
        const below = anchor.bottom + 7;
        const top = below + popup.height <= window.innerHeight - margin
          ? below
          : Math.max(margin, anchor.top - popup.height - 7);
        const nextPosition = { top, left: clampedLeft };
        setPosition(nextPosition);
      });
    };
    const outside = event => {
      const insidePopup = popupRef.current?.contains(event.target) || anchorRef.current?.contains(event.target);
      if (insidePopup) return;
      if (overlayKind && window.cacheBrowse?.showPopupOverlay) window.cacheBrowse.closePopupOverlay?.(overlayId.current);
      onClose();
    };
    const keydown = event => {
      if (event.key === 'Escape') {
        event.stopPropagation();
        onClose();
      }
    };
    const onNativeClose = payload => {
      if (payload?.id !== overlayId.current) return;
      closeRef.current();
      if (payload.reason === 'escape') requestAnimationFrame(() => anchorRef.current?.focus?.());
    };
    const onNativeAction = payload => {
      if (payload?.id !== overlayId.current) return;
      overlayActionRef.current?.(payload.action, payload.value);
      if (closeAfterAction) closeRef.current();
    };
    document.addEventListener('pointerdown', outside);
    document.addEventListener('keydown', keydown);
    window.addEventListener('resize', place);
    window.addEventListener('scroll', place, true);
    const unsubscribeClose = window.cacheBrowse?.subscribe?.('popupClosed', onNativeClose);
    const unsubscribeAction = window.cacheBrowse?.subscribe?.('popupAction', onNativeAction);
    place();
    return () => {
      cancelAnimationFrame(frame);
      document.removeEventListener('pointerdown', outside);
      document.removeEventListener('keydown', keydown);
      window.removeEventListener('resize', place);
      window.removeEventListener('scroll', place, true);
      unsubscribeClose?.();
      unsubscribeAction?.();
      if (overlayKind) window.cacheBrowse?.closePopupOverlay?.(overlayId.current);
    };
  }, [open, anchorRef, align, overlayKind, closeAfterAction]);

  useLayoutEffect(() => {
    if (!open || !overlayKind || !window.cacheBrowse?.showPopupOverlay || position.top < 0) return;
    const popup = popupRef.current?.getBoundingClientRect();
    if (!popup) return;
    window.cacheBrowse.showPopupOverlay({
      id: overlayId.current,
      kind: overlayKind,
      payload: overlayDataRef.current || {},
      bounds: { x: position.left, y: position.top, width: popup.width, height: popup.height },
    }).catch(error => console.error(`[Overlay] Could not show ${overlayKind} popup: ${error.message}`));
  }, [open, overlayKind, position.top, position.left, payloadKey]);

  if (!open) return null;
  if (overlayKind && window.cacheBrowse?.showPopupOverlay) {
    return <div ref={popupRef} className={className} style={{ position: 'fixed', top: -10000, left: -10000, visibility: 'hidden', pointerEvents: 'none' }}>{children}</div>;
  }
  return createPortal(
    <div ref={popupRef} className={className} style={{ position: 'fixed', top: position.top, left: position.left, zIndex: 10000 }}>
      {children}
    </div>,
    document.body,
  );
}
