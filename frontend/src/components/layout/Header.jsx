export default function Header({ status }) {
  return (
    <div className="header">
      <div className="brand">
        <div className="logo" aria-hidden="true"></div>
        <div>
          <div className="eyebrow">School platform</div>
          <h1>Digitalization of Grading and School Management</h1>
          <div className="sub">Catalog digital, orar si comunicare scolara intr-un singur spatiu.</div>
        </div>
      </div>

      <div className="headerInfo">
        <div id="status-badge" className="badge statusBadge">
          <span className="statusDot"></span>
          Status: {status}
        </div>
      </div>
    </div>
  )
}