import { useState } from 'react'
import { Link } from 'react-router-dom'

const FEATURE_CARDS = [
  {
    id: 'timetable',
    eyebrow: 'Core module',
    title: 'Generator orare automat',
    summary: 'Genereaza orare complete, tine cont de profesori, sali si clase, apoi permite ajustari manuale fara sa rupi regulile hard.',
    details: [
      'Genereaza un orar complet pentru fiecare clasa din acelasi nucleu de date.',
      'Pastreaza restrictii reale pe profesori, sali si suprapuneri intre sloturi.',
      'Permite swap sau mutare manuala direct din grid, cu validare pe server.',
    ],
    metric: '10 clase demo, grid complet si editare manuala',
    accent: 'teal',
  },
  {
    id: 'notifications',
    eyebrow: 'Always on',
    title: 'Notification System',
    summary: 'Notificarile importante apar intr-un inbox global si pot trimite utilizatorul exact in modulul care s-a schimbat.',
    details: [
      'Badge pentru necitite si centru de notificari cu filtre rapide.',
      'Deep-link direct catre documente, feedback, catalog sau orar.',
      'Control separat pentru notificari in aplicatie si pe email.',
    ],
    metric: 'Inbox persistent, unread count si actiuni rapide',
    accent: 'amber',
  },
  {
    id: 'mail',
    eyebrow: 'Delivery layer',
    title: 'Mail System',
    summary: 'Cand fluxul o cere, platforma poate livra notificari si prin email, cu configurare separata de canalul in-app.',
    details: [
      'Mesajele importante se pot dubla pe email pentru utilizatorii care aleg asta.',
      'Secretariatul si sistemul pot declansa notificari tranzactionale coerente.',
      'Setarile de profil decid daca utilizatorul primeste sau nu email.',
    ],
    metric: 'Canal separat pentru comunicari tranzactionale',
    accent: 'slate',
  },
]

export default function HomePage() {
  const [activeFeatureId, setActiveFeatureId] = useState(FEATURE_CARDS[0].id)

  const activeFeature = FEATURE_CARDS.find((feature) => feature.id === activeFeatureId) ?? FEATURE_CARDS[0]

  return (
    <main className="landingPage landingPageCompact">
      <section className="landingHero landingHeroCommand">
        <div className="landingTopbar">
          <div className="landingBrand">
            <div className="logo" aria-hidden="true"></div>
            <div>
              <div className="eyebrow">Platforma pentru liceu</div>
              <h1>Digitalization of Grading and School Management</h1>
            </div>
          </div>

          <div className="landingActions">
            <Link className="btn btn-primary" to="/login">Login</Link>
          </div>
        </div>

        <div className="landingCommandDeck">
          <section className="landingCommandLead">
            <div className="landingKicker">Academic workspace</div>
            <div className="landingTitle landingTitleCompact">
              Orar, notificari si fluxuri scolare intr-un ecran mai clar.
            </div>

            <div className="landingActions landingActionsInline">
              <Link className="btn btn-primary" to="/login">Intra in platforma</Link>
            </div>

            <div className="landingHoverGrid" aria-label="Module principale">
              {FEATURE_CARDS.map((feature) => (
                <button
                  key={feature.id}
                  className={`landingHoverCard accent-${feature.accent} ${activeFeature.id === feature.id ? 'active' : ''}`.trim()}
                  type="button"
                  onMouseEnter={() => setActiveFeatureId(feature.id)}
                  onFocus={() => setActiveFeatureId(feature.id)}
                >
                  <span className="landingHoverEyebrow">{feature.eyebrow}</span>
                  <strong>{feature.title}</strong>
                  <span>{feature.summary}</span>
                </button>
              ))}
            </div>
          </section>

          <aside className={`landingInfoPanel accent-${activeFeature.accent}`.trim()}>
            <div className="landingPanelTitle">{activeFeature.title}</div>
            <p className="landingPanelCopy">{activeFeature.summary}</p>

            <div className="landingInfoMetric">{activeFeature.metric}</div>

            <div className="landingInfoList">
              {activeFeature.details.map((detail) => (
                <div key={detail} className="landingInfoItem">
                  <span className="landingInfoDot" aria-hidden="true"></span>
                  <span>{detail}</span>
                </div>
              ))}
            </div>
          </aside>
        </div>
      </section>
    </main>
  )
}
