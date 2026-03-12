import { Link } from 'react-router-dom'

const workflowSteps = [
  {
    title: 'Autentificare si acces pe roluri',
    text: 'Fiecare utilizator intra in platforma cu propriul cont si vede doar modulele potrivite rolului sau: elev, profesor, secretariat, admin sau sysadmin.',
  },
  {
    title: 'Orar generat si actualizat centralizat',
    text: 'Clasele primesc un orar unic, iar modificarile sunt facute din acelasi loc, astfel incat informatia sa ramana coerenta pentru toti cei implicati.',
  },
  {
    title: 'Catalog completat direct din platforma',
    text: 'Profesorii si secretariatul pot adauga sau modifica note, iar elevii isi vad imediat situatia scolara in acelasi spatiu digital.',
  },
  {
    title: 'Notificari pentru schimbarile importante',
    text: 'Cand apar modificari in orar sau in catalog, elevii primesc notificari persistente si pot reveni asupra lor pana cand le inchid manual.',
  },
  {
    title: 'Date pastrate intre sesiuni',
    text: 'Informatiile importante raman in baza de date, astfel incat orarul, notele si restul actualizarilor sa fie disponibile si dupa repornirea aplicatiei.',
  },
]

export default function HomePage() {
  return (
    <main className="landingPage">
      <section className="landingHero landingHeroSingle">
        <div className="landingTopbar">
          <div className="landingBrand">
            <div className="logo" aria-hidden="true"></div>
            <div>
              <div className="eyebrow">Platforma pentru liceu</div>
              <h1>Digitalization of Grading and School Management</h1>
            </div>
          </div>

          <div className="landingActions">
            <Link className="btn" to="/register">Creeaza cont</Link>
            <Link className="btn btn-primary" to="/login">Login</Link>
          </div>
        </div>

        <div className="landingGrid landingGridFocus">
          <div className="landingLead landingLeadMinimal">
            <div className="landingKicker">Gestionare scolara intr-un singur loc</div>
            <div className="landingTitle">Acces clar la orar, catalog si comunicarile importante.</div>
            <p className="landingCopy">
              Prima pagina ramane simpla si orientata spre intrarea in platforma, iar fluxul din dreapta descrie pe scurt cum circula informatia in aplicatie.
            </p>

            <div className="landingActions landingActionsInline">
              <Link className="btn btn-primary" to="/login">Intra in platforma</Link>
              <Link className="btn" to="/register">Cont nou</Link>
            </div>
          </div>

          <div className="landingPanel">
            <div className="landingPanelTag">Flux principal</div>
            <div className="landingPanelTitle">Cum functioneaza platforma</div>
            <div className="landingWorkflow">
              {workflowSteps.map((step, index) => (
                <div key={step.title} className="landingWorkflowItem">
                  <div className="landingWorkflowIndex">0{index + 1}</div>
                  <div className="landingWorkflowBody">
                    <div className="landingWorkflowHeading">{step.title}</div>
                    <p>{step.text}</p>
                  </div>
                </div>
              ))}
            </div>
          </div>
        </div>
      </section>
    </main>
  )
}
