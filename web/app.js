/* ═══════════════════════════════════════════════════════════════
   令牌库 TokenVault — 核心逻辑
   纯本地 · AES-256-GCM 加密 · RFC 6238 TOTP · Steam Guard
   ═══════════════════════════════════════════════════════════════ */
'use strict';

/* ───────────────── 品牌图标 ───────────────── */
const ICONS = {
  google: `<svg viewBox="0 0 48 48"><path fill="#EA4335" d="M24 9.5c3.54 0 6.71 1.22 9.21 3.6l6.85-6.85C35.9 2.38 30.47 0 24 0 14.62 0 6.51 5.38 2.56 13.22l7.98 6.19C12.43 13.72 17.74 9.5 24 9.5z"/><path fill="#4285F4" d="M46.98 24.55c0-1.57-.15-3.09-.38-4.55H24v9.02h12.94c-.58 2.96-2.26 5.48-4.78 7.18l7.73 6c4.51-4.18 7.09-10.36 7.09-17.65z"/><path fill="#FBBC05" d="M10.53 28.59c-.48-1.45-.76-2.99-.76-4.59s.27-3.14.76-4.59l-7.98-6.19C.92 16.46 0 20.12 0 24s.92 7.54 2.56 10.78l7.97-6.19z"/><path fill="#34A853" d="M24 48c6.48 0 11.93-2.13 15.89-5.81l-7.73-6c-2.15 1.45-4.92 2.3-8.16 2.3-6.26 0-11.57-4.22-13.47-9.91l-7.98 6.19C6.51 42.62 14.62 48 24 48z"/></svg>`,
  microsoft: `<svg viewBox="0 0 24 24"><rect x="2" y="2" width="9.4" height="9.4" rx=".6" fill="#F25022"/><rect x="12.6" y="2" width="9.4" height="9.4" rx=".6" fill="#7FBA00"/><rect x="2" y="12.6" width="9.4" height="9.4" rx=".6" fill="#00A4EF"/><rect x="12.6" y="12.6" width="9.4" height="9.4" rx=".6" fill="#FFB900"/></svg>`,
  github: `<svg viewBox="0 0 24 24" fill="#f0f3f6"><path d="M12 .3a12 12 0 0 0-3.8 23.4c.6.1.8-.3.8-.6v-2c-3.3.7-4-1.6-4-1.6-.6-1.4-1.4-1.8-1.4-1.8-1.1-.8.1-.8.1-.8 1.2.1 1.8 1.2 1.8 1.2 1.1 1.9 2.9 1.3 3.5 1 .1-.8.4-1.3.8-1.6-2.7-.3-5.5-1.3-5.5-5.9 0-1.3.5-2.4 1.2-3.2-.1-.3-.5-1.5.1-3.2 0 0 1-.3 3.3 1.2a11.5 11.5 0 0 1 6 0C17.3 4.2 18.3 4.5 18.3 4.5c.6 1.7.2 2.9.1 3.2.8.8 1.2 1.9 1.2 3.2 0 4.6-2.8 5.6-5.5 5.9.4.4.8 1.1.8 2.2v3.3c0 .3.2.7.8.6A12 12 0 0 0 12 .3z"/></svg>`,
  steam: `<svg viewBox="0 0 24 24" fill="#c7d5e0"><path d="M11.98 2C6.5 2 2.01 6.15 1.5 11.5l5.6 2.32a3.15 3.15 0 0 1 2.03-.51l2.5-3.62v-.05a4.2 4.2 0 1 1 4.2 4.2h-.1l-3.57 2.55c0 .06.01.13.01.2a3.16 3.16 0 1 1-6.28-.2l-3.2-1.32A10.5 10.5 0 0 0 11.98 22c5.8 0 10.5-4.7 10.5-10.5S17.78 2 11.98 2zm-4.6 13.9-1.4-.58a2.37 2.37 0 0 0 4.36-1.05 2.37 2.37 0 0 0-4.35-1.1l1.45.6a1.74 1.74 0 1 1-1.33 3.2l-.03-.02 1.3.06zm8.35-4.44a2.8 2.8 0 1 0 0-5.6 2.8 2.8 0 0 0 0 5.6zm0-.44a2.36 2.36 0 1 1 0-4.72 2.36 2.36 0 0 1 0 4.72z"/></svg>`,
  apple: `<svg viewBox="0 0 24 24" fill="#e8ebf0"><path d="M17.05 12.53c-.03-2.5 2.04-3.7 2.13-3.76-1.16-1.7-2.97-1.93-3.61-1.96-1.54-.16-3 .9-3.78.9-.78 0-1.98-.88-3.26-.86-1.68.03-3.22.97-4.08 2.47-1.74 3.02-.44 7.5 1.25 9.95.83 1.2 1.82 2.55 3.12 2.5 1.25-.05 1.72-.81 3.23-.81 1.5 0 1.93.81 3.25.79 1.34-.03 2.19-1.22 3.01-2.43.95-1.39 1.34-2.73 1.36-2.8-.03-.01-2.6-1-2.62-3.99zM14.6 5.2c.68-.83 1.14-1.98 1.02-3.12-.98.04-2.17.65-2.88 1.48-.63.73-1.19 1.9-1.04 3.02 1.1.09 2.21-.55 2.9-1.38z"/></svg>`,
  discord: `<svg viewBox="0 0 24 24" fill="#c9d1f0"><path d="M20.3 4.5A19.8 19.8 0 0 0 15.4 3l-.25.5a18 18 0 0 0-6.3 0L8.6 3a19.8 19.8 0 0 0-4.9 1.5C.6 9.2-.2 13.8.2 18.2a19.9 19.9 0 0 0 6 3l.8-1.3a13 13 0 0 1-2.1-1l.5-.4a14.2 14.2 0 0 0 12.2 0l.5.4a13 13 0 0 1-2.1 1l.8 1.3a19.9 19.9 0 0 0 6-3c.5-5-.8-9.6-3.5-13.7zM8.3 15.3c-1.2 0-2.2-1.1-2.2-2.4s1-2.4 2.2-2.4 2.2 1.1 2.2 2.4-1 2.4-2.2 2.4zm7.4 0c-1.2 0-2.2-1.1-2.2-2.4s1-2.4 2.2-2.4 2.2 1.1 2.2 2.4-1 2.4-2.2 2.4z"/></svg>`,
  x: `<svg viewBox="0 0 24 24" fill="#e8ebf0"><path d="M18.9 2h3.5l-7.7 8.8L23.8 22h-7l-5.5-7.2L5 22H1.5l8.2-9.4L1 2h7.2l5 6.6L18.9 2zm-1.2 18h1.9L7.4 3.9H5.4L17.7 20z"/></svg>`,
  facebook: `<svg viewBox="0 0 24 24" fill="#1877F2"><path d="M24 12a12 12 0 1 0-13.9 11.9v-8.4H7.1V12h3V9.4c0-3 1.8-4.6 4.5-4.6 1.3 0 2.6.2 2.6.2v2.9h-1.5c-1.5 0-1.9.9-1.9 1.8V12h3.3l-.5 3.5h-2.8v8.4A12 12 0 0 0 24 12z"/></svg>`,
  amazon: `<svg viewBox="0 0 24 24" fill="#FF9900"><path d="M14.6 11.1c0 .3 0 .6.1.8.1.2.2.4.3.5l.1.2c0 .1 0 .1-.1.2l-.4.3c-.1 0-.1 0-.2-.1a1.9 1.9 0 0 1-.4-.6c-.9 1.1-2.1 1.6-3.4 1.6-1.6 0-2.6-.9-2.6-2.3 0-1.5 1.2-2.4 3-2.4.5 0 1 0 1.6.1v-.6c0-1.3-.6-1.9-2-1.9-.5 0-1 .1-1.5.2-.3.1-.5.2-.6.2-.1 0-.1 0-.2-.1v-.5c0-.1 0-.2.1-.2.4-.2.8-.4 1.3-.5.6-.1 1.1-.2 1.7-.2 2.1 0 3.2 1.2 3.2 3.2v-3.1zm-4.7 1.8c.5 0 .9-.1 1.3-.3.4-.2.7-.6.9-1 .1-.2.1-.4.1-.7v-.6c-.4-.1-.9-.1-1.3-.1-1 0-1.6.4-1.6 1.3 0 .8.5 1.4 1.6 1.4z"/><path d="M19.3 17.4c-2.2 1.6-5.3 2.4-8 2.4-3.8 0-7.2-1.4-9.7-3.7-.2-.2 0-.5.2-.4 2.8 1.6 6.1 2.6 9.7 2.6 2.4 0 4.9-.5 7.3-1.5.4-.1.6.2.5.6z"/><path d="M20.3 16.3c-.3-.4-1.9-.2-2.6-.1-.2 0-.3-.2-.1-.3.9-.6 2.2-.4 2.3-.2.2.2 0 1.4-.8 2.1-.1.1-.2 0-.2-.1.2-.5.7-1.3.4-1.4z"/></svg>`,
  dropbox: `<svg viewBox="0 0 24 24" fill="#0061FF"><path d="M6 2 0 5.9l6 3.9 6-3.9L6 2zm12 0-6 3.9 6 3.9 6-3.9L18 2zM0 13.7l6 3.9 6-3.9-6-3.9-6 3.9zm18-3.9-6 3.9 6 3.9 6-3.9-6-3.9zM6 18.9l6 3.9 6-3.9-6-3.8-6 3.8z"/></svg>`,
  gitlab: `<svg viewBox="0 0 24 24" fill="#FC6D26"><path d="M23.5 9.6 12 2.3 12 22l5.9-4.3 4.3-5.4c.4-.5.5-1.2.2-1.8l1.1-.9z" opacity=".85"/><path d="M12 2.3.5 9.6l1.1.9c-.3.6-.2 1.3.2 1.8l4.3 5.4L12 22V2.3z"/></svg>`,
  linkedin: `<svg viewBox="0 0 24 24" fill="#0A66C2"><path d="M20.4 2H3.6C2.7 2 2 2.7 2 3.6v16.8c0 .9.7 1.6 1.6 1.6h16.8c.9 0 1.6-.7 1.6-1.6V3.6c0-.9-.7-1.6-1.6-1.6zM8 19H5V9h3v10zM6.5 7.7a1.8 1.8 0 1 1 0-3.5 1.8 1.8 0 0 1 0 3.5zM19 19h-3v-5.3c0-1.3 0-2.9-1.8-2.9s-2 1.4-2 2.8V19h-3V9h2.9v1.4h.1c.4-.8 1.4-1.6 2.9-1.6 3.1 0 3.7 2 3.7 4.7V19z"/></svg>`,
  twitter: `<svg viewBox="0 0 24 24" fill="#1DA1F2"><path d="M23 4.9a9 9 0 0 1-2.6.7 4.5 4.5 0 0 0 2-2.5c-.9.5-1.9.9-2.9 1.1a4.5 4.5 0 0 0-7.7 4.1A12.8 12.8 0 0 1 2.5 3.6a4.5 4.5 0 0 0 1.4 6 4.4 4.4 0 0 1-2-.6v.1a4.5 4.5 0 0 0 3.6 4.4 4.5 4.5 0 0 1-2 .1 4.5 4.5 0 0 0 4.2 3.1A9 9 0 0 1 1 18.6a12.7 12.7 0 0 0 6.9 2c8.3 0 12.8-6.9 12.8-12.8v-.6A9 9 0 0 0 23 4.9z"/></svg>`,
  telegram: `<svg viewBox="0 0 24 24" fill="#2AABEE"><path d="M12 2a10 10 0 1 0 0 20 10 10 0 0 0 0-20zm4.6 6.9-1.6 7.5c-.1.5-.4.7-.9.4l-2.4-1.8-1.2 1.1c-.1.1-.2.2-.5.2l.2-2.5 4.5-4c.2-.2 0-.3-.3-.1l-5.6 3.5-2.4-.7c-.5-.2-.5-.5.1-.8l9.4-3.6c.4-.2.8 0 .7.8z"/></svg>`,
  slack: `<svg viewBox="0 0 24 24"><path d="M5.1 15.2a2 2 0 1 1-2-2h2v2zm1 0a2 2 0 0 1 4 0v5a2 2 0 1 1-4 0v-5z" fill="#E01E5A"/><path d="M8.1 5.2a2 2 0 1 1 2-2v2h-2zm0 1a2 2 0 0 1 0 4h-5a2 2 0 1 1 0-4h5z" fill="#36C5F0"/><path d="M18.1 8.2a2 2 0 1 1 2 2h-2v-2zm-1 0a2 2 0 0 1-4 0v-5a2 2 0 1 1 4 0v5z" fill="#2EB67D"/><path d="M15.1 18.2a2 2 0 1 1-2 2v-2h2zm0-1a2 2 0 0 1 0-4h5a2 2 0 1 1 0 4h-5z" fill="#ECB22E"/></svg>`,
  reddit: `<svg viewBox="0 0 24 24" fill="#FF4500"><path d="M22 11.8a2.4 2.4 0 0 0-4.1-1.7 11.7 11.7 0 0 0-5.3-1.6l1-4.1 2.9.6a1.7 1.7 0 1 0 .3-1.4l-3.6-.8a.6.6 0 0 0-.7.4l-1.2 4.9a11.8 11.8 0 0 0-5.4 1.7 2.4 2.4 0 1 0-2.5 3.9 4.4 4.4 0 0 0-.1.9c0 3.3 3.9 6 8.6 6s8.6-2.7 8.6-6a4.4 4.4 0 0 0-.1-.9 2.4 2.4 0 0 0 1.6-2.3zM6 12.9a1.5 1.5 0 1 1 3 0 1.5 1.5 0 0 1-3 0zm8.5 3.9c-1 1-3 1.1-3.6 1.1s-2.6-.1-3.6-1.1a.4.4 0 0 1 .5-.5c.7.5 2.2.7 3.1.7s2.4-.2 3.1-.7a.4.4 0 0 1 .5.5zm-.4-2.4a1.5 1.5 0 1 1 0-3 1.5 1.5 0 0 1 0 3z"/></svg>`,
  twitch: `<svg viewBox="0 0 24 24" fill="#9146FF"><path d="M4.3 2 .5 5.8v14.5h4v3.7l3.7-3.7h3L20 12V2H4.3zM18 11.3l-3.3 3.3h-4l-2.7 2.6v-2.6H4.7V4h13.3v7.3z"/><path d="M10.3 7.3h2v5.4h-2zm5.4 0h2v5.4h-2z"/></svg>`,
  spotify: `<svg viewBox="0 0 24 24" fill="#1DB954"><path d="M12 2a10 10 0 1 0 0 20 10 10 0 0 0 0-20zm4.6 14.4a.9.9 0 0 1-1.2.3c-3.3-2-7.4-2.5-12.3-1.4a.9.9 0 1 1-.4-1.7c5.3-1.2 9.9-.7 13.6 1.6.4.2.5.7.3 1.2zm1.2-2.8a1.1 1.1 0 0 1-1.5.4c-3.8-2.3-9.5-3-14-1.6a1.1 1.1 0 1 1-.6-2.1c5.1-1.5 11.4-.8 15.7 1.8.5.3.7 1 .4 1.5zm.1-2.9C13.4 8 6.7 7.8 2.4 9.1a1.3 1.3 0 1 1-.8-2.5C6.6 5.1 14 5.4 19 8.4a1.3 1.3 0 0 1-1.3 2.3z"/></svg>`,
  binance: `<svg viewBox="0 0 24 24" fill="#F0B90B"><path d="M12 1.5 15.2 4.7 12 7.9 8.8 4.7 12 1.5zM5.3 8.2 8.5 11.4 5.3 14.6 2.1 11.4 5.3 8.2zm13.4 0 3.2 3.2-3.2 3.2-3.2-3.2 3.2-3.2zM12 8.7l3.3 3.3-3.3 3.3-3.3-3.3L12 8.7zm0 7.4 3.2 3.2L12 22.5l-3.2-3.2L12 16.1z"/></svg>`,
  cloudflare: `<svg viewBox="0 0 24 24" fill="#F38020"><path d="M17.7 13.6c.1-.3.1-.6 0-.8-.1-.3-.3-.4-.6-.5l-8.5-.1c-.1 0-.2 0-.2-.1s0-.2.1-.2l8.6-.1c1-.1 2.1-.9 2.5-1.8l.5-1.3c0-.1 0-.1 0-.2A5.6 5.6 0 0 0 9.4 7c-.5-.4-1.2-.6-1.9-.5a3 3 0 0 0-2.7 2.5 2.8 2.8 0 0 0-2.4 2.6v.3c0 .1.1.2.2.2h15c.1 0 .2-.1.1-.5z"/><path d="M19.2 9.6h-.2l-.3 1a6 6 0 0 1-.5 1.8c0 .1 0 .2.1.2h.8c.1 0 .2 0 .2-.2.4-1.4.2-2.2-.1-2.8z" opacity=".7"/></svg>`,
  notion: `<svg viewBox="0 0 24 24" fill="#e8ebf0"><path d="M4 3.7 15.6 2.6c1.4-.1 1.8 0 2.7.6l3.2 2.3c.6.4.8.6.8 1.1v14.6c0 .9-.3 1.4-1.5 1.5L7.7 23.8c-.9.1-1.3-.1-1.8-.7l-2.4-3.2c-.6-.7-.8-1.3-.8-2V5.2c0-.7.3-1.3 1.3-1.5zm12.3 2-9.9.7c-.7.1-.9.4-.9 1v12.4l11.9-.7V6.5c0-.5-.2-.7-.6-.8h-.5zM9.4 8.9l6.4-.4v9.6l-6.4.4V8.9z"/></svg>`,
  proton: `<svg viewBox="0 0 24 24" fill="#8B5CF6"><path d="M2 5h20v7.5h-5.3L12 7.2 7.3 12.5H2V5zm0 9.5h3.7l3.5-4 3.5 4h3.5L12 9.9l-4.4 4.6H2zM2 16.5h20V19H2v-2.5z"/></svg>`,
  instagram: `<svg viewBox="0 0 24 24" fill="#E1306C"><path d="M12 2.2c3.2 0 3.6 0 4.8.1 1.2.1 1.8.2 2.2.4.6.2 1 .5 1.4.9.4.4.7.8.9 1.4.2.4.4 1 .4 2.2.1 1.2.1 1.6.1 4.8s0 3.6-.1 4.8c-.1 1.2-.2 1.8-.4 2.2-.2.6-.5 1-.9 1.4-.4.4-.8.7-1.4.9-.4.2-1 .4-2.2.4-1.2.1-1.6.1-4.8.1s-3.6 0-4.8-.1c-1.2-.1-1.8-.2-2.2-.4-.6-.2-1-.5-1.4-.9-.4-.4-.7-.8-.9-1.4-.2-.4-.4-1-.4-2.2C2.2 15.6 2.2 15.2 2.2 12s0-3.6.1-4.8c.1-1.2.2-1.8.4-2.2.2-.6.5-1 .9-1.4.4-.4.8-.7 1.4-.9.4-.2 1-.4 2.2-.4C8.4 2.2 8.8 2.2 12 2.2zm0 1.8c-3.1 0-3.5 0-4.7.1-1.1.1-1.7.2-2.1.4-.5.2-.9.4-1.2.8-.4.4-.6.7-.8 1.2-.2.4-.3 1-.4 2.1-.1 1.2-.1 1.6-.1 4.7s0 3.5.1 4.7c.1 1.1.2 1.7.4 2.1.2.5.4.9.8 1.2.4.4.7.6 1.2.8.4.2 1 .3 2.1.4 1.2.1 1.6.1 4.7.1s3.5 0 4.7-.1c1.1-.1 1.7-.2 2.1-.4.5-.2.9-.4 1.2-.8.4-.4.6-.7.8-1.2.2-.4.3-1 .4-2.1.1-1.2.1-1.6.1-4.7s0-3.5-.1-4.7c-.1-1.1-.2-1.7-.4-2.1-.2-.5-.4-.9-.8-1.2-.4-.4-.7-.6-1.2-.8-.4-.2-1-.3-2.1-.4-1.2-.1-1.6-.1-4.7-.1zm0 3.1a4.9 4.9 0 1 1 0 9.8 4.9 4.9 0 0 1 0-9.8zm0 8.1a3.2 3.2 0 1 0 0-6.4 3.2 3.2 0 0 0 0 6.4zm6.3-8.3a1.15 1.15 0 1 1-2.3 0 1.15 1.15 0 0 1 2.3 0z"/></svg>`,
  epic: `<svg viewBox="0 0 24 24" fill="#e8ebf0"><path d="M3.5 2 4.9 20 12 22l7.1-2L20.5 2h-17zm3.9 4h9.2v1.9H9.4v2.5h6.6v1.9H9.4v2.9h7.2v1.9H7.4V6z"/></svg>`,
  xbox: `<svg viewBox="0 0 24 24" fill="#107C10"><path d="M4.6 4.5C6.6 3 9.2 2.1 12 2.1c2.8 0 5.4.9 7.4 2.4A13 13 0 0 0 12 11a13 13 0 0 0-7.4-6.5zM3.1 6.4A10 10 0 0 0 2 12c0 2.4.8 4.6 2.2 6.3 1.5-3 4-6.1 5.6-7.5-1.9-2.2-4-4.2-6.7-4.4zm17.8 0c-2.7.2-4.8 2.2-6.7 4.4 1.6 1.4 4.1 4.5 5.6 7.5A10 10 0 0 0 22 12c0-2.1-.4-4-1.1-5.6zM12 13.4c-1.9 1.7-4.6 5.3-5.6 7.2 1.6.9 3.5 1.4 5.6 1.4s4-.5 5.6-1.4c-1-1.9-3.7-5.5-5.6-7.2z"/></svg>`,
  custom: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round"><circle cx="8.5" cy="8.5" r="4.2"/><path d="M11.6 11.6 20 20M17 17l2-2M14 14l2-2"/></svg>`
};

/* 平台预置：颜色 / 默认参数 / 图标 */
const PLATFORMS = {
  google:    { label:'Google',    bg:'#ffffff', fg:'#000', digits:6, period:30, algo:'SHA1', charset:'numeric' },
  microsoft: { label:'Microsoft', bg:'#ffffff', fg:'#000', digits:6, period:30, algo:'SHA1', charset:'numeric' },
  github:    { label:'GitHub',    bg:'#24292f', fg:'#fff', digits:6, period:30, algo:'SHA1', charset:'numeric' },
  steam:     { label:'Steam',     bg:'#1b2838', fg:'#fff', digits:5, period:30, algo:'SHA1', charset:'steam'   },
  apple:     { label:'Apple',     bg:'#1c1c1e', fg:'#fff', digits:6, period:30, algo:'SHA1', charset:'numeric' },
  discord:   { label:'Discord',   bg:'#5865f2', fg:'#fff', digits:6, period:30, algo:'SHA1', charset:'numeric' },
  x:         { label:'X',         bg:'#000000', fg:'#fff', digits:6, period:30, algo:'SHA1', charset:'numeric' },
  facebook:  { label:'Facebook',  bg:'#1877f2', fg:'#fff', digits:6, period:30, algo:'SHA1', charset:'numeric' },
  amazon:    { label:'Amazon',    bg:'#131921', fg:'#fff', digits:6, period:30, algo:'SHA1', charset:'numeric' },
  dropbox:   { label:'Dropbox',   bg:'#0061ff', fg:'#fff', digits:6, period:30, algo:'SHA1', charset:'numeric' },
  gitlab:    { label:'GitLab',    bg:'#fc6d26', fg:'#fff', digits:6, period:30, algo:'SHA1', charset:'numeric' },
  linkedin:  { label:'LinkedIn',  bg:'#0a66c2', fg:'#fff', digits:6, period:30, algo:'SHA1', charset:'numeric' },
  twitter:   { label:'Twitter',   bg:'#1da1f2', fg:'#fff', digits:6, period:30, algo:'SHA1', charset:'numeric' },
  telegram:  { label:'Telegram',  bg:'#2aabee', fg:'#fff', digits:6, period:30, algo:'SHA1', charset:'numeric' },
  slack:     { label:'Slack',     bg:'#ffffff', fg:'#000', digits:6, period:30, algo:'SHA1', charset:'numeric' },
  reddit:    { label:'Reddit',    bg:'#ff4500', fg:'#fff', digits:6, period:30, algo:'SHA1', charset:'numeric' },
  twitch:    { label:'Twitch',    bg:'#9146ff', fg:'#fff', digits:6, period:30, algo:'SHA1', charset:'numeric' },
  spotify:   { label:'Spotify',   bg:'#1db954', fg:'#fff', digits:6, period:30, algo:'SHA1', charset:'numeric' },
  binance:   { label:'Binance',   bg:'#181a20', fg:'#f0b90b', digits:6, period:30, algo:'SHA1', charset:'numeric' },
  cloudflare:{ label:'Cloudflare',bg:'#f38020', fg:'#fff', digits:6, period:30, algo:'SHA1', charset:'numeric' },
  notion:    { label:'Notion',    bg:'#ffffff', fg:'#000', digits:6, period:30, algo:'SHA1', charset:'numeric' },
  proton:    { label:'Proton',    bg:'#6d4aff', fg:'#fff', digits:6, period:30, algo:'SHA1', charset:'numeric' },
  instagram: { label:'Instagram', bg:'#e1306c', fg:'#fff', digits:6, period:30, algo:'SHA1', charset:'numeric' },
  epic:      { label:'Epic',      bg:'#2a2a2a', fg:'#fff', digits:6, period:30, algo:'SHA1', charset:'numeric' },
  xbox:      { label:'Xbox',      bg:'#107c10', fg:'#fff', digits:6, period:30, algo:'SHA1', charset:'numeric' },
  custom:    { label:'自定义',      bg:'#3a3f52', fg:'#fff', digits:6, period:30, algo:'SHA1', charset:'numeric' }
};

const STEAM_CHARS = '23456789BCDFGHJKMNPQRTVWXY';
const B32_ALPHABET = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ234567';

/* ───────────────── 小工具 ───────────────── */
const $  = (s, r = document) => r.querySelector(s);
const $$ = (s, r = document) => Array.from(r.querySelectorAll(s));
const el = (tag, cls, html) => { const n = document.createElement(tag); if (cls) n.className = cls; if (html != null) n.innerHTML = html; return n; };
const pad2 = n => String(n).padStart(2, '0');
const clamp = (v, a, b) => Math.min(b, Math.max(a, v));

function haptic(ms = 8) { try { navigator.vibrate && navigator.vibrate(ms); } catch (_) {} }

/* Base32 解码（容错：忽略空格/连字符/等号，0→O、1→I/L） */
function b32decode(input) {
  let s = String(input || '').replace(/[\s\-=]/g, '').toUpperCase()
            .replace(/0/g, 'O').replace(/1/g, 'L');
  let bits = 0, value = 0;
  const out = [];
  for (let i = 0; i < s.length; i++) {
    const idx = B32_ALPHABET.indexOf(s[i]);
    if (idx < 0) continue;
    value = (value << 5) | idx;
    bits += 5;
    if (bits >= 8) { out.push((value >>> (bits - 8)) & 0xff); bits -= 8; }
  }
  return new Uint8Array(out);
}

/* 校验是否为可用的 Base32 密钥 */
function isValidSecret(str) {
  const s = String(str || '').replace(/[\s\-=]/g, '').toUpperCase();
  if (s.length < 8) return false;
  return b32decode(s).length >= 5;
}

/* 生成随机 Base32 密钥（20 字节 = 160 bit，RFC 推荐） */
function randomSecret(bytes = 20) {
  const buf = new Uint8Array(bytes);
  crypto.getRandomValues(buf);
  let out = '';
  let bits = 0, value = 0;
  for (const b of buf) {
    value = (value << 8) | b; bits += 8;
    while (bits >= 5) { out += B32_ALPHABET[(value >>> (bits - 5)) & 31]; bits -= 5; }
  }
  if (bits > 0) out += B32_ALPHABET[(value << (5 - bits)) & 31];
  return out;
}

/* ───────────────── TOTP 引擎 ───────────────── */
const _hmacKeyCache = new Map();

/* WebCrypto 要求带连字符的算法名 */
const HASH_NAME = { SHA1: 'SHA-1', SHA256: 'SHA-256', SHA512: 'SHA-512' };

