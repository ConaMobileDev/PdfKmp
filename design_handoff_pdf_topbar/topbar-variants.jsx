// PDF Viewer Topbar variants — mobile, minimalist
// All variants share: back, title, download, share, page indicator (bottom)

const PHONE_W = 390;
const PHONE_H = 844;

// ─── Icons (Lucide-style, 24px stroke 2) ─────────────────────────
const Ico = {
  chevronLeft: (s = 24, c = 'currentColor', sw = 2) => (
    <svg width={s} height={s} viewBox="0 0 24 24" fill="none" stroke={c} strokeWidth={sw} strokeLinecap="round" strokeLinejoin="round"><polyline points="15 18 9 12 15 6"/></svg>
  ),
  arrowLeft: (s = 24, c = 'currentColor', sw = 2) => (
    <svg width={s} height={s} viewBox="0 0 24 24" fill="none" stroke={c} strokeWidth={sw} strokeLinecap="round" strokeLinejoin="round"><line x1="19" y1="12" x2="5" y2="12"/><polyline points="12 19 5 12 12 5"/></svg>
  ),
  download: (s = 24, c = 'currentColor', sw = 2) => (
    <svg width={s} height={s} viewBox="0 0 24 24" fill="none" stroke={c} strokeWidth={sw} strokeLinecap="round" strokeLinejoin="round"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/></svg>
  ),
  downloadFilled: (s = 24, c = 'currentColor') => (
    <svg width={s} height={s} viewBox="0 0 24 24" fill={c}><path d="M12 3a1 1 0 0 1 1 1v9.586l3.293-3.293a1 1 0 1 1 1.414 1.414l-5 5a1 1 0 0 1-1.414 0l-5-5a1 1 0 1 1 1.414-1.414L11 13.586V4a1 1 0 0 1 1-1zM4 18a1 1 0 0 1 1 1v1h14v-1a1 1 0 1 1 2 0v2a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1v-2a1 1 0 0 1 1-1z"/></svg>
  ),
  share: (s = 24, c = 'currentColor', sw = 2) => (
    // iOS share — square with up arrow
    <svg width={s} height={s} viewBox="0 0 24 24" fill="none" stroke={c} strokeWidth={sw} strokeLinecap="round" strokeLinejoin="round"><path d="M16 6l-4-4-4 4"/><line x1="12" y1="2" x2="12" y2="15"/><path d="M20 12v7a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2v-7"/></svg>
  ),
  shareNodes: (s = 24, c = 'currentColor', sw = 2) => (
    <svg width={s} height={s} viewBox="0 0 24 24" fill="none" stroke={c} strokeWidth={sw} strokeLinecap="round" strokeLinejoin="round"><circle cx="18" cy="5" r="3"/><circle cx="6" cy="12" r="3"/><circle cx="18" cy="19" r="3"/><line x1="8.59" y1="13.51" x2="15.42" y2="17.49"/><line x1="15.41" y1="6.51" x2="8.59" y2="10.49"/></svg>
  ),
  search: (s = 24, c = 'currentColor', sw = 2) => (
    <svg width={s} height={s} viewBox="0 0 24 24" fill="none" stroke={c} strokeWidth={sw} strokeLinecap="round" strokeLinejoin="round"><circle cx="11" cy="11" r="7"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>
  ),
  more: (s = 24, c = 'currentColor') => (
    <svg width={s} height={s} viewBox="0 0 24 24" fill={c}><circle cx="5" cy="12" r="2"/><circle cx="12" cy="12" r="2"/><circle cx="19" cy="12" r="2"/></svg>
  ),
  fileText: (s = 16, c = 'currentColor', sw = 1.6) => (
    <svg width={s} height={s} viewBox="0 0 24 24" fill="none" stroke={c} strokeWidth={sw} strokeLinecap="round" strokeLinejoin="round"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/><line x1="16" y1="13" x2="8" y2="13"/><line x1="16" y1="17" x2="8" y2="17"/><line x1="10" y1="9" x2="8" y2="9"/></svg>
  ),
  close: (s = 22, c = 'currentColor', sw = 2) => (
    <svg width={s} height={s} viewBox="0 0 24 24" fill="none" stroke={c} strokeWidth={sw} strokeLinecap="round" strokeLinejoin="round"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
  ),
};

// ─── Status bar (reused across all variants) ────────────────────
function StatusBar({ dark = false, time = '9:41' }) {
  const c = dark ? '#fff' : '#000';
  return (
    <div style={{
      height: 54, display: 'flex', alignItems: 'center', justifyContent: 'space-between',
      padding: '21px 32px 0', boxSizing: 'border-box', flexShrink: 0,
    }}>
      <span style={{ fontFamily: '-apple-system, "SF Pro", system-ui', fontWeight: 600, fontSize: 17, color: c, letterSpacing: -0.2 }}>{time}</span>
      <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
        <svg width="18" height="11" viewBox="0 0 19 12"><rect x="0" y="7.5" width="3.2" height="4.5" rx="0.7" fill={c}/><rect x="4.8" y="5" width="3.2" height="7" rx="0.7" fill={c}/><rect x="9.6" y="2.5" width="3.2" height="9.5" rx="0.7" fill={c}/><rect x="14.4" y="0" width="3.2" height="12" rx="0.7" fill={c}/></svg>
        <svg width="16" height="11" viewBox="0 0 17 12"><path d="M8.5 3.2C10.8 3.2 12.9 4.1 14.4 5.6L15.5 4.5C13.7 2.7 11.2 1.5 8.5 1.5C5.8 1.5 3.3 2.7 1.5 4.5L2.6 5.6C4.1 4.1 6.2 3.2 8.5 3.2Z" fill={c}/><path d="M8.5 6.8C9.9 6.8 11.1 7.3 12 8.2L13.1 7.1C11.8 5.9 10.2 5.1 8.5 5.1C6.8 5.1 5.2 5.9 3.9 7.1L5 8.2C5.9 7.3 7.1 6.8 8.5 6.8Z" fill={c}/><circle cx="8.5" cy="10.5" r="1.5" fill={c}/></svg>
        <svg width="25" height="12" viewBox="0 0 27 13"><rect x="0.5" y="0.5" width="23" height="12" rx="3.5" stroke={c} strokeOpacity="0.35" fill="none"/><rect x="2" y="2" width="20" height="9" rx="2" fill={c}/><path d="M25 4.5V8.5C25.8 8.2 26.5 7.2 26.5 6.5C26.5 5.8 25.8 4.8 25 4.5Z" fill={c} fillOpacity="0.4"/></svg>
      </div>
    </div>
  );
}

// ─── Shared PDF page placeholder ────────────────────────────────
function PdfPage({ dark = false, scrollable = true }) {
  const bg = dark ? '#1a1a1a' : '#e9e9ec';
  const paper = dark ? '#2a2a2c' : '#fff';
  const ink = dark ? '#48484a' : '#d4d4d8';
  return (
    <div style={{ flex: 1, background: bg, position: 'relative', overflow: 'hidden' }}>
      <div style={{
        margin: '20px 16px', borderRadius: 4,
        background: paper,
        boxShadow: dark ? '0 1px 0 rgba(255,255,255,0.04)' : '0 1px 2px rgba(0,0,0,0.06), 0 4px 12px rgba(0,0,0,0.04)',
        padding: '28px 26px',
        fontFamily: '"Times New Roman", Georgia, serif',
        color: ink,
      }}>
        <div style={{ height: 14, width: '60%', background: ink, borderRadius: 2, marginBottom: 16, opacity: dark ? 0.6 : 1 }}/>
        <div style={{ height: 8, width: '95%', background: ink, opacity: 0.5, borderRadius: 2, marginBottom: 8 }}/>
        <div style={{ height: 8, width: '92%', background: ink, opacity: 0.5, borderRadius: 2, marginBottom: 8 }}/>
        <div style={{ height: 8, width: '88%', background: ink, opacity: 0.5, borderRadius: 2, marginBottom: 8 }}/>
        <div style={{ height: 8, width: '94%', background: ink, opacity: 0.5, borderRadius: 2, marginBottom: 8 }}/>
        <div style={{ height: 8, width: '40%', background: ink, opacity: 0.5, borderRadius: 2, marginBottom: 22 }}/>

        <div style={{ height: 10, width: '45%', background: ink, opacity: 0.7, borderRadius: 2, marginBottom: 12 }}/>
        <div style={{ height: 8, width: '96%', background: ink, opacity: 0.5, borderRadius: 2, marginBottom: 8 }}/>
        <div style={{ height: 8, width: '90%', background: ink, opacity: 0.5, borderRadius: 2, marginBottom: 8 }}/>
        <div style={{ height: 8, width: '93%', background: ink, opacity: 0.5, borderRadius: 2, marginBottom: 8 }}/>
        <div style={{ height: 8, width: '60%', background: ink, opacity: 0.5, borderRadius: 2, marginBottom: 22 }}/>

        <div style={{ height: 120, width: '100%', background: ink, opacity: 0.35, borderRadius: 4, marginBottom: 16 }}/>

        <div style={{ height: 8, width: '94%', background: ink, opacity: 0.5, borderRadius: 2, marginBottom: 8 }}/>
        <div style={{ height: 8, width: '88%', background: ink, opacity: 0.5, borderRadius: 2, marginBottom: 8 }}/>
        <div style={{ height: 8, width: '70%', background: ink, opacity: 0.5, borderRadius: 2 }}/>
      </div>
    </div>
  );
}

