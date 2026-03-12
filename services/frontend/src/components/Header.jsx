export default function Header({ status }) {
  return (
    <div className="header">
      <div className="brand">
        <div className="logo" aria-hidden="true"></div>
        <div>
          <h1>Digitalization of Grading and School Management</h1>
          <div className="sub">Frontend</div>
        </div>
      </div>
      <div id="status-badge" className="badge">
        Status: {status}
      </div>
    </div>
  );
}