async function _hmac(algo, keyBytes, msgBytes) {
  // file:// 等非安全上下文中没有 crypto.subtle，退化为纯 JS HMAC-SHA1
  if (!HAS_SUBTLE) {
    if (algo !== 'SHA1') throw new Error('当前环境仅支持 SHA-1，请用本地服务器打开以启用 SHA-256/512');
    return hmacSha1(keyBytes, msgBytes);
  }
  const hash = HASH_NAME[algo] || 'SHA-1';
  const cacheKey = hash + ':' + keyBytes.length + ':' + Array.from(keyBytes).join(',');
  let key = _hmacKeyCache.get(cacheKey);
  if (!key) {
    key = await crypto.subtle.importKey('raw', keyBytes, { name: 'HMAC', hash }, false, ['sign']);
    if (_hmacKeyCache.size > 200) _hmacKeyCache.clear();
    _hmacKeyCache.set(cacheKey, key);
  }
  return new Uint8Array(await crypto.subtle.sign('HMAC', key, msgBytes));
}

function _counterBytes(n) {
  const b = new Uint8Array(8);
  let v = BigInt(Math.floor(n));
  for (let i = 7; i >= 0; i--) { b[i] = Number(v & 0xffn); v >>= 8n; }
  return b;
}

/**
 * 生成 TOTP 验证码
 * @returns {{code:string, remaining:number, period:number}}
 */
async function totp(secret, { digits = 6, period = 30, algo = 'SHA1', charset = 'numeric' } = {}) {
  const keyBytes = b32decode(secret);
  if (!keyBytes.length) return { code: '—'.repeat(1), remaining: 0, period };
  const nowSec = Math.floor(Date.now() / 1000);
  const counter = Math.floor(nowSec / period);
  const remaining = period - (nowSec % period);
  const bin = await _hmac(algo, keyBytes, _counterBytes(counter));

  const off = bin[bin.length - 1] & 0x0f;
  const num = ((bin[off] & 0x7f) << 24) | (bin[off + 1] << 16) | (bin[off + 2] << 8) | bin[off + 3];

  let code;
  if (charset === 'steam') {
    let v = num;
    let s = '';
    for (let i = 0; i < 5; i++) { s += STEAM_CHARS[v % STEAM_CHARS.length]; v = Math.floor(v / STEAM_CHARS.length); }
    code = s;
  } else {
    const mod = Math.pow(10, digits);
    code = String(num % mod).padStart(digits, '0');
  }
  return { code, remaining, period };
}

/* 格式化：6 位 → "123 456"；5 位/8 位按需分组 */
function formatCode(code) {
  if (!code) return '';
  if (code.length === 6) return code.slice(0, 3) + ' ' + code.slice(3);
  if (code.length === 8) return code.slice(0, 4) + ' ' + code.slice(4);
  if (code.length === 5) return code.slice(0, 2) + ' ' + code.slice(2, 5);
  return code;
}

/* 按平台习惯切分成组，渲染时用 CSS gap 控制间距（比空格字符更紧凑好看） */
function codeGroups(code) {
  if (!code) return [];
  if (code.length === 6) return [code.slice(0, 3), code.slice(3)];
  if (code.length === 8) return [code.slice(0, 4), code.slice(4)];
  if (code.length === 5) return [code.slice(0, 2), code.slice(2)];
  return [code];
}
function codeMarkup(code, masked) {
  const groups = masked ? ['•••', '•••'] : codeGroups(code);
  return groups.map(g => `<span class="cg">${esc(g)}</span>`).join('');
}