// ─── Page indicator pill (bottom) — shared ──────────────────────
function PageIndicator({ current = 7, total = 24, dark = false, style = {} }) {
  return (
    <div style={{
      position: 'absolute', bottom: 28, left: '50%', transform: 'translateX(-50%)',
      padding: '8px 14px', borderRadius: 999,
      background: dark ? 'rgba(28,28,30,0.85)' : 'rgba(20,20,22,0.78)',
      backdropFilter: 'blur(20px)', WebkitBackdropFilter: 'blur(20px)',
      color: '#fff',
      fontFamily: '-apple-system, "SF Pro", system-ui',
      fontSize: 13, fontWeight: 600, letterSpacing: -0.1,
      boxShadow: '0 4px 16px rgba(0,0,0,0.18), 0 1px 2px rgba(0,0,0,0.1)',
      display: 'flex', alignItems: 'center', gap: 6,
      ...style,
    }}>
      <span style={{ fontVariantNumeric: 'tabular-nums' }}>{current}</span>
      <span style={{ opacity: 0.5 }}>/</span>
      <span style={{ opacity: 0.6, fontVariantNumeric: 'tabular-nums' }}>{total}</span>
    </div>
  );
}

// ─── Phone wrapper (no scaling — DCArtboard handles size) ───────
function Phone({ children, bg = '#fff' }) {
  return (
    <div style={{
      width: PHONE_W, height: PHONE_H,
      background: bg,
      borderRadius: 0,
      overflow: 'hidden',
      display: 'flex', flexDirection: 'column',
      fontFamily: '-apple-system, "SF Pro Text", system-ui',
      position: 'relative',
    }}>
      {children}
    </div>
  );
}

// ════════════════════════════════════════════════════════════════
// VARIANT 1 — Classic iOS Native
// Standard Apple-style: back chevron + label, centered title,
// trailing icons. Most familiar pattern.
// ════════════════════════════════════════════════════════════════
function V1_ClassicIOS() {
  return (
    <Phone>
      <StatusBar />
      <div style={{
        height: 52, display: 'grid',
        gridTemplateColumns: '1fr auto 1fr',
        alignItems: 'center', padding: '0 8px',
        borderBottom: '0.5px solid rgba(0,0,0,0.08)',
        background: '#fff',
      }}>
        {/* Leading: back */}
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'flex-start' }}>
          <button style={btnReset()}>
            <span style={{ display: 'inline-flex', alignItems: 'center', color: '#0A84FF', gap: 2 }}>
              {Ico.chevronLeft(28, '#0A84FF', 2.4)}
              <span style={{ fontSize: 17, fontWeight: 400, letterSpacing: -0.4 }}>Files</span>
            </span>
          </button>
        </div>
        {/* Center title */}
        <div style={{
          fontSize: 17, fontWeight: 600, color: '#000',
          letterSpacing: -0.4, maxWidth: 180,
          whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis',
        }}>Annual Report</div>
        {/* Trailing actions */}
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'flex-end', gap: 4, paddingRight: 4 }}>
          <button style={btnReset(36)}>{Ico.search(22, '#0A84FF', 2)}</button>
          <button style={btnReset(36)}>{Ico.share(22, '#0A84FF', 2)}</button>
          <button style={btnReset(36)}>{Ico.download(22, '#0A84FF', 2)}</button>
        </div>
      </div>
      <PdfPage />
      <PageIndicator />
    </Phone>
  );
}

// ════════════════════════════════════════════════════════════════
// VARIANT 2 — Minimal Mono (recommended)
// Clean monochrome. Tap-target chips for actions, no center title
// dominance. Title gets full width with subtle file-type prefix.
// ════════════════════════════════════════════════════════════════
function V2_MinimalMono() {
  return (
    <Phone>
      <StatusBar />
      <div style={{
        padding: '6px 8px 10px', display: 'flex', alignItems: 'center', gap: 4,
        background: '#fff',
      }}>
        <button style={chip()}>{Ico.arrowLeft(20, '#111', 2)}</button>
        <div style={{ flex: 1, minWidth: 0, padding: '0 6px' }}>
          <div style={{
            fontSize: 15, fontWeight: 600, color: '#0a0a0a', letterSpacing: -0.2,
            whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis', lineHeight: 1.2,
          }}>Annual Report 2025</div>
          <div style={{ fontSize: 11, color: '#8e8e93', fontWeight: 500, letterSpacing: 0.2, marginTop: 1, textTransform: 'uppercase' }}>PDF · 2.4 MB</div>
        </div>
        <button style={chip()}>{Ico.search(20, '#111', 2)}</button>
        <button style={chip()}>{Ico.share(20, '#111', 2)}</button>
        <button style={chip(true)}>{Ico.download(20, '#fff', 2.2)}</button>
      </div>
      <div style={{ height: 1, background: '#f1f1f3' }}/>
      <PdfPage />
      <PageIndicator />
    </Phone>
  );
}

// ════════════════════════════════════════════════════════════════
// VARIANT 3 — Floating Glass Pills (split layout)
// Topbar overlays the PDF. Two floating pills: leading (back+title)
// and trailing (actions). Modern, immersive feel.
// ════════════════════════════════════════════════════════════════
function V3_FloatingGlass() {
  return (
    <Phone bg="#e9e9ec">
      <StatusBar />
      <div style={{ flex: 1, position: 'relative', overflow: 'hidden' }}>
        <PdfPage />
        {/* Overlaid pills */}
        <div style={{
          position: 'absolute', top: 8, left: 12, right: 12,
          display: 'flex', alignItems: 'center', gap: 8,
        }}>
          {/* Leading pill: back + title */}
          <div style={glassPill({ flex: 1, padding: '0 6px 0 4px', minWidth: 0 })}>
            <button style={btnReset(36)}>{Ico.chevronLeft(22, '#1c1c1e', 2.4)}</button>
            <div style={{ minWidth: 0, paddingRight: 8 }}>
              <div style={{ fontSize: 14, fontWeight: 600, color: '#1c1c1e', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis', letterSpacing: -0.2, lineHeight: 1.15 }}>Annual Report</div>
              <div style={{ fontSize: 10.5, color: '#6e6e73', fontWeight: 500, marginTop: 1 }}>Page 7 of 24</div>
            </div>
          </div>
          {/* Trailing pill: actions */}
          <div style={glassPill({ padding: '0 4px' })}>
            <button style={btnReset(36)}>{Ico.search(20, '#1c1c1e', 2)}</button>
            <button style={btnReset(36)}>{Ico.share(20, '#1c1c1e', 2)}</button>
            <button style={btnReset(36)}>{Ico.download(20, '#1c1c1e', 2)}</button>
          </div>
        </div>
      </div>
    </Phone>
  );
}

// ════════════════════════════════════════════════════════════════
// VARIANT 4 — Two-row hierarchy
// Row 1: Back + meta + more (compact). Row 2: Big title + primary
// CTAs (Download as primary filled button). Best when title is
// long and you want clear hierarchy.
// ════════════════════════════════════════════════════════════════
function V4_TwoRow() {
  return (
    <Phone>
      <StatusBar />
      <div style={{ background: '#fff', padding: '4px 8px 14px' }}>
        {/* Row 1 */}
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', height: 40 }}>
          <button style={chip()}>{Ico.arrowLeft(20, '#111', 2)}</button>
          <div style={{ display: 'flex', alignItems: 'center', gap: 6, color: '#8e8e93', fontSize: 12, fontWeight: 500 }}>
            {Ico.fileText(13, '#8e8e93', 1.8)}
            <span>2.4 MB · 24 pages</span>
          </div>
          <button style={chip()}>{Ico.more(22, '#111')}</button>
        </div>
        {/* Row 2 */}
        <div style={{ padding: '8px 6px 0', display: 'flex', alignItems: 'flex-end', gap: 10 }}>
          <div style={{ flex: 1, minWidth: 0 }}>
            <div style={{
              fontSize: 22, fontWeight: 700, color: '#0a0a0a', letterSpacing: -0.6,
              lineHeight: 1.15, overflow: 'hidden',
              display: '-webkit-box', WebkitLineClamp: 2, WebkitBoxOrient: 'vertical',
            }}>Annual Report 2025</div>
          </div>
          <div style={{ display: 'flex', gap: 6, flexShrink: 0 }}>
            <button style={iconBtnSquare()}>{Ico.search(19, '#111', 2)}</button>
            <button style={iconBtnSquare()}>{Ico.share(19, '#111', 2)}</button>
            <button style={iconBtnSquareFilled()}>{Ico.download(19, '#fff', 2.2)}</button>
          </div>
        </div>
      </div>
      <div style={{ height: 1, background: '#f1f1f3' }}/>
      <PdfPage />
      <PageIndicator />
    </Phone>
  );
}

// ════════════════════════════════════════════════════════════════
// VARIANT 5 — Centered with ambient backdrop
// Title centered, single-line truncate. Back gets generous tap
// area on left. Right cluster groups secondary + primary action.
// Subtle gradient under bar for depth.
// ════════════════════════════════════════════════════════════════
function V5_Ambient() {
  return (
    <Phone>
      <StatusBar />
      <div style={{
        position: 'relative',
        background: '#fff',
        padding: '4px 12px 12px',
      }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          <button style={chip()}>{Ico.arrowLeft(20, '#111', 2)}</button>
          <div style={{
            flex: 1, textAlign: 'center', minWidth: 0,
            fontSize: 16, fontWeight: 600, color: '#0a0a0a',
            letterSpacing: -0.3,
            whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis',
          }}>Annual Report 2025</div>
          {/* grouped action cluster */}
          <div style={{
            display: 'flex', alignItems: 'center',
            background: '#f4f4f6', borderRadius: 999, padding: 2, gap: 0,
          }}>
            <button style={btnReset(36)}>{Ico.search(19, '#3a3a3c', 2)}</button>
            <button style={btnReset(36)}>{Ico.share(19, '#3a3a3c', 2)}</button>
            <button style={{ ...btnReset(36), background: '#111', borderRadius: 999, height: 32, width: 32 }}>{Ico.download(17, '#fff', 2.4)}</button>
          </div>
        </div>
      </div>
      <div style={{ height: 24, background: 'linear-gradient(180deg, rgba(0,0,0,0.04), rgba(0,0,0,0))', marginTop: -8, pointerEvents: 'none' }}/>
      <div style={{ flex: 1, marginTop: -16, position: 'relative' }}>
        <PdfPage />
        <PageIndicator />
      </div>
    </Phone>
  );
}

// ════════════════════════════════════════════════════════════════
// VARIANT 6 — Stacked Title + Inline Search expand
// Default view shows title clearly. Search icon expands inline
// into a search field, replacing the title. Compact yet powerful.
// ════════════════════════════════════════════════════════════════
function V6_ExpandingSearch() {
  const [searchOpen, setSearchOpen] = React.useState(false);
  return (
    <Phone>
      <StatusBar />
      <div style={{
        background: '#fff',
        padding: '6px 10px 12px',
        borderBottom: '0.5px solid #ececef',
      }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
          {!searchOpen && (
            <>
              <button style={chip()} onClick={() => {}}>{Ico.arrowLeft(20, '#111', 2)}</button>
              <div style={{ flex: 1, minWidth: 0, padding: '0 4px' }}>
                <div style={{
                  fontSize: 15.5, fontWeight: 600, color: '#0a0a0a', letterSpacing: -0.2,
                  whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis', lineHeight: 1.2,
                }}>Annual Report 2025</div>
                <div style={{ fontSize: 11.5, color: '#8e8e93', fontWeight: 500, marginTop: 2 }}>Page 7 of 24 · 2.4 MB</div>
              </div>
              <button style={chip()} onClick={() => setSearchOpen(true)}>{Ico.search(19, '#111', 2)}</button>
              <button style={chip()}>{Ico.share(19, '#111', 2)}</button>
              <button style={chip(true)}>{Ico.download(19, '#fff', 2.2)}</button>
            </>
          )}
          {searchOpen && (
            <>
              <div style={{
                flex: 1, display: 'flex', alignItems: 'center', gap: 8,
                background: '#f1f1f3', borderRadius: 10, padding: '0 12px', height: 38,
              }}>
                {Ico.search(18, '#8e8e93', 2)}
                <div style={{ color: '#8e8e93', fontSize: 15 }}>Search in document</div>
                <div style={{ flex: 1 }}/>
                <div style={{ width: 2, height: 18, background: '#0A84FF', animation: 'blink 1s infinite' }}/>
              </div>
              <button style={btnReset(38)} onClick={() => setSearchOpen(false)}>
                <span style={{ color: '#0A84FF', fontSize: 16, fontWeight: 500 }}>Cancel</span>
              </button>
            </>
          )}
        </div>
      </div>
      <PdfPage />
      <PageIndicator />
    </Phone>
  );
}

// ════════════════════════════════════════════════════════════════
// VARIANT 7 — Edge-to-edge dark immersive
// Dark topbar makes the PDF page paper "pop". Great for reading
// mode. Actions remain monochrome and quiet.
// ════════════════════════════════════════════════════════════════
function V7_DarkImmersive() {
  return (
    <Phone bg="#0a0a0a">
      <StatusBar dark />
      <div style={{
        padding: '4px 8px 12px', display: 'flex', alignItems: 'center', gap: 4,
      }}>
        <button style={chipDark()}>{Ico.arrowLeft(20, '#fff', 2)}</button>
        <div style={{ flex: 1, minWidth: 0, padding: '0 6px' }}>
          <div style={{
            fontSize: 15, fontWeight: 600, color: '#fff', letterSpacing: -0.2,
            whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis', lineHeight: 1.2,
          }}>Annual Report 2025</div>
          <div style={{ fontSize: 11, color: '#8e8e93', fontWeight: 500, marginTop: 1 }}>24 pages · 2.4 MB</div>
        </div>
        <button style={chipDark()}>{Ico.search(20, '#fff', 2)}</button>
        <button style={chipDark()}>{Ico.share(20, '#fff', 2)}</button>
        <button style={{ ...chipDark(), background: '#fff' }}>{Ico.download(20, '#0a0a0a', 2.2)}</button>
      </div>
      <PdfPage dark />
      <PageIndicator dark />
    </Phone>
  );
}

// ════════════════════════════════════════════════════════════════
// VARIANT 8 — Notion / Linear style
// Subtle, document-app feel. Filename treated like a doc title with
// breadcrumb hint. Small icon-only actions, lots of breathing room.
// ════════════════════════════════════════════════════════════════
function V8_DocumentStyle() {
  return (
    <Phone>
      <StatusBar />
      <div style={{
        background: '#fff', padding: '0 14px',
      }}>
        {/* Breadcrumb row */}
        <div style={{ display: 'flex', alignItems: 'center', gap: 6, height: 36, color: '#8e8e93', fontSize: 12.5, fontWeight: 500 }}>
          <button style={{ ...btnReset(28), padding: 0, color: '#8e8e93' }}>{Ico.arrowLeft(16, '#8e8e93', 2)}</button>
          <span>Documents</span>
          <span style={{ opacity: 0.5 }}>/</span>
          <span>Reports</span>
        </div>
        {/* Title + actions */}
        <div style={{ display: 'flex', alignItems: 'center', gap: 8, paddingBottom: 14 }}>
          <div style={{
            width: 32, height: 40, borderRadius: 4,
            background: 'linear-gradient(180deg, #fff 0%, #f4f4f6 100%)',
            border: '1px solid #ececef',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            color: '#ef4444', fontSize: 9, fontWeight: 700, letterSpacing: 0.3,
            flexShrink: 0,
            boxShadow: '0 1px 2px rgba(0,0,0,0.04)',
          }}>PDF</div>
          <div style={{ flex: 1, minWidth: 0 }}>
            <div style={{
              fontSize: 17, fontWeight: 700, color: '#0a0a0a', letterSpacing: -0.4,
              whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis', lineHeight: 1.15,
            }}>Annual Report 2025</div>
            <div style={{ fontSize: 11.5, color: '#8e8e93', fontWeight: 500, marginTop: 2 }}>Edited Apr 28 · 24 pages</div>
          </div>
          <div style={{ display: 'flex', gap: 2 }}>
            <button style={btnReset(34)}>{Ico.search(19, '#3a3a3c', 1.8)}</button>
            <button style={btnReset(34)}>{Ico.share(19, '#3a3a3c', 1.8)}</button>
            <button style={btnReset(34)}>{Ico.download(19, '#3a3a3c', 1.8)}</button>
          </div>
        </div>
      </div>
      <div style={{ height: 1, background: '#f1f1f3' }}/>
      <PdfPage />
      <PageIndicator />
    </Phone>
  );
}

// ─── Style helpers ──────────────────────────────────────────────
function btnReset(size = 40) {
  return {
    width: size, height: size, border: 'none', background: 'transparent',
    display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
    cursor: 'pointer', padding: 0, borderRadius: 999,
    fontFamily: 'inherit',
  };
}
function chip(filled = false) {
  return {
    width: 38, height: 38, borderRadius: 999, border: 'none',
    background: filled ? '#111' : '#f4f4f6',
    display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
    cursor: 'pointer', padding: 0, flexShrink: 0,
    transition: 'transform 0.15s',
  };
}
function chipDark() {
  return {
    width: 38, height: 38, borderRadius: 999, border: 'none',
    background: 'rgba(255,255,255,0.08)',
    display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
    cursor: 'pointer', padding: 0, flexShrink: 0,
  };
}
function iconBtnSquare() {
  return {
    width: 40, height: 40, borderRadius: 12, border: '1px solid #ececef',
    background: '#fff',
    display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
    cursor: 'pointer', padding: 0, flexShrink: 0,
  };
}
function iconBtnSquareFilled() {
  return {
    width: 40, height: 40, borderRadius: 12, border: 'none',
    background: '#111',
    display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
    cursor: 'pointer', padding: 0, flexShrink: 0,
    boxShadow: '0 1px 2px rgba(0,0,0,0.1)',
  };
}
function glassPill(extra = {}) {
  return {
    height: 44, borderRadius: 999,
    background: 'rgba(255,255,255,0.72)',
    backdropFilter: 'blur(20px) saturate(180%)',
    WebkitBackdropFilter: 'blur(20px) saturate(180%)',
    border: '0.5px solid rgba(0,0,0,0.06)',
    boxShadow: '0 1px 2px rgba(0,0,0,0.06), 0 6px 16px rgba(0,0,0,0.08)',
    display: 'flex', alignItems: 'center',
    ...extra,
  };
}

Object.assign(window, {
  V1_ClassicIOS, V2_MinimalMono, V3_FloatingGlass, V4_TwoRow,
  V5_Ambient, V6_ExpandingSearch, V7_DarkImmersive, V8_DocumentStyle,
  PHONE_W, PHONE_H,
});
