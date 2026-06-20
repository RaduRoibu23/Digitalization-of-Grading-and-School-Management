import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { apiGetPublic } from '../services/apiService'
import ThemeToggle from '../components/ui/ThemeToggle'

const PROFILE_FALLBACK = [
  {
    id: 'filologie',
    title: 'Filologie',
    profile_name: 'Filologie',
    summary: 'Accent pe limbi, literatura si discipline umaniste, cu un parcurs echilibrat pe toate nivelurile de liceu.',
    details: [
      'Ore pe nivel: IX: 31 ore, X: 31 ore, XI: 32 ore, XII: 32 ore.',
      'Materii reprezentative: Limba si literatura romana, Istorie, Geografie, Limba engleza.',
      'Curriculum orientat spre competente de comunicare, analiza de text si cultura generala.',
    ],
    total_weekly_hours_by_level: { IX: 31, X: 31, XI: 32, XII: 32 },
    accent: 'amber',
  },
  {
    id: 'mate-info',
    title: 'Matematica-Informatica',
    profile_name: 'Matematica-Informatica',
    summary: 'Profil real cu accent pe matematica, informatica si stiintele fundamentale, construit pentru continuitate tehnica.',
    details: [
      'Ore pe nivel: IX: 31 ore, X: 31 ore, XI: 32 ore, XII: 32 ore.',
      'Materii reprezentative: Matematica, Informatica, Fizica, Limba engleza.',
      'Curriculum orientat spre logica, modelare si lucru sustinut pe discipline STEM.',
    ],
    total_weekly_hours_by_level: { IX: 31, X: 31, XI: 32, XII: 32 },
    accent: 'teal',
  },
  {
    id: 'mate-info-intensiv',
    title: 'Matematica-Informatica Intensiv',
    profile_name: 'Matematica-Informatica Intensiv',
    summary: 'Varianta intensiva creste ponderea orelor de informatica si consolideaza traseul spre specializari tehnice.',
    details: [
      'Ore pe nivel: IX: 31 ore, X: 31 ore, XI: 32 ore, XII: 32 ore.',
      'Materii reprezentative: Matematica, Informatica intensiv, Fizica, Limba engleza.',
      'Curriculum gandit pentru aprofundare si ritm mai sustinut pe zona digitala.',
    ],
    total_weekly_hours_by_level: { IX: 31, X: 31, XI: 32, XII: 32 },
    accent: 'slate',
  },
]

function normalizeProfiles(data) {
  const list = Array.isArray(data) ? data : []
  return list.map((item, index) => ({
    id: String(item.profile_name || item.title || index).toLowerCase().replace(/\s+/g, '-'),
    title: item.profile_name || item.title || 'Profil',
    profile_name: item.profile_name || item.title || 'Profil',
    summary: item.summary || '',
    details: Array.isArray(item.details) ? item.details : [],
    total_weekly_hours_by_level: item.total_weekly_hours_by_level || {},
    accent: item.accent || 'teal',
  }))
}

export default function HomePage() {
  const [profiles, setProfiles] = useState(PROFILE_FALLBACK)
  const [activeFeatureId, setActiveFeatureId] = useState(PROFILE_FALLBACK[0].id)

  useEffect(() => {
    let ignore = false
    ;(async () => {
      try {
        const data = await apiGetPublic('/public/curriculum-profiles')
        if (ignore) return
        const nextProfiles = normalizeProfiles(data)
        if (nextProfiles.length > 0) {
          setProfiles(nextProfiles)
          setActiveFeatureId((current) =>
            nextProfiles.some((feature) => feature.id === current) ? current : nextProfiles[0].id
          )
        }
      } catch {
        if (!ignore) {
          setProfiles(PROFILE_FALLBACK)
        }
      }
    })()
    return () => {
      ignore = true
    }
  }, [])

  const activeFeature = useMemo(
    () => profiles.find((feature) => feature.id === activeFeatureId) ?? profiles[0] ?? PROFILE_FALLBACK[0],
    [activeFeatureId, profiles]
  )
  const mainMetric = `Clasa IX: ${activeFeature?.total_weekly_hours_by_level?.IX ?? '-'} ore / saptamana`

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
            <ThemeToggle />
            <Link className="btn btn-primary" to="/login">Login</Link>
          </div>
        </div>

        <div className="landingCommandDeck">
          <section className="landingCommandIntro anim-fade-up">
            <div className="landingTitle landingTitleWide">
              Bun venit! Mai jos gasesti descrierea profilelor pe care acest liceu le are.
            </div>
            <p className="landingCopy landingCopyWide">
              Informatiile sunt prezentate direct din curriculumul real configurat in platforma, astfel incat sa vezi rapid
              specificul fiecarui profil si repartizarea orelor pe fiecare nivel.
            </p>

            <div className="landingActions landingActionsInline">
              <Link className="btn btn-primary" to="/login">Intra in platforma</Link>
            </div>
          </section>

          <section className="landingCommandLead">
            <div className="landingHoverGrid anim-stagger" aria-label="Profilele liceului">
              {profiles.map((feature) => (
                <button
                  key={feature.id}
                  className={`landingHoverCard accent-${feature.accent} anim-fade-up ${activeFeature.id === feature.id ? 'active' : ''}`.trim()}
                  type="button"
                  onMouseEnter={() => setActiveFeatureId(feature.id)}
                  onFocus={() => setActiveFeatureId(feature.id)}
                >
                  <span className="landingHoverEyebrow">Profil liceal</span>
                  <strong>{feature.title}</strong>
                  <span>{feature.summary}</span>
                </button>
              ))}
            </div>
          </section>

          <aside className={`landingInfoPanel accent-${activeFeature.accent} anim-fade-up anim-d3`.trim()}>
            <div className="landingPanelTitle">{activeFeature.title}</div>
            <p className="landingPanelCopy">{activeFeature.summary}</p>

            <div className="landingInfoMetric">{mainMetric}</div>

            <div className="landingInfoList">
              {(activeFeature.details || []).map((detail) => (
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