/* 解析 otpauth:// 链接 */
function parseOtpAuth(uri) {
  try {
    const u = new URL(String(uri).trim());
    if (u.protocol !== 'otpauth:') return null;
    const p = u.searchParams;
    const rawLabel = decodeURIComponent(u.pathname.replace(/^\//, ''));
    let issuer = p.get('issuer') || '';
    let account = rawLabel;
    if (rawLabel.includes(':')) {
      const [a, b] = rawLabel.split(':');
      if (!issuer) issuer = a.trim();
      account = b.trim();
    }
    const secret = (p.get('secret') || '').toUpperCase();
    if (!isValidSecret(secret)) return null;
    const type = (u.host || '').toLowerCase();
    let digits = parseInt(p.get('digits') || '6', 10);
    let period = parseInt(p.get('period') || '30', 10);
    let algo = (p.get('algorithm') || 'SHA1').toUpperCase().replace('-', '');
    if (!['SHA1', 'SHA256', 'SHA512'].includes(algo)) algo = 'SHA1';
    // 猜平台
    const hay = (issuer + ' ' + account).toLowerCase();
    let platform = 'custom';
    for (const k of Object.keys(PLATFORMS)) {
      if (k === 'custom') continue;
      if (hay.includes(k) || hay.includes(PLATFORMS[k].label.toLowerCase())) { platform = k; break; }
    }
    if (hay.includes('steam') || type === 'steam') { platform = 'steam'; digits = 5; period = 30; algo = 'SHA1'; }
    return { platform, issuer: issuer || PLATFORMS[platform].label, account, secret, digits, period, algo };
  } catch (_) { return null; }
}

/* ───────────────── 解析 otpauth-migration（Google Authenticator 导出）───────────────── */
/* Base32 编码：migration 里的 secret 是原始字节 */
function b32encode(bytes) {
  let bits = 0, value = 0, out = '';
  for (let i = 0; i < bytes.length; i++) {
    value = (value << 8) | bytes[i];
    bits += 8;
    while (bits >= 5) {
      out += B32_ALPHABET[(value >>> (bits - 5)) & 31];
      bits -= 5;
    }
  }
  if (bits > 0) out += B32_ALPHABET[(value << (5 - bits)) & 31];
  return out;
}

/* 极简 protobuf 读取器 */
function _pbReader(buf) {
  let pos = 0;
  return {
    more: () => pos < buf.length,
    varint() {
      let r = 0, shift = 0;
      while (pos < buf.length) {
        const b = buf[pos++];
        r += (b & 0x7f) * Math.pow(2, shift);
        if ((b & 0x80) === 0) return r;
        shift += 7;
        if (shift > 63) return r;
      }
      return r;
    },
    bytes(n) {
      const end = Math.min(pos + Math.max(0, n), buf.length);
      const out = buf.subarray(pos, end);
      pos = end;
      return out;
    },
    skip(wire) {
      if (wire === 0) this.varint();
      else if (wire === 1) pos += 8;
      else if (wire === 2) pos += this.varint();
      else if (wire === 5) pos += 4;
      else pos = buf.length;
      if (pos < 0 || pos > buf.length) pos = buf.length;
    }
  };
}

function _b64UrlBytes(input) {
  try {
    let s = String(input).replace(/-/g, '+').replace(/_/g, '/').replace(/[^A-Za-z0-9+/=]/g, '');
    if (!s) return null;
    s += '='.repeat((4 - (s.length % 4)) % 4);
    const bin = atob(s);
    const out = new Uint8Array(bin.length);
    for (let i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i);
    return out;
  } catch (_) { return null; }
}

/* 平台别名：issuer 里常见的非品牌名 */
const PLATFORM_ALIASES = {
  google:     ['gmail', 'googlemail', 'youtube', 'workspace', 'gsuite'],
  microsoft:  ['outlook', 'hotmail', 'office365', 'office 365', 'azure', 'onedrive', 'skype', 'msn', 'live.com'],
  apple:      ['icloud', 'appleid', 'apple id'],
  amazon:     ['aws', 'amazon web'],
  proton:     ['protonmail'],
  facebook:   ['meta', 'fb'],
  x:          ['twitter'],
  epic:       ['epicgames', 'epic games'],
  xbox:       ['xbox live'],
  dropbox:    ['dropbox'],
  binance:    ['binance'],
  telegram:   ['t.me', 'web.telegram', 'telegram web']
};

function _guessPlatform(hay) {
  const raw = String(hay || '').trim();
  const s = raw.toLowerCase();
  if (!s) return 'custom';
  if (PLATFORMS[s]) return s;                        // key 精确命中
  for (const k of Object.keys(PLATFORMS)) {
    if (k === 'custom' || k.length < 3) continue;    // 单字符 key（x）不走模糊匹配
    const label = String(PLATFORMS[k].label || '').toLowerCase();
    if (s.includes(k) || (label.length >= 3 && s.includes(label))) return k;
  }
  for (const k of Object.keys(PLATFORM_ALIASES)) {
    if (PLATFORM_ALIASES[k].some(w => s.includes(w))) return k;
  }
  if (s.includes('steam')) return 'steam';
  return 'custom';
}

function _readOtpParams(b) {
  let secret = null, name = '', issuer = '', algo = 1, digits = 1, type = 2;
  const r = _pbReader(b);
  const dec = new TextDecoder();
  while (r.more()) {
    const key = r.varint();
    const wire = key & 7, field = key >>> 3;
    if (wire === 2) {
      const v = r.bytes(r.varint());
      if (field === 1) secret = v;
      else if (field === 2) name = dec.decode(v);
      else if (field === 3) issuer = dec.decode(v);
    } else if (wire === 0) {
      const v = r.varint();
      if (field === 4) algo = v;
      else if (field === 5) digits = v;
      else if (field === 6) type = v;
    } else {
      r.skip(wire);
    }
  }
  if (!secret || !secret.length) return null;
  if (type === 1) return null;            // HOTP 是计数器型，本应用只做 TOTP
  const secretB32 = b32encode(secret);
  if (!isValidSecret(secretB32)) return null;

  const platform = _guessPlatform(issuer + ' ' + name);
  const isSteam = platform === 'steam';
  const algoName = algo === 2 ? 'SHA256' : algo === 3 ? 'SHA512' : 'SHA1';
  return {
    platform,
    issuer: (issuer || PLATFORMS[platform].label).trim(),
    account: (name.includes(':') ? name.split(':').slice(1).join(':') : name).trim(),
    secret: secretB32,
    digits: isSteam ? 5 : (digits === 2 ? 8 : 6),
    period: isSteam ? 30 : (PLATFORMS[platform].period || 30),
    algo: isSteam ? 'SHA1' : algoName,
    charset: isSteam ? 'steam' : 'numeric'
  };
}

function parseOtpMigration(uri) {
  const t = String(uri).trim();
  if (!/^otpauth-migration:\/\//i.test(t)) return [];
  const qi = t.indexOf('?');
  if (qi < 0) return [];
  let data = '';
  for (const pair of t.slice(qi + 1).split('&')) {
    const i = pair.indexOf('=');
    if (i <= 0) continue;
    if (pair.slice(0, i).toLowerCase() === 'data') { data = pair.slice(i + 1); break; }
  }
  if (!data) return [];
  const bytes = _b64UrlBytes(data);
  if (!bytes) return [];

  const out = [];
  const rd = _pbReader(bytes);
  while (rd.more()) {
    const key = rd.varint();
    const wire = key & 7, field = key >>> 3;
    if (wire === 2) {
      const payload = rd.bytes(rd.varint());
      if (field === 1) {
        const p = _readOtpParams(payload);
        if (p) out.push(p);
      }
    } else {
      rd.skip(wire);
    }
  }
  return out;
}

/* 统一解析：migration 串 / otpauth 链接 / 裸 Base32，按 secret 去重 */
function parseAnyOtp(text) {
  const out = [], seen = new Set();
  const push = p => {
    if (!p || !p.secret) return;
    const k = String(p.secret).toUpperCase();
    if (seen.has(k)) return;
    seen.add(k);
    out.push(p);
  };
  let s = String(text || '');
  s = s.replace(/&amp;/g, '&').replace(/&#38;/g, '&').replace(/&quot;/g, '"').replace(/\\\//g, '/');

  for (const m of s.match(/otpauth-migration:\/\/[^\s"'<>\\]+/gi) || []) {
    for (const p of parseOtpMigration(m)) push(p);
  }
  for (const m of s.match(/otpauth:\/\/[^\s"'<>\\]+/gi) || []) {
    push(parseOtpAuth(m));
  }
  if (!out.length) {
    const cand = s.replace(/[\s\-=]/g, '').toUpperCase();
    if (/^[A-Z2-7]{16,128}$/.test(cand) && /[A-Z]/.test(cand) && isValidSecret(cand)) {
      push({
        platform: 'custom', issuer: '自定义', account: '', secret: cand,
        digits: 6, period: 30, algo: 'SHA1', charset: 'numeric'
      });
    }
  }
  return out;
}

/* 把解析结果去重入库 */
async function addParsedTokens(list) {
  if (!Array.isArray(list) || !list.length) { toast('没识别到有效密钥', 'err'); return 0; }
  let added = 0;
  for (const p of list) {
    const key = String(p.secret).toUpperCase();
    if (state.entries.some(x => x.type === 'code' && x.secret && x.secret.toUpperCase() === key)) continue;
    state.entries.push(normalizeEntry({
      id: uid(), type: 'code',
      platform: p.platform || 'custom',
      name: (p.issuer || PLATFORMS[p.platform]?.label || '自定义'),
      account: p.account || '',
      secret: p.secret,
      digits: p.digits || 6,
      period: p.period || 30,
      algo: p.algo || 'SHA1',
      charset: p.charset || 'numeric',
      createdAt: Date.now()
    }));
    added++;
  }
  await persist();
  renderList();
  haptic(12);
  toast(added ? `已导入 ${added} 个令牌` : '这些令牌已经在库里了', added ? 'ok' : 'info');
  return added;
}

/* ───────────────── 加密存储 ───────────────── */
const DB_KEY   = 'tokenvault_v1';
const CFG_KEY  = 'tokenvault_cfg_v1';
const PBKDF2_ITER = 250000;

const cryptoBox = {
  _key: null,

  async deriveKey(pin, saltB64) {
    const enc = new TextEncoder();
    const salt = saltB64 ? b64ToBytes(saltB64) : crypto.getRandomValues(new Uint8Array(16));
    const base = await crypto.subtle.importKey('raw', enc.encode(pin), 'PBKDF2', false, ['deriveKey']);
    const key = await crypto.subtle.deriveKey(
      { name: 'PBKDF2', salt, iterations: PBKDF2_ITER, hash: 'SHA-256' },
      base, { name: 'AES-GCM', length: 256 }, false, ['encrypt', 'decrypt']
    );
    return { key, saltB64: bytesToB64(salt) };
  },

  async encrypt(obj) {
    const iv = crypto.getRandomValues(new Uint8Array(12));
    const data = new TextEncoder().encode(JSON.stringify(obj));
    const ct = await crypto.subtle.encrypt({ name: 'AES-GCM', iv }, this._key, data);
    return { iv: bytesToB64(iv), ct: bytesToB64(new Uint8Array(ct)) };
  },

  async decrypt(box) {
    const iv = b64ToBytes(box.iv);
    const ct = b64ToBytes(box.ct);
    const pt = await crypto.subtle.decrypt({ name: 'AES-GCM', iv }, this._key, ct);
    return JSON.parse(new TextDecoder().decode(pt));
  }
};

function bytesToB64(bytes) {
  let bin = '';
  const chunk = 0x8000;
  for (let i = 0; i < bytes.length; i += chunk) {
    bin += String.fromCharCode.apply(null, bytes.subarray(i, i + chunk));
  }
  return btoa(bin);
}
function b64ToBytes(b64) {
  const bin = atob(b64);
  const out = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i);
  return out;
}

/* ───────────────── 纯 JS SHA-1 / HMAC（无 WebCrypto 时的后备） ───────────────── */
function sha1Bytes(msg) {
  const ml = msg.length;
  const total = (((ml + 9) + 63) >> 6) << 6;
  const buf = new Uint8Array(total);
  buf.set(msg); buf[ml] = 0x80;
  const dv = new DataView(buf.buffer);
  const bitLen = ml * 8;
  dv.setUint32(total - 8, Math.floor(bitLen / 4294967296), false);
  dv.setUint32(total - 4, bitLen >>> 0, false);

  let h0 = 0x67452301, h1 = 0xEFCDAB89, h2 = 0x98BADCFE, h3 = 0x10325476, h4 = 0xC3D2E1F0;
  const w = new Int32Array(80);

  for (let i = 0; i < total; i += 64) {
    for (let j = 0; j < 16; j++) w[j] = dv.getInt32(i + j * 4, false);
    for (let j = 16; j < 80; j++) {
      const x = w[j - 3] ^ w[j - 8] ^ w[j - 14] ^ w[j - 16];
      w[j] = (x << 1) | (x >>> 31);
    }
    let a = h0, b = h1, c = h2, d = h3, e = h4;
    for (let j = 0; j < 80; j++) {
      let f, k;
      if (j < 20)      { f = (b & c) | (~b & d);          k = 0x5A827999; }
      else if (j < 40) { f = b ^ c ^ d;                   k = 0x6ED9EBA1; }
      else if (j < 60) { f = (b & c) | (b & d) | (c & d); k = 0x8F1BBCDC; }
      else             { f = b ^ c ^ d;                   k = 0xCA62C1D6; }
      const t = ((((a << 5) | (a >>> 27)) + f + e + k + w[j]) | 0);
      e = d; d = c; c = (b << 30) | (b >>> 2); b = a; a = t;
    }
    h0 = (h0 + a) | 0; h1 = (h1 + b) | 0; h2 = (h2 + c) | 0;
    h3 = (h3 + d) | 0; h4 = (h4 + e) | 0;
  }
  const out = new Uint8Array(20);
  const ov = new DataView(out.buffer);
  ov.setInt32(0, h0, false); ov.setInt32(4, h1, false); ov.setInt32(8, h2, false);
  ov.setInt32(12, h3, false); ov.setInt32(16, h4, false);
  return out;
}

function hmacSha1(key, msg) {
  let k = key;
  if (k.length > 64) k = sha1Bytes(k);
  const base = new Uint8Array(64);
  base.set(k);
  const inner = new Uint8Array(64 + msg.length);
  const outer = new Uint8Array(64 + 20);
  for (let i = 0; i < 64; i++) { inner[i] = base[i] ^ 0x36; outer[i] = base[i] ^ 0x5c; }
  inner.set(msg, 64);
  outer.set(sha1Bytes(inner), 64);
  return sha1Bytes(outer);
}

/* ───────────────── 运行环境 ───────────────── */
const HAS_SUBTLE = (() => {
  try { return !!(window.isSecureContext && crypto && crypto.subtle && crypto.subtle.importKey); }
  catch (_) { return false; }
})();
const HAS_CRYPTO = !!(crypto && crypto.getRandomValues);

/* ───────────────── 配置 ───────────────── */
const CFG_DEFAULTS = {
  theme: 'dark',
  showRing: true,
  autoLock: 0,
  clipClear: 20,
  hideCode: false,
  hasVault: false,
  salt: '',
  bio: false,      // 是否启用指纹 / 面容解锁
  bioId: '',       // WebAuthn credential rawId（base64url）
  bioPin: '',      // 被包裹的访问密码（iv.ct）
  bioKey: ''       // 包裹密钥（设备支持 PRF 时不用它）
};
let cfg = { ...CFG_DEFAULTS };

function loadCfg() {
  try {
    const raw = localStorage.getItem(CFG_KEY);
    if (raw) cfg = { ...CFG_DEFAULTS, ...JSON.parse(raw) };
  } catch (_) {}
}
function saveCfg() {
  try { localStorage.setItem(CFG_KEY, JSON.stringify(cfg)); } catch (_) {}
}
function applyTheme() {
  const t = cfg.theme === 'auto'
    ? (matchMedia('(prefers-color-scheme: light)').matches ? 'light' : 'dark')
    : cfg.theme;
  document.documentElement.setAttribute('data-theme', t);
  const meta = document.querySelector('meta[name="theme-color"]');
  if (meta) meta.setAttribute('content', t === 'light' ? '#f6f7fb' : '#08090d');
}

/* ───────────────── 应用状态 ───────────────── */
const state = {
  entries: [],
  filter: 'all',
  query: '',
  tab: 'vault',
  editingId: null,
  editorKind: 'code',
  editorPlatform: 'google',
  unlocked: false,
  hiddenAt: 0,
  codeCache: new Map(),
  revealed: new Set(),
  detailId: null
};

let uidCounter = 0;
function uid() {
  uidCounter++;
  return 'e' + Date.now().toString(36) + uidCounter.toString(36) + Math.random().toString(36).slice(2, 6);
}

/* ───────────────── 持久化 ───────────────── */
async function persist() {
  if (!HAS_SUBTLE) { await persistFallback(state.entries); return true; }
  if (!cryptoBox._key) return false;
  try {
    const box = await cryptoBox.encrypt(state.entries);
    localStorage.setItem(DB_KEY, JSON.stringify(box));
    return true;
  } catch (e) {
    toast('保存失败：' + e.message, 'err');
    return false;
  }
}

async function loadVault() {
  const raw = localStorage.getItem(DB_KEY);
  if (!raw) { state.entries = []; return true; }
  try {
    const box = JSON.parse(raw);
    const arr = await cryptoBox.decrypt(box);
    state.entries = Array.isArray(arr) ? arr.map(normalizeEntry) : [];
    return true;
  } catch (_) { return false; }
}

function normalizeEntry(e) {
  const p = PLATFORMS[e.platform] ? e.platform : 'custom';
  const def = PLATFORMS[p];
  return {
    id: e.id || uid(),
    type: e.type === 'note' ? 'note' : 'code',
    platform: p,
    name: (e.name || def.label || '未命名').trim(),
    account: (e.account || '').trim(),
    secret: (e.secret || '').toUpperCase().replace(/[\s\-]/g, ''),
    note: e.note || '',
    digits: [5, 6, 8].includes(+e.digits) ? +e.digits : def.digits,
    period: [30, 60].includes(+e.period) ? +e.period : def.period,
    algo: ['SHA1', 'SHA256', 'SHA512'].includes(e.algo) ? e.algo : def.algo,
    charset: e.charset === 'steam' || (p === 'steam') ? 'steam' : 'numeric',
    favorite: !!e.favorite,
    createdAt: e.createdAt || Date.now()
  };
}

/* ───────────────── 渲染：列表 ───────────────── */
function iconTile(platform, size) {
  const def = PLATFORMS[platform] || PLATFORMS.custom;
  return {
    svg: ICONS[platform] || ICONS.custom,
    style: `--icon-bg:${def.bg};`
  };
}

function ringSvg(percent) {
  const C = 94.25; // 2πr, r=15
  const off = C * (1 - clamp(percent, 0, 1));
  return `<svg viewBox="0 0 36 36">
    <circle class="ring-bg" cx="18" cy="18" r="15"/>
    <circle class="ring-fg" cx="18" cy="18" r="15"
            stroke-dasharray="${C}" stroke-dashoffset="${off}"/>
  </svg>`;
}

function filteredEntries() {
  let arr = state.entries.slice();
  if (state.filter === 'favorite') arr = arr.filter(e => e.favorite);
  if (state.filter === 'code') arr = arr.filter(e => e.type === 'code');
  if (state.filter === 'note') arr = arr.filter(e => e.type === 'note');
  const q = state.query.trim().toLowerCase();
  if (q) {
    arr = arr.filter(e =>
      (e.name || '').toLowerCase().includes(q) ||
      (e.account || '').toLowerCase().includes(q) ||
      (PLATFORMS[e.platform]?.label || '').toLowerCase().includes(q));
  }
  arr.sort((a, b) => {
    if (a.favorite !== b.favorite) return a.favorite ? -1 : 1;
    return (b.createdAt || 0) - (a.createdAt || 0);
  });
  return arr;
}

function renderList() {
  const list = $('#list');
  const emptyEl = $('#emptyState');
  const items = filteredEntries();

  $('#vaultCount').textContent = state.entries.length;
  $('#eyebrow').textContent = state.query ? '搜索结果' : (
    state.filter === 'favorite' ? '收藏' :
    state.filter === 'code' ? '动态码' :
    state.filter === 'note' ? '密钥本' : '全部令牌');

  if (!items.length) {
    list.innerHTML = '';
    emptyEl.hidden = false;
    const t = $('#emptyTitle'), d = $('#emptyDesc');
    if (state.query) { t.textContent = '没有匹配项'; d.textContent = '换个关键词试试，或用平台名搜索'; }
    else if (state.filter !== 'all') { t.textContent = '这个分类还是空的'; d.textContent = '点右下角 + 添加，或切换回「全部」'; }
    else { t.textContent = '还没有令牌'; d.textContent = '点击右下角 + 添加你的第一个验证器'; }
    return;
  }
  emptyEl.hidden = true;

  const frag = document.createDocumentFragment();
  let lastFavGroup = null;
  items.forEach((e, i) => {
    if (e.favorite && lastFavGroup === null && !state.filter.startsWith('fav')) {
      frag.appendChild(el('div', 'group-label', '收藏'));
      lastFavGroup = true;
    }
    const pf = iconTile(e.platform);
    const card = el('div', 'card' + (e.favorite ? ' is-fav' : ''));
    card.dataset.id = e.id;
    card.style.animationDelay = Math.min(i * 34, 320) + 'ms';

    let rightHtml;
    if (e.type === 'code') {
      const masked = cfg.hideCode && !state.revealed.has(e.id);
      rightHtml = `<div class="card-code${masked ? ' masked' : ''}" data-code="${e.id}">${codeMarkup(null, masked)}</div>
        ${cfg.showRing ? `<div class="ring" data-ring="${e.id}">${ringSvg(1)}</div>` : ''}`;
    } else {
      rightHtml = `<div class="card-code note-code">密钥本 ›</div>`;
    }

    card.innerHTML = `
      <div class="pf-icon" style="${pf.style}">${pf.svg}</div>
      <div class="card-body">
        <div class="card-name">${esc(e.name)}</div>
        <div class="card-acct">${esc(e.account || (e.type === 'note' ? '点击查看内容' : '未填账号'))}</div>
      </div>
      <div class="card-right">${rightHtml}</div>`;
    frag.appendChild(card);
  });

  list.innerHTML = '';
  list.appendChild(frag);

  // 立即填充缓存中的验证码，避免闪烁
  for (const e of items) {
    if (e.type !== 'code') continue;
    const c = state.codeCache.get(e.id);
    if (c) updateCardCode(e.id, c.code, c.remaining, c.period);
  }
  tick();
}

function esc(s) {
  return String(s == null ? '' : s)
    .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
}

function updateCardCode(id, code, remaining, period) {
  const box = document.querySelector(`[data-code="${id}"]`);
  if (box) {
    const masked = cfg.hideCode && !state.revealed.has(id);
    box.innerHTML = codeMarkup(code, masked);
    box.classList.toggle('masked', masked);
    box.classList.toggle('warn', !masked && remaining <= Math.min(7, period * 0.25) && remaining > 3);
    box.classList.toggle('danger', !masked && remaining <= 3);
  }
  updateRing(id, remaining, period);
}

function updateRing(id, remaining, period) {
  const ring = document.querySelector(`[data-ring="${id}"]`);
  if (!ring) return;
  const fg = ring.querySelector('.ring-fg');
  if (!fg) return;
  const p = clamp(remaining / period, 0, 1);
  const C = 94.25;
  fg.setAttribute('stroke-dashoffset', String(C * (1 - p)));
  ring.classList.toggle('warn', remaining <= Math.min(7, period * 0.25) && remaining > 3);
  ring.classList.toggle('danger', remaining <= 3);
}

/* ───────────────── 定时器 ───────────────── */
let _timer = null;

function startTicker() {
  if (_timer) return;
  _timer = setInterval(tick, 250);
  tick();
}
function stopTicker() {
  if (_timer) { clearInterval(_timer); _timer = null; }
}

async function tick() {
  if (!state.unlocked) return;
  const nowMs = Date.now();
  const nowSec = Math.floor(nowMs / 1000);
  const pending = [];

  for (const e of state.entries) {
    if (e.type !== 'code' || !e.secret) continue;
    const period = e.period || 30;
    const counter = Math.floor(nowSec / period);
    const cached = state.codeCache.get(e.id);
    const remaining = period - (nowSec % period);

    if (!cached || cached.counter !== counter) {
      pending.push((async () => {
        try {
          const r = await totp(e.secret, e);
          state.codeCache.set(e.id, { code: r.code, counter, remaining: r.remaining, period });
          updateCardCode(e.id, r.code, r.remaining, period);
        } catch (err) {
          state.codeCache.set(e.id, { code: 'ERROR', counter, remaining, period });
          const box = document.querySelector(`[data-code="${e.id}"]`);
          if (box) { box.textContent = '密钥无效'; box.classList.add('danger'); }
        }
      })());
    } else {
      cached.remaining = remaining;
      updateCardCode(e.id, cached.code, remaining, period);
    }
  }

  if (state.detailId) updateDetailLive();
  if (pending.length) await Promise.allSettled(pending);
}

/* ───────────────── Toast / Modal ───────────────── */
const TOAST_ICONS = {
  ok:   '<svg viewBox="0 0 24 24"><path d="M20 6 9 17l-5-5"/></svg>',
  err:  '<svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="9"/><path d="M12 8v5M12 16.2v.1"/></svg>',
  info: '<svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="9"/><path d="M12 11v5M12 7.8v.1"/></svg>'
};

function toast(msg, kind = 'ok', ms = 1900) {
  const stack = $('#toastStack');
  const t = el('div', 'toast ' + kind, TOAST_ICONS[kind] + '<span>' + esc(msg) + '</span>');
  stack.appendChild(t);
  setTimeout(() => {
    t.classList.add('is-out');
    setTimeout(() => t.remove(), 280);
  }, ms);
  while (stack.children.length > 3) stack.firstChild.remove();
}

let _modalResolve = null;
function showModal({ title, body = '', input = false, placeholder = '', value = '',
                    okText = '确定', cancelText = '取消', danger = false }) {
  $('#modalTitle').textContent = title;
  $('#modalBody').textContent = body;
  $('#modalBody').hidden = !body;
  $('#modalInputWrap').hidden = !input;
  $('#modalInput').placeholder = placeholder;
  $('#modalInput').value = value;
  $('#modalOk').textContent = okText;
  $('#modalCancel').textContent = cancelText;
  $('#modalOk').className = 'btn ' + (danger ? 'btn-danger-ghost' : 'btn-primary');
  $('#modalScrim').hidden = false;

  return new Promise(resolve => {
    _modalResolve = resolve;
    if (input) setTimeout(() => $('#modalInput').focus(), 120);
  });
}
function closeModal(result) {
  $('#modalScrim').hidden = true;
  if (_modalResolve) { const r = _modalResolve; _modalResolve = null; r(result); }
}

/* ───────────────── 剪贴板 ───────────────── */
let _clipTimer = null;
async function copyText(text, label = '已复制') {
  try {
    if (navigator.clipboard && window.isSecureContext) {
      await navigator.clipboard.writeText(text);
    } else {
      const ta = document.createElement('textarea');
      ta.value = text;
      ta.style.cssText = 'position:fixed;opacity:0;left:-9999px';
      document.body.appendChild(ta);
      ta.select();
      document.execCommand('copy');
      ta.remove();
    }
    haptic(12);
    toast(label, 'ok');
    const sec = +cfg.clipClear;
    if (sec > 0) {
      clearTimeout(_clipTimer);
      _clipTimer = setTimeout(async () => {
        try {
          if (navigator.clipboard && window.isSecureContext) {
            const cur = await navigator.clipboard.readText().catch(() => null);
            if (cur === text) await navigator.clipboard.writeText('');
          }
        } catch (_) {}
      }, sec * 1000);
    }
    return true;
  } catch (e) {
    toast('复制失败，请长按选择', 'err');
    return false;
  }
}

/* ───────────────── 详情面板 ───────────────── */
function openDetail(id) {
  const e = state.entries.find(x => x.id === id);
  if (!e) return;
  state.detailId = id;

  const pf = iconTile(e.platform);
  const iconBox = $('#detailIcon');
  iconBox.style.cssText = pf.style;
  iconBox.innerHTML = pf.svg;
  $('#detailName').textContent = e.name;
  $('#detailAccount').textContent = e.account || (e.type === 'note' ? '密钥笔记' : '未填账号');

  const codeWrap = $('#detailCodeWrap');
  codeWrap.hidden = e.type !== 'code';
  $('#detailHint').hidden = e.type !== 'code';
  $('#detailCopySecret').hidden = e.type === 'note' || !e.secret;

  const rows = $('#detailRows');
  rows.innerHTML = '';
  const addRow = (k, v, isNote) => {
    if (v == null || v === '') return;
    const r = el('div', 'detail-row');
    r.innerHTML = `<span>${esc(k)}</span><b class="${isNote ? 'note-val' : ''}">${esc(v)}</b>`;
    rows.appendChild(r);
  };

  if (e.type === 'code') {
    addRow('平台', PLATFORMS[e.platform].label);
    addRow('算法', e.algo + ' · ' + e.period + 's · ' + e.digits + '位');
    addRow('密钥', formatSecret(e.secret));
  } else {
    addRow('类型', '密钥笔记');
    addRow('内容', e.note, true);
  }
  addRow('创建于', new Date(e.createdAt).toLocaleDateString('zh-CN'));

  $('#detailScrim').hidden = false;
  $('#detailSheet').hidden = false;
  updateDetailLive();
}

function formatSecret(s) {
  return (s || '').replace(/(.{4})/g, '$1 ').trim();
}

function updateDetailLive() {
  const id = state.detailId;
  if (!id) return;
  const e = state.entries.find(x => x.id === id);
  if (!e || e.type !== 'code') return;
  const c = state.codeCache.get(id);
  const period = e.period || 30;
  const nowSec = Math.floor(Date.now() / 1000);
  const remaining = period - (nowSec % period);

  if (c) {
    const codeEl = $('#detailCode');
    codeEl.innerHTML = codeMarkup(c.code, false);
    codeEl.classList.toggle('warn', remaining <= Math.min(7, period * 0.25) && remaining > 3);
    codeEl.classList.toggle('danger', remaining <= 3);
  }
  $('#detailRemain').textContent = remaining;
  const p = clamp(remaining / period, 0, 1);
  const C = 113.1; // 2πr, r=18
  const fg = $('#detailRingFg');
  if (fg) {
    fg.setAttribute('stroke-dasharray', String(C));
    fg.setAttribute('stroke-dashoffset', String(C * (1 - p)));
  }
  const ring = $('#detailRing');
  ring.classList.toggle('warn', remaining <= Math.min(7, period * 0.25) && remaining > 3);
  ring.classList.toggle('danger', remaining <= 3);
  const bar = $('#detailBar');
  if (bar) bar.style.transform = `scaleX(${p})`;
}

function closeSheet(scrimId, sheetId) {
  const sheet = $('#' + sheetId);
  const scrim = $('#' + scrimId);
  if (!sheet || sheet.hidden) return;
  sheet.classList.add('is-closing');
  scrim.style.transition = 'opacity .28s';
  scrim.style.opacity = '0';
  setTimeout(() => {
    sheet.hidden = true;
    sheet.classList.remove('is-closing');
    scrim.hidden = true;
    scrim.style.opacity = '';
    scrim.style.transition = '';
  }, 280);
  if (sheetId === 'detailSheet') state.detailId = null;
}

/* ───────────────── 编辑器 ───────────────── */
function buildPlatformGrid() {
  const grid = $('#platformGrid');
  grid.innerHTML = '';
  Object.keys(PLATFORMS).forEach(key => {
    const b = el('button', 'pf-opt');
    b.type = 'button';
    b.dataset.pf = key;
    b.title = PLATFORMS[key].label;
    // 用平台底色做图标，保证浅色主题下也可见
    const def = PLATFORMS[key];
    b.innerHTML = `<span style="display:grid;place-items:center;width:34px;height:34px;border-radius:10px;background:${def.bg}">${
      (ICONS[key] || ICONS.custom).replace('<svg', '<svg style="width:20px;height:20px"')
    }</span>`;
    grid.appendChild(b);
  });
  grid.addEventListener('click', ev => {
    const btn = ev.target.closest('.pf-opt');
    if (!btn) return;
    selectPlatform(btn.dataset.pf);
    haptic(6);
  });
}

function selectPlatform(key) {
  state.editorPlatform = key;
  $$('#platformGrid .pf-opt').forEach(b => b.classList.toggle('is-on', b.dataset.pf === key));
  const def = PLATFORMS[key];
  if (key !== 'custom') {
    const nameInput = $('#fName');
    if (!nameInput.value.trim() || Object.values(PLATFORMS).some(p => p.label === nameInput.value.trim())) {
      nameInput.value = def.label;
    }
    $('#fDigits').value = String(def.digits);
    $('#fPeriod').value = String(def.period);
    $('#fAlgo').value = def.algo;
  }
}

function setEditorKind(kind) {
  state.editorKind = kind;
  $$('#editorSeg button').forEach(b => b.classList.toggle('is-on', b.dataset.kind === kind));
  $('#secretField').hidden = kind !== 'code';
  $('#noteField').hidden = kind !== 'note';
  $('#advToggle').hidden = kind !== 'code';
  if (kind !== 'code') { $('#advBody').hidden = true; $('#advToggle').classList.remove('is-open'); }
}

function openEditor(id = null) {
  state.editingId = id;
  const isEdit = !!id;
  $('#editorTitle').textContent = isEdit ? '编辑令牌' : '添加令牌';

  const e = isEdit ? state.entries.find(x => x.id === id) : null;
  state.editorPlatform = e ? e.platform : 'google';

  $('#fName').value = e ? e.name : '';
  $('#fAccount').value = e ? e.account : '';
  $('#fSecret').value = e ? e.secret : '';
  $('#fNote').value = e ? e.note : '';
  $('#fDigits').value = String(e ? e.digits : 6);
  $('#fPeriod').value = String(e ? e.period : 30);
  $('#fAlgo').value = e ? e.algo : 'SHA1';
  $('#advBody').hidden = true;
  $('#advToggle').classList.remove('is-open');

  setEditorKind(e ? e.type : 'code');
  selectPlatform(state.editorPlatform);
  $$('#platformGrid .pf-opt').forEach(b => b.classList.toggle('is-on', b.dataset.pf === state.editorPlatform));

  $('#editorScrim').hidden = false;
  $('#editorSheet').hidden = false;
  if (!isEdit) setTimeout(() => $('#fName').focus(), 320);
}

async function saveEditor() {
  const kind = state.editorKind;
  const err = m => { toast(m, 'err'); return false; };

  if (kind === 'code') {
    const name = $('#fName').value.trim();
    if (!name) return err('请填写名称');
    const rawSecret = $('#fSecret').value.trim();
    if (!rawSecret) return err('请填写密钥');
    if (!isValidSecret(rawSecret)) return err('密钥格式不正确（应为 Base32）');
    const platform = state.editorPlatform;
    const item = normalizeEntry({
      id: state.editingId || uid(),
      type: 'code',
      platform,
      name,
      account: $('#fAccount').value.trim(),
      secret: rawSecret,
      digits: +$('#fDigits').value,
      period: +$('#fPeriod').value,
      algo: $('#fAlgo').value,
      charset: platform === 'steam' ? 'steam' : 'numeric',
      favorite: state.editingId ? (state.entries.find(x => x.id === state.editingId)?.favorite || false) : false,
      createdAt: state.editingId ? (state.entries.find(x => x.id === state.editingId)?.createdAt || Date.now()) : Date.now()
    });
    // 先验证能算出码
    try { await totp(item.secret, item); } catch (ex) { return err('密钥无法生成验证码，请检查'); }
    upsertEntry(item);
  } else {
    const name = $('#fName').value.trim();
    const note = $('#fNote').value;
    if (!name) return err('请填写名称');
    if (!note.trim()) return err('请填写内容');
    const item = normalizeEntry({
      id: state.editingId || uid(),
      type: 'note',
      platform: state.editorPlatform,
      name,
      account: $('#fAccount').value.trim(),
      note,
      favorite: state.editingId ? (state.entries.find(x => x.id === state.editingId)?.favorite || false) : false,
      createdAt: state.editingId ? (state.entries.find(x => x.id === state.editingId)?.createdAt || Date.now()) : Date.now()
    });
    upsertEntry(item);
  }

  await persist();
  closeSheet('editorScrim', 'editorSheet');
  renderList();
  toast(state.editingId ? '已更新' : '已添加', 'ok');
  haptic(14);
  state.editingId = null;
  return true;
}

function upsertEntry(item) {
  const i = state.entries.findIndex(x => x.id === item.id);
  if (i >= 0) state.entries[i] = item; else state.entries.push(item);
  state.codeCache.delete(item.id);
}

/* ───────────────── 指纹 / 面容解锁（WebAuthn platform authenticator） ───────────────── */
/* 说明：浏览器不允许网页直接读指纹，只能用系统弹窗做「本地用户在场验证」。
   因此做法是：开启时把访问密码用 AES-GCM 包一层存本机，验证通过后解出来走正常解锁。
   支持 WebAuthn PRF 扩展的设备（Chrome / Edge / Safari 17+）用 PRF 结果派生包裹密钥，
   密钥本身不落盘；不支持时退回用本机随机 key（等价于「把密码加密存在这台设备」）。 */
const BIO_PRF_SALT = new TextEncoder().encode('tokenvault/biometric/v1');
let currentPin = '';   // 仅在本次已解锁会话内保存，锁屏即清空

function bioSupported() {
  return typeof PublicKeyCredential !== 'undefined'
    && typeof navigator.credentials !== 'undefined'
    && window.isSecureContext === true;
}

async function bioAvailable() {
  if (!bioSupported()) return false;
  try {
    if (typeof PublicKeyCredential.isUserVerifyingPlatformAuthenticatorAvailable !== 'function') return false;
    return await PublicKeyCredential.isUserVerifyingPlatformAuthenticatorAvailable();
  } catch (_) { return false; }
}

const _bioB64u = buf => bytesToB64(new Uint8Array(buf))
  .replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
const _bioUnb64u = s => b64ToBytes(String(s).replace(/-/g, '+').replace(/_/g, '/'));

function _bioPrfOf(cred) {
  try {
    const ext = cred && cred.getClientExtensionResults ? cred.getClientExtensionResults() : null;
    const first = ext && ext.prf && ext.prf.results && ext.prf.results.first;
    return first || null;
  } catch (_) { return null; }
}

/* 包裹密钥：PRF 优先，否则本机随机 key */
async function _bioWrapKey(prfFirst, create) {
  if (prfFirst) {
    const h = await crypto.subtle.digest('SHA-256', prfFirst);
    return crypto.subtle.importKey('raw', h, 'AES-GCM', false, ['encrypt', 'decrypt']);
  }
  if (!cfg.bioKey && create) {
    cfg.bioKey = bytesToB64(crypto.getRandomValues(new Uint8Array(32)));
  }
  if (!cfg.bioKey) return null;
  return crypto.subtle.importKey('raw', b64ToBytes(cfg.bioKey), 'AES-GCM', false, ['encrypt', 'decrypt']);
}

async function _bioSeal(pin, key) {
  const iv = crypto.getRandomValues(new Uint8Array(12));
  const ct = await crypto.subtle.encrypt({ name: 'AES-GCM', iv }, key, new TextEncoder().encode(pin));
  return bytesToB64(iv) + '.' + bytesToB64(new Uint8Array(ct));
}

async function _bioUnseal(sealed, key) {
  const parts = String(sealed).split('.');
  const pt = await crypto.subtle.decrypt(
    { name: 'AES-GCM', iv: b64ToBytes(parts[0]) }, key, b64ToBytes(parts[1])
  );
  return new TextDecoder().decode(pt);
}

/* 锁屏上的指纹按钮只在「已开启 + 设备支持」时出现 */
async function refreshBioButton() {
  const btn = $('#bioBtn');
  if (!btn) return;
  let on = false;
  if (cfg.bio) on = await bioAvailable();
  btn.hidden = !on;
}

async function enableBiometric() {
  if (!HAS_SUBTLE) { toast('当前打开方式不支持加密接口', 'err'); return; }
  if (!await bioAvailable()) { toast('这台设备没有可用的指纹或面容', 'err'); return; }
  let pin = currentPin;
  if (!pin) {
    pin = await showModal({
      title: '验证访问密码',
      body: '开启指纹解锁前，请再输一次当前访问密码',
      input: true,
      placeholder: '6 位访问密码',
      okText: '继续'
    });
    if (!pin) return;
  }
  try {
    const cred = await navigator.credentials.create({
      publicKey: {
        challenge: crypto.getRandomValues(new Uint8Array(32)),
        rp: { name: 'TokenVault' },
        user: {
          id: crypto.getRandomValues(new Uint8Array(16)),
          name: 'tokenvault-local',
          displayName: '本机令牌库'
        },
        pubKeyCredParams: [{ type: 'public-key', alg: -7 }, { type: 'public-key', alg: -257 }],
        authenticatorSelection: {
          authenticatorAttachment: 'platform',
          userVerification: 'required',
          residentKey: 'preferred',
          requireResidentKey: false
        },
        timeout: 90000,
        attestation: 'none',
        extensions: { prf: { eval: { first: BIO_PRF_SALT } } }
      }
    });
    if (!cred) return;
    const key = await _bioWrapKey(_bioPrfOf(cred), true);
    if (!key) throw new Error('无法生成包裹密钥');
    cfg.bioId = _bioB64u(cred.rawId);
    cfg.bioPin = await _bioSeal(pin, key);
    cfg.bio = true;
    saveCfg();
    await refreshBioButton();
    if (typeof bindSettingsPage === 'function' && $('#pageHost')) bindSettingsPage($('#pageHost'));
    haptic(14);
    toast('指纹解锁已开启', 'ok');
  } catch (e) {
    if (e && (e.name === 'NotAllowedError' || e.name === 'AbortError')) return;
    toast('开启失败：' + ((e && e.message) || '设备不支持'), 'err');
  }
}

function disableBiometric(silent) {
  const had = !!cfg.bio;
  cfg.bio = false;
  cfg.bioId = '';
  cfg.bioPin = '';
  cfg.bioKey = '';
  saveCfg();
  refreshBioButton();
  if (typeof bindSettingsPage === 'function' && $('#pageHost')) bindSettingsPage($('#pageHost'));
  if (had && !silent) toast('指纹解锁已关闭');
}

async function bioUnlock() {
  if (!cfg.bio || !cfg.bioId) return;
  if (!await bioAvailable()) { toast('这台设备当前不支持指纹或面容', 'err'); return; }
  try {
    const cred = await navigator.credentials.get({
      publicKey: {
        challenge: crypto.getRandomValues(new Uint8Array(32)),
        allowCredentials: [{ id: _bioUnb64u(cfg.bioId), type: 'public-key', transports: ['internal'] }],
        userVerification: 'required',
        timeout: 90000,
        extensions: { prf: { eval: { first: BIO_PRF_SALT } } }
      }
    });
    if (!cred) return;
    const key = await _bioWrapKey(_bioPrfOf(cred), false);
    if (!key) throw new Error('包裹密钥不可用');
    const pin = await _bioUnseal(cfg.bioPin, key);
    const ok = await tryUnlockWithPin(pin);
    if (!ok) {
      disableBiometric(true);
      pinShake('指纹解锁已失效，请用密码进入后重新开启');
    }
  } catch (e) {
    if (e && (e.name === 'NotAllowedError' || e.name === 'AbortError')) return;
    toast('指纹验证失败：' + ((e && e.message) || '未知错误'), 'err');
  }
}

/* 用密码解锁（PIN 与指纹共用） */
async function tryUnlockWithPin(pin) {
  try {
    if (!HAS_SUBTLE) {
      if (!loadFallback(pin)) return false;
    } else {
      const { key } = await cryptoBox.deriveKey(pin, cfg.salt);
      cryptoBox._key = key;
      const ok = await loadVault();
      if (!ok) { cryptoBox._key = null; return false; }
    }
    currentPin = pin;
    await unlock();
    return true;
  } catch (_) {
    cryptoBox._key = null;
    return false;
  }
}

/* ───────────────── 锁屏 ───────────────── */
let pinBuffer = '';
let pinMode = 'unlock';   // unlock | setup | confirm
let firstPin = '';

function renderPinDots() {
  const dots = $$('#pinDots i');
  dots.forEach((d, i) => d.classList.toggle('on', i < pinBuffer.length));
}

function pinShake(msg) {
  $('#pinDots').classList.add('err');
  $('#brandMark').classList.add('shake');
  $('#pinError').textContent = msg || '';
  haptic([18, 60, 18]);
  setTimeout(() => {
    $('#pinDots').classList.remove('err');
    $('#brandMark').classList.remove('shake');
    pinBuffer = '';
    renderPinDots();
  }, 480);
}

async function onPinComplete() {
  const pin = pinBuffer;

  if (pinMode === 'setup') {
    firstPin = pin;
    pinMode = 'confirm';
    pinBuffer = '';
    renderPinDots();
    $('#lockTitle').textContent = '确认密码';
    $('#lockSub').textContent = '再输入一次以确认';
    $('#pinError').textContent = '';
    return;
  }

  if (pinMode === 'confirm') {
    if (pin !== firstPin) {
      pinMode = 'setup';
      firstPin = '';
      $('#lockTitle').textContent = '设置访问密码';
      $('#lockSub').textContent = '6 位数字 · 用于保护你的令牌';
      pinShake('两次输入不一致，请重新设置');
      return;
    }
    // 创建新库
    try {
      if (!HAS_SUBTLE) {
        // 无 WebCrypto：使用可逆混淆（明确降级）
        cryptoBox._key = { __fallback: true };
        cfg.salt = '';
        await persistFallback([]);
      } else {
        const { key, saltB64 } = await cryptoBox.deriveKey(pin);
        cryptoBox._key = key;
        cfg.salt = saltB64;
      }
      cfg.hasVault = true;
      saveCfg();
      state.entries = [];
      currentPin = pin;
      await unlock();
      toast('令牌库已创建', 'ok');
    } catch (e) {
      pinShake('创建失败：' + e.message);
    }
    return;
  }

  // 解锁
  const okPin = await tryUnlockWithPin(pin);
  if (!okPin) pinShake('密码错误');
}

/* 无 WebCrypto 的降级存储（XOR + Base64，仅为让 file:// 可演示） */
function _fallbackKey(pin) {
  let k = 0;
  for (let i = 0; i < pin.length; i++) k = (k * 31 + pin.charCodeAt(i)) >>> 0;
  return String(k);
}
async function persistFallback(arr) {
  if (HAS_SUBTLE) return persist();
  const pin = firstPin || '';
  const key = _fallbackKey(pin);
  const json = JSON.stringify(arr);
  let masked = '';
  for (let i = 0; i < json.length; i++) {
    masked += String.fromCharCode(json.charCodeAt(i) ^ key.charCodeAt(i % key.length));
  }
  localStorage.setItem(DB_KEY, JSON.stringify({ fb: true, k: key, d: bytesToB64(new TextEncoder().encode(masked)) }));
}
function loadFallback(pin) {
  try {
    const raw = localStorage.getItem(DB_KEY);
    if (!raw) { state.entries = []; return true; }
    const box = JSON.parse(raw);
    if (!box.fb) return false;
    const key = _fallbackKey(pin);
    if (box.k !== key) return false;
    const masked = new TextDecoder().decode(b64ToBytes(box.d));
    let json = '';
    for (let i = 0; i < masked.length; i++) {
      json += String.fromCharCode(masked.charCodeAt(i) ^ key.charCodeAt(i % key.length));
    }
    try { state.entries = JSON.parse(json).map(normalizeEntry); }
    catch (_) { state.entries = []; }
    return true;
  } catch (_) { return false; }
}

async function unlock() {
  state.unlocked = true;
  pinBuffer = '';
  renderPinDots();
  const lock = $('#lockScreen');
  lock.classList.add('is-out');
  setTimeout(() => { lock.hidden = true; lock.classList.remove('is-out'); }, 440);
  $('#app').hidden = false;
  renderList();
  startTicker();
  switchTab('vault');
  reloadFallbackHint();
}

function lockApp() {
  if (!state.unlocked) return;
  state.unlocked = false;
  stopTicker();
  state.codeCache.clear();
  state.revealed.clear();
  cryptoBox._key = null;
  $('#app').hidden = true;
  closeSheet('detailScrim', 'detailSheet');
  closeSheet('editorScrim', 'editorSheet');

  pinMode = 'unlock';
  pinBuffer = '';
  renderPinDots();
  $('#lockTitle').textContent = '令牌库';
  $('#lockSub').textContent = '输入访问密码';
  $('#pinError').textContent = '';
  const lock = $('#lockScreen');
  lock.hidden = false;
  currentPin = '';
  refreshBioButton();
  requestAnimationFrame(() => lock.classList.remove('is-out'));
}

function reloadFallbackHint() {
  const foot = $('.lock-foot');
  if (!HAS_SUBTLE && foot) {
    foot.textContent = '⚠ 当前为 file:// 打开，仅 SHA-1 与降级加密生效';
  }
}

/* ───────────────── 标签页 ───────────────── */
/* 顶部标题栏随页面切换 */
function syncTopbar(tab) {
  const isVault = tab === 'vault';
  const title = $('#pageTitle');
  const tn = Array.prototype.find.call(title.childNodes, n => n.nodeType === 3);
  if (tn) tn.nodeValue = isVault ? '令牌库' : (tab === 'generator' ? '安全工具' : '设置');
  $('#vaultCount').hidden = !isVault;
  $('#filterChips').hidden = !isVault;
  $('#searchBtn').hidden = !isVault;
  const gb = $('#grabBtn');
  if (gb) gb.hidden = !isVault;
  if (!isVault) $('#searchbar').hidden = true;
  if (!isVault) $('#eyebrow').textContent = tab === 'generator' ? '离线生成' : '偏好设置';
}

function switchTab(tab) {
  state.tab = tab;
  syncTopbar(tab);
  $$('#tabbar .tab').forEach(b => b.classList.toggle('is-on', b.dataset.tab === tab));
  const list = $('#list');
  const emptyEl = $('#emptyState');
  const old = $('#pageHost');
  if (old) old.remove();

  if (tab === 'vault') {
    list.hidden = false;
    $('#fab').hidden = false;
    renderList();
    return;
  }

  list.hidden = true;
  emptyEl.hidden = true;
  $('#fab').hidden = true;
  const host = el('div', 'page');
  host.id = 'pageHost';
  host.innerHTML = tab === 'generator' ? generatorPageHtml() : settingsPageHtml();
  $('#app').insertBefore(host, $('#tabbar'));
  if (tab === 'generator') bindGeneratorPage(host);
  else bindSettingsPage(host);
}

function generatorPageHtml() {
  return `
    <div class="hero-card">
      <h2>安全工具箱</h2>
      <p>在本地生成高强度口令与 TOTP 密钥，全部离线计算，不产生任何网络请求。</p>
    </div>

    <div class="tool-card">
      <h3>随机口令生成器</h3>
      <p>用于注册新账号时替换弱口令</p>
      <div class="gen-out" id="pwOut">点击下方按钮生成</div>
      <div class="gen-row">
        <button class="mini-btn" id="pwGen">生成</button>
        <button class="mini-btn" id="pwCopy">复制</button>
      </div>
    </div>

    <div class="tool-card">
      <h3>TOTP 密钥生成器</h3>
      <p>Base32 · 160 bit（RFC 4226 推荐长度）</p>
      <div class="gen-out" id="secOut">点击下方按钮生成</div>
      <div class="gen-row">
        <button class="mini-btn" id="secGen">生成</button>
        <button class="mini-btn" id="secCopy">复制</button>
        <button class="mini-btn" id="secUse">直接添加</button>
      </div>
    </div>

    <div class="tool-card">
      <h3>安全提示</h3>
      <p style="line-height:1.85">
        · 令牌数据以 AES-256-GCM 加密后仅存于本机浏览器<br>
        · 访问密码不会被保存，忘记后数据无法恢复<br>
        · 建议定期导出加密备份，并保存在离线介质<br>
        · 本页面不会发起任何网络请求，可断网使用
      </p>
    </div>`;
}

function bindGeneratorPage(host) {
  let lastPw = '', lastSec = '';
  const CH = 'ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#$%^&*-_=+';
  host.querySelector('#pwGen').onclick = () => {
    const buf = new Uint8Array(20);
    crypto.getRandomValues(buf);
    lastPw = Array.from(buf, b => CH[b % CH.length]).join('');
    host.querySelector('#pwOut').textContent = lastPw;
    haptic(8);
  };
  host.querySelector('#pwCopy').onclick = () =>
    lastPw ? copyText(lastPw, '口令已复制') : toast('请先生成', 'info');
  host.querySelector('#secGen').onclick = () => {
    lastSec = randomSecret(20);
    host.querySelector('#secOut').textContent = formatSecret(lastSec);
    haptic(8);
  };
  host.querySelector('#secCopy').onclick = () =>
    lastSec ? copyText(lastSec, '密钥已复制') : toast('请先生成', 'info');
  host.querySelector('#secUse').onclick = () => {
    if (!lastSec) return toast('请先生成', 'info');
    openEditor();
    $('#fSecret').value = lastSec;
    toast('已填入密钥', 'ok');
  };
}

function settingsPageHtml() {
  return `
    <div class="set-group">
      <div class="set-group-label">外观</div>
      <div class="set-row">
        <div class="set-row-main"><span>主题</span></div>
        <div class="seg seg-sm" id="themeSeg">
          <button data-theme="dark">深色</button>
          <button data-theme="light">浅色</button>
          <button data-theme="auto">跟随</button>
        </div>
      </div>
      <div class="set-row">
        <div class="set-row-main"><span>显示倒计时</span><em>卡片右侧环形进度</em></div>
        <label class="switch"><input type="checkbox" id="setRing" ${cfg.showRing ? 'checked' : ''}><i></i></label>
      </div>
      <div class="set-row">
        <div class="set-row-main"><span>隐藏验证码</span><em>点按卡片才显示</em></div>
        <label class="switch"><input type="checkbox" id="setHideCode" ${cfg.hideCode ? 'checked' : ''}><i></i></label>
      </div>
    </div>

    <div class="set-group">
      <div class="set-group-label">安全</div>
      <div class="set-row">
        <div class="set-row-main"><span>指纹 / 面容解锁</span><em id="bioHint">用系统生物识别代替输入密码</em></div>
        <label class="switch"><input type="checkbox" id="setBio" ${cfg.bio ? 'checked' : ''}><i></i></label>
      </div>
      <div class="set-row">
        <div class="set-row-main"><span>自动锁定</span><em>切到后台后多久重新验证</em></div>
        <select id="setAutoLock" class="set-select">
          <option value="0" ${cfg.autoLock == 0 ? 'selected' : ''}>立即</option>
          <option value="30" ${cfg.autoLock == 30 ? 'selected' : ''}>30 秒</option>
          <option value="300" ${cfg.autoLock == 300 ? 'selected' : ''}>5 分钟</option>
          <option value="-1" ${cfg.autoLock == -1 ? 'selected' : ''}>从不</option>
        </select>
      </div>
      <div class="set-row">
        <div class="set-row-main"><span>复制后清除剪贴板</span><em>避免验证码被其它应用读取</em></div>
        <select id="setClipClear" class="set-select">
          <option value="0" ${cfg.clipClear == 0 ? 'selected' : ''}>不清除</option>
          <option value="20" ${cfg.clipClear == 20 ? 'selected' : ''}>20 秒</option>
          <option value="60" ${cfg.clipClear == 60 ? 'selected' : ''}>60 秒</option>
        </select>
      </div>
      <div class="set-row">
        <div class="set-row-main"><span>修改访问密码</span><em>需要重新设置，数据将重新加密</em></div>
        <button class="mini-btn" id="btnChangePin">修改</button>
      </div>
      <div class="set-row">
        <div class="set-row-main"><span>立即锁定</span></div>
        <button class="mini-btn" id="btnLockNow">锁定</button>
      </div>
    </div>

    <div class="set-group">
      <div class="set-group-label">数据</div>
      <div class="set-row">
        <div class="set-row-main"><span>导出加密备份</span><em>JSON + 独立密码，可离线保存</em></div>
        <button class="mini-btn" id="btnExport">导出</button>
      </div>
      <div class="set-row">
        <div class="set-row-main"><span>从备份导入</span><em>支持本应用备份与 otpauth 链接</em></div>
        <button class="mini-btn" id="btnImport">导入</button>
      </div>
      <input type="file" id="importFile" accept=".json,.txt,application/json,text/plain" hidden>
    </div>

    <div class="set-group">
      <div class="set-group-label">关于</div>
      <div class="set-row">
        <div class="set-row-main">
          <span>令牌库 TokenVault</span>
          <em>v1.0 · 纯本地 · 无网络请求 · ${HAS_SUBTLE ? 'WebCrypto 已启用' : '降级模式'}</em>
        </div>
      </div>
    </div>
    <div class="set-bottom-space"></div>`;
}

function bindSettingsPage(host) {
  $$('#themeSeg button', host).forEach(b => {
    b.onclick = () => {
      cfg.theme = b.dataset.theme;
      saveCfg(); applyTheme();
      $$('#themeSeg button', host).forEach(x => x.classList.toggle('is-on', x.dataset.theme === cfg.theme));
      haptic(6);
    };
  });
  $$('#themeSeg button', host).forEach(b => b.classList.toggle('is-on', b.dataset.theme === cfg.theme));

  host.querySelector('#setRing').onchange = ev => { cfg.showRing = ev.target.checked; saveCfg(); renderList(); };
  host.querySelector('#setHideCode').onchange = ev => {
    cfg.hideCode = ev.target.checked; saveCfg(); state.revealed.clear(); renderList();
  };
  host.querySelector('#setAutoLock').onchange = ev => { cfg.autoLock = +ev.target.value; saveCfg(); };

  // 指纹 / 面容解锁
  const bioEl = host.querySelector('#setBio');
  if (bioEl) {
    bioEl.onchange = async ev => {
      if (ev.target.checked) {
        ev.target.checked = false;         // 先复位，真的开启成功再勾上
        await enableBiometric();
        ev.target.checked = !!cfg.bio;
      } else {
        disableBiometric();
      }
    };
    bioAvailable().then(ok => {
      if (!ok) {
        bioEl.disabled = true;
        const hint = host.querySelector('#bioHint');
        if (hint) hint.textContent = '这台设备或浏览器不支持（需 HTTPS + 系统指纹）';
      }
    });
  }
  host.querySelector('#setClipClear').onchange = ev => { cfg.clipClear = +ev.target.value; saveCfg(); };
  host.querySelector('#btnLockNow').onclick = () => lockApp();
  host.querySelector('#btnChangePin').onclick = changePin;
  host.querySelector('#btnExport').onclick = exportBackup;
  host.querySelector('#btnImport').onclick = () => host.querySelector('#importFile').click();
  host.querySelector('#importFile').onchange = importBackupFile;
}

/* ───────────────── 备份 ───────────────── */
async function exportBackup() {
  const pwd = await showModal({
    title: '导出加密备份',
    body: '设置一个备份密码。恢复时需要输入同一密码。',
    input: true, placeholder: '备份密码（至少 6 位）', okText: '导出'
  });
  if (pwd == null) return;
  if (String(pwd).length < 6) return toast('密码太短', 'err');

  let payload;
  if (HAS_SUBTLE) {
    const { key, saltB64 } = await cryptoBox.deriveKey(String(pwd));
    const iv = crypto.getRandomValues(new Uint8Array(12));
    const ct = await crypto.subtle.encrypt({ name: 'AES-GCM', iv }, key,
      new TextEncoder().encode(JSON.stringify(state.entries)));
    payload = { v: 1, alg: 'AES-256-GCM', salt: saltB64, iv: bytesToB64(iv), ct: bytesToB64(new Uint8Array(ct)) };
  } else {
    payload = { v: 1, alg: 'base64-plain', data: bytesToB64(new TextEncoder().encode(JSON.stringify(state.entries))) };
    toast('降级模式：备份未加密', 'info');
  }

  const doc = {
    app: 'TokenVault', version: 1, exportedAt: new Date().toISOString(),
    count: state.entries.length, payload
  };
  const blob = new Blob([JSON.stringify(doc, null, 2)], { type: 'application/json' });
  const a = document.createElement('a');
  a.href = URL.createObjectURL(blob);
  a.download = `tokenvault-backup-${new Date().toISOString().slice(0, 10)}.json`;
  a.click();
  setTimeout(() => URL.revokeObjectURL(a.href), 4000);
  toast('备份已导出', 'ok');
}

async function importBackupFile(ev) {
  const file = ev.target.files && ev.target.files[0];
  ev.target.value = '';
  if (!file) return;
  const text = await file.text();

  // 1) 纯 otpauth 链接 / 文本
  const otpMatches = text.match(/otpauth:\/\/[^\s"',]+/gi);
  if (otpMatches && otpMatches.length) {
    let added = 0;
    for (const uri of otpMatches) {
      const p = parseOtpAuth(uri);
      if (!p) continue;
      state.entries.push(normalizeEntry({
        id: uid(), type: 'code', platform: p.platform, name: p.issuer,
        account: p.account, secret: p.secret, digits: p.digits,
        period: p.period, algo: p.algo, charset: p.platform === 'steam' ? 'steam' : 'numeric',
        createdAt: Date.now()
      }));
      added++;
    }
    if (added) {
      await persist(); renderList();
      return toast(`已导入 ${added} 个 otpauth 令牌`, 'ok');
    }
  }

  // 2) 本应用备份
  let doc;
  try { doc = JSON.parse(text); } catch (_) { return toast('文件格式无法识别', 'err'); }
  if (!doc || doc.app !== 'TokenVault' || !doc.payload) return toast('不是有效的令牌库备份', 'err');

  const p = doc.payload;
  if (p.alg === 'base64-plain') {
    try {
      const arr = JSON.parse(new TextDecoder().decode(b64ToBytes(p.data)));
      await mergeEntries(arr);
    } catch (_) { toast('备份数据损坏', 'err'); }
    return;
  }

  const pwd = await showModal({
    title: '输入备份密码',
    body: `该备份包含 ${doc.count || '?'} 条令牌，需输入导出时设置的密码。`,
    input: true, placeholder: '备份密码', okText: '解密导入'
  });
  if (pwd == null) return;
  try {
    const { key } = await cryptoBox.deriveKey(String(pwd), p.salt);
    const pt = await crypto.subtle.decrypt(
      { name: 'AES-GCM', iv: b64ToBytes(p.iv) }, key, b64ToBytes(p.ct));
    const arr = JSON.parse(new TextDecoder().decode(pt));
    await mergeEntries(arr);
  } catch (_) {
    toast('密码错误或文件损坏', 'err');
  }
}

/* ───────────────── 获取令牌：扫码 / 图片 / 粘贴 ───────────────── */
let _scanStream = null, _scanTimer = null;

/** 底部选择面板，返回选中项的 key，取消返回 null */
function showChoice({ title, body = '', options }) {
  return new Promise(resolve => {
    const scrim = el('div', 'grab-scrim');
    const sheet = el('div', 'grab-sheet');

    const head = el('div', 'grab-head');
    const h = el('div', 'grab-title');
    h.textContent = title;
    head.appendChild(h);
    if (body) {
      const b = el('div', 'grab-body');
      b.textContent = body;
      head.appendChild(b);
    }
    sheet.appendChild(head);

    const list = el('div', 'grab-list');
    options.forEach(o => {
      const row = el('button', 'grab-row');
      const left = el('div', 'grab-row-txt');
      const t = el('b'); t.textContent = o.title; left.appendChild(t);
      if (o.desc) { const d = el('em'); d.textContent = o.desc; left.appendChild(d); }
      row.appendChild(left);
      const arrow = el('span', 'grab-arrow', '›');
      row.appendChild(arrow);
      row.onclick = () => done(o.key);
      list.appendChild(row);
    });
    sheet.appendChild(list);

    const cancel = el('button', 'grab-cancel', '取消');
    cancel.onclick = () => done(null);
    sheet.appendChild(cancel);

    scrim.appendChild(sheet);
    scrim.addEventListener('click', e => { if (e.target === scrim) done(null); });
    document.body.appendChild(scrim);
    requestAnimationFrame(() => scrim.classList.add('is-on'));

    function done(v) {
      if (!scrim.isConnected) return;
      scrim.classList.remove('is-on');
      setTimeout(() => scrim.remove(), 240);
      resolve(v);
    }
  });
}

function scanSupported() {
  return typeof window.BarcodeDetector === 'function';
}

async function openScanner() {
  if (!scanSupported() || !navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
    toast('这个浏览器不能实时扫码，改从相册选图');
    pickImageForScan();
    return;
  }
  const overlay = $('#scanOverlay'), video = $('#scanVideo'), hint = $('#scanHint');
  try {
    _scanStream = await navigator.mediaDevices.getUserMedia({
      video: { facingMode: { ideal: 'environment' } }, audio: false
    });
  } catch (e) {
    const denied = e && (e.name === 'NotAllowedError' || e.name === 'SecurityError');
    toast(denied ? '摄像头权限被拒绝，改从相册选图' : '摄像头打不开，改从相册选图');
    pickImageForScan();
    return;
  }
  video.srcObject = _scanStream;
  overlay.hidden = false;
  if (hint) hint.textContent = '把二维码放进取景框';
  try { await video.play(); } catch (_) {}

  let detector = null;
  try { detector = new window.BarcodeDetector({ formats: ['qr_code'] }); }
  catch (_) { detector = new window.BarcodeDetector(); }

  const tick = async () => {
    if (overlay.hidden) return;
    try {
      const codes = await detector.detect(video);
      if (codes && codes.length) {
        const raw = codes[0].rawValue || '';
        if (/^otpauth/i.test(raw)) {
          stopScanner();
          await addParsedTokens(parseAnyOtp(raw));
          return;
        }
        if (hint) hint.textContent = '这不是两步验证二维码，换个码试试';
      }
    } catch (_) {}
    _scanTimer = setTimeout(tick, 260);
  };
  _scanTimer = setTimeout(tick, 320);
}

function stopScanner() {
  if (_scanTimer) { clearTimeout(_scanTimer); _scanTimer = null; }
  if (_scanStream) {
    try { _scanStream.getTracks().forEach(t => t.stop()); } catch (_) {}
    _scanStream = null;
  }
  const v = $('#scanVideo');
  if (v) { try { v.pause(); } catch (_) {} v.srcObject = null; }
  const o = $('#scanOverlay');
  if (o) o.hidden = true;
}

function pickImageForScan() {
  const inp = $('#scanFile');
  if (!inp) return;
  inp.value = '';
  inp.onchange = async ev => {
    const f = ev.target.files && ev.target.files[0];
    inp.value = '';
    if (!f) return;
    if (!scanSupported()) { toast('此浏览器不支持图片识别，改用粘贴', 'err'); return; }
    try {
      const bmp = await createImageBitmap(f);
      const det = new window.BarcodeDetector({ formats: ['qr_code'] });
      const codes = await det.detect(bmp);
      if (!codes || !codes.length) { toast('这张图片里没识别到二维码', 'err'); return; }
      const raw = codes[0].rawValue || '';
      if (!/^otpauth/i.test(raw)) { toast('二维码内容不是 otpauth 令牌', 'err'); return; }
      stopScanner();
      await addParsedTokens(parseAnyOtp(raw));
    } catch (e) {
      toast('图片识别失败', 'err');
    }
  };
  inp.click();
}

async function grabFromClipboard() {
  let txt = '';
  try {
    if (navigator.clipboard && navigator.clipboard.readText) {
      txt = await navigator.clipboard.readText();
    }
  } catch (_) {}
  if (!txt || !txt.trim()) {
    txt = await showModal({
      title: '粘贴令牌内容',
      body: '支持 otpauth 链接、Google 迁移串（otpauth-migration://）或纯 Base32 密钥。',
      input: true, placeholder: 'otpauth://totp/...', okText: '导入'
    });
    if (txt == null) return;
  }
  const parsed = parseAnyOtp(txt);
  if (!parsed.length) { toast('没识别到有效密钥', 'err'); return; }
  await addParsedTokens(parsed);
}

async function openGrab() {
  const choice = await showChoice({
    title: '获取令牌',
    body: '把已经开着的两步验证搬进来',
    options: [
      { key: 'scan',  title: '扫二维码',        desc: '用它扫网站上的 2FA 二维码' },
      { key: 'image', title: '从图片识别',      desc: '相册里的二维码截图' },
      { key: 'paste', title: '粘贴链接或密钥',  desc: 'otpauth 链接 / 迁移串 / Base32' }
    ]
  });
  if (choice === 'scan') await openScanner();
  else if (choice === 'image') pickImageForScan();
  else if (choice === 'paste') await grabFromClipboard();
}

async function mergeEntries(arr) {
  if (!Array.isArray(arr)) return toast('备份内容无效', 'err');
  let n = 0;
  for (const raw of arr) {
    const e = normalizeEntry(raw);
    if (state.entries.some(x => x.secret && x.secret === e.secret && e.type === 'code')) continue;
    state.entries.push(e); n++;
  }
  await persist(); renderList();
  toast(n ? `已导入 ${n} 条令牌` : '没有新令牌可导入', n ? 'ok' : 'info');
}

async function changePin() {
  const cur = await showModal({
    title: '修改访问密码', body: '先输入当前密码', input: true,
    placeholder: '当前密码', okText: '下一步'
  });
  if (cur == null) return;
  if (HAS_SUBTLE) {
    try {
      const { key } = await cryptoBox.deriveKey(String(cur), cfg.salt);
      const oldKey = cryptoBox._key;
      cryptoBox._key = key;
      const raw = localStorage.getItem(DB_KEY);
      if (raw) await cryptoBox.decrypt(JSON.parse(raw));
      cryptoBox._key = oldKey;
    } catch (_) { return toast('当前密码不正确', 'err'); }
  } else if (_fallbackKey(String(cur)) !== _fallbackKey(firstPin || '')) {
    // 降级模式下用一次试解验证
    const probe = loadFallback(String(cur));
    if (!probe) return toast('当前密码不正确', 'err');
  }

  const np = await showModal({
    title: '设置新密码', body: '6 位数字，修改后旧密码立即失效', input: true,
    placeholder: '新密码（6 位数字）', okText: '保存'
  });
  if (np == null) return;
  if (!/^\d{6}$/.test(String(np))) return toast('请输入 6 位数字', 'err');

  if (HAS_SUBTLE) {
    const { key, saltB64 } = await cryptoBox.deriveKey(String(np));
    cryptoBox._key = key;
    cfg.salt = saltB64; saveCfg();
  } else {
    firstPin = String(np);
  }
  await persist();
  toast('密码已修改', 'ok');
}

/* ═══════════════════════════════════════════════════
   事件绑定与启动
   ═══════════════════════════════════════════════════ */

/* ── 锁屏键盘 ── */
function bindLockScreen() {
  $('#keypad').addEventListener('click', ev => {
    const btn = ev.target.closest('button');
    if (!btn) return;
    const k = btn.dataset.k;
    if (k === 'del') {
      pinBuffer = pinBuffer.slice(0, -1);
      renderPinDots();
      $('#pinError').textContent = '';
      haptic(6);
      return;
    }
    if (k === 'bio') { bioUnlock(); return; }
    if (/^\d$/.test(k)) {
      if (pinBuffer.length >= 6) return;
      pinBuffer += k;
      renderPinDots();
      haptic(6);
      if (pinBuffer.length === 6) setTimeout(onPinComplete, 110);
    }
  });

  // 物理键盘支持
  document.addEventListener('keydown', ev => {
    if ($('#lockScreen').hidden) return;
    if (/^\d$/.test(ev.key)) {
      if (pinBuffer.length >= 6) return;
      pinBuffer += ev.key;
      renderPinDots(); haptic(6);
      if (pinBuffer.length === 6) setTimeout(onPinComplete, 110);
    } else if (ev.key === 'Backspace') {
      pinBuffer = pinBuffer.slice(0, -1);
      renderPinDots();
    } else if (ev.key === 'Enter' && pinBuffer.length === 6) {
      onPinComplete();
    }
  });
}

/* ── 列表交互 ── */
function bindList() {
  const list = $('#list');
  let pressTimer = null, longPressed = false, pressCard = null;

  const startPress = (card, x, y) => {
    longPressed = false;
    pressCard = card;
    clearTimeout(pressTimer);
    pressTimer = setTimeout(() => {
      longPressed = true;
      haptic(16);
      openDetail(card.dataset.id);
    }, 460);
  };
  const endPress = () => { clearTimeout(pressTimer); pressTimer = null; };

  list.addEventListener('pointerdown', ev => {
    const card = ev.target.closest('.card');
    if (!card) return;
    startPress(card, ev.clientX, ev.clientY);
  });
  list.addEventListener('pointerup', ev => {
    const card = ev.target.closest('.card');
    const wasLong = longPressed;
    endPress();
    if (!card || wasLong) { longPressed = false; return; }
    handleCardTap(card, ev);
  });
  list.addEventListener('pointercancel', endPress);
  list.addEventListener('pointerleave', endPress);
  list.addEventListener('contextmenu', ev => {
    const card = ev.target.closest('.card');
    if (card) { ev.preventDefault(); openDetail(card.dataset.id); }
  });
}

function handleCardTap(card, ev) {
  const id = card.dataset.id;
  const e = state.entries.find(x => x.id === id);
  if (!e) return;

  // 点左侧图标 → 详情
  if (ev.target.closest('.pf-icon')) { openDetail(id); return; }

  if (e.type === 'note') { openDetail(id); return; }

  // 隐藏模式下第一次点击 = 显示
  if (cfg.hideCode && !state.revealed.has(id)) {
    state.revealed.add(id);
    const c = state.codeCache.get(id);
    if (c) updateCardCode(id, c.code, c.remaining, c.period);
    toast('已显示，再次点击复制', 'info', 1400);
    return;
  }

  const c = state.codeCache.get(id);
  if (c && c.code && c.code !== 'ERROR') {
    copyText(c.code.replace(/\s/g, ''), `${e.name} 验证码已复制`);
  } else {
    openDetail(id);
  }
}

/* ── 全局事件 ── */
function bindGlobal() {
  // 搜索
  $('#searchBtn').onclick = () => {
    const bar = $('#searchbar');
    if (bar.hidden) { bar.hidden = false; setTimeout(() => $('#searchInput').focus(), 60); }
    else { bar.hidden = true; state.query = ''; $('#searchInput').value = ''; renderList(); }
  };
  $('#searchInput').oninput = ev => { state.query = ev.target.value; renderList(); };
  $('#searchClear').onclick = () => {
    state.query = ''; $('#searchInput').value = '';
    $('#searchbar').hidden = true;
    renderList();
  };

  // 过滤器
  $('#filterChips').onclick = ev => {
    const chip = ev.target.closest('.chip');
    if (!chip) return;
    state.filter = chip.dataset.filter;
    $$('#filterChips .chip').forEach(c => c.classList.toggle('is-on', c === chip));
    haptic(6);
    renderList();
  };

  // FAB
  $('#fab').onclick = () => { haptic(10); openEditor(); };

  // 获取令牌
  const grabBtn = $('#grabBtn');
  if (grabBtn) grabBtn.onclick = () => { haptic(10); openGrab(); };
  const scanClose = $('#scanClose');
  if (scanClose) scanClose.onclick = () => stopScanner();
  const scanAlbum = $('#scanAlbum');
  if (scanAlbum) scanAlbum.onclick = () => pickImageForScan();
  document.addEventListener('visibilitychange', () => {
    if (document.hidden && _scanStream) stopScanner();
  });

  // 标签栏
  $('#tabbar').onclick = ev => {
    const tab = ev.target.closest('.tab');
    if (!tab) return;
    haptic(6);
    switchTab(tab.dataset.tab);
  };

  // 滚动阴影
  window.addEventListener('scroll', () => {
    $('#topbar').classList.toggle('is-scrolled', window.scrollY > 6);
  }, { passive: true });

  // 详情面板
  $('#detailClose').onclick = () => closeSheet('detailScrim', 'detailSheet');
  $('#detailScrim').onclick = () => closeSheet('detailScrim', 'detailSheet');
  $('#detailCodeWrap').onclick = () => {
    const e = state.entries.find(x => x.id === state.detailId);
    const c = state.codeCache.get(state.detailId);
    if (e && c && c.code) copyText(c.code, '验证码已复制');
  };
  $('#detailCopySecret').onclick = () => {
    const e = state.entries.find(x => x.id === state.detailId);
    if (e && e.secret) copyText(e.secret, '密钥已复制');
  };
  $('#detailEdit').onclick = () => {
    const id = state.detailId;
    closeSheet('detailScrim', 'detailSheet');
    setTimeout(() => openEditor(id), 180);
  };
  $('#detailDelete').onclick = async () => {
    const id = state.detailId;
    const e = state.entries.find(x => x.id === id);
    if (!e) return;
    const ok = await showModal({
      title: '删除令牌？',
      body: `「${e.name}」将从本机永久移除，无法撤销。`,
      okText: '删除', danger: true
    });
    if (!ok) return;
    state.entries = state.entries.filter(x => x.id !== id);
    state.codeCache.delete(id);
    await persist();
    closeSheet('detailScrim', 'detailSheet');
    renderList();
    toast('已删除', 'ok');
  };

  // 编辑器
  $('#editorCancel').onclick = () => { state.editingId = null; closeSheet('editorScrim', 'editorSheet'); };
  $('#editorScrim').onclick = () => { state.editingId = null; closeSheet('editorScrim', 'editorSheet'); };
  $('#editorSave').onclick = saveEditor;

  $('#editorSeg').onclick = ev => {
    const b = ev.target.closest('button');
    if (!b) return;
    haptic(6);
    setEditorKind(b.dataset.kind);
  };

  $('#advToggle').onclick = () => {
    const body = $('#advBody');
    body.hidden = !body.hidden;
    $('#advToggle').classList.toggle('is-open', !body.hidden);
  };

  $('#pasteSecret').onclick = async () => {
    try {
      const txt = await navigator.clipboard.readText();
      if (!txt) return toast('剪贴板是空的', 'info');
      // 若粘贴的是 otpauth 链接则整体解析
      const p = parseOtpAuth(txt);
      if (p) {
        $('#fSecret').value = p.secret;
        $('#fName').value = p.issuer;
        $('#fAccount').value = p.account;
        $('#fDigits').value = String(p.digits);
        $('#fPeriod').value = String(p.period);
        $('#fAlgo').value = p.algo;
        selectPlatform(p.platform);
        toast('已从链接解析', 'ok');
      } else {
        $('#fSecret').value = txt.replace(/[\s\-]/g, '').toUpperCase();
        toast('已粘贴', 'ok');
      }
    } catch (_) {
      toast('无法读取剪贴板，请手动粘贴', 'err');
    }
  };

  // 顶部齿轮 → 设置页
  $('#settingsBtn').onclick = () => switchTab('settings');

  // Modal
  $('#modalOk').onclick = () => {
    const input = $('#modalInputWrap');
    closeModal(input.hidden ? true : $('#modalInput').value);
  };
  $('#modalCancel').onclick = () => closeModal(null);
  $('#modalScrim').onclick = ev => { if (ev.target === $('#modalScrim')) closeModal(null); };
  $('#modalInput').addEventListener('keydown', ev => {
    if (ev.key === 'Enter') { ev.preventDefault(); $('#modalOk').click(); }
  });

  // ESC 关闭
  document.addEventListener('keydown', ev => {
    if (ev.key !== 'Escape') return;
    if (!$('#modalScrim').hidden) return closeModal(null);
    if (!$('#detailSheet').hidden) return closeSheet('detailScrim', 'detailSheet');
    if (!$('#editorSheet').hidden) { state.editingId = null; return closeSheet('editorScrim', 'editorSheet'); }
    if (!$('#searchbar').hidden) return $('#searchBtn').click();
  });

  // 自动锁定
  document.addEventListener('visibilitychange', () => {
    if (document.hidden) {
      state.hiddenAt = Date.now();
      if (cfg.autoLock === 0) { /* 立即锁：仅在回到前台时判断，避免 iOS 误触 */ }
    } else if (state.unlocked) {
      const away = Date.now() - (state.hiddenAt || 0);
      const limit = +cfg.autoLock;
      if (limit >= 0 && state.hiddenAt && away >= limit * 1000) lockApp();
      else { tick(); }
    }
  });

  // 系统主题变化
  matchMedia('(prefers-color-scheme: light)').addEventListener('change', () => {
    if (cfg.theme === 'auto') applyTheme();
  });

  // 防止双指缩放（iOS）
  document.addEventListener('gesturestart', ev => ev.preventDefault());
}

/* ── 启动 ── */
async function boot() {
  loadCfg();
  applyTheme();

  // 主题分段（设置面板里的）
  $$('#themeSeg button').forEach(b => {
    b.onclick = () => {
      cfg.theme = b.dataset.theme; saveCfg(); applyTheme();
      $$('#themeSeg button').forEach(x => x.classList.toggle('is-on', x.dataset.theme === cfg.theme));
    };
    b.classList.toggle('is-on', b.dataset.theme === cfg.theme);
  });

  buildPlatformGrid();
  bindLockScreen();
  bindList();
  bindGlobal();

  const hasData = !!localStorage.getItem(DB_KEY);

  if (!hasData || !cfg.hasVault) {
    pinMode = 'setup';
    $('#lockTitle').textContent = '设置访问密码';
    $('#lockSub').textContent = '6 位数字 · 用于保护你的令牌';
  } else {
    pinMode = 'unlock';
    $('#lockTitle').textContent = '令牌库';
    $('#lockSub').textContent = '输入访问密码';
  }
  renderPinDots();
  reloadFallbackHint();
  refreshBioButton();

  // 预热：无数据的空库也需要 key 已存在
  if (hasData && cfg.salt && HAS_SUBTLE) { /* 等用户输入 PIN 后派生 */ }
}

document.addEventListener('DOMContentLoaded', boot);
window.addEventListener('error', ev => {
  console.error('[TokenVault]', ev.error || ev.message);
});