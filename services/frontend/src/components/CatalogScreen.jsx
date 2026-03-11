import React, { useEffect, useMemo, useState } from "react";
import { apiGet, apiPatch } from "../services/apiService";

function studentLabel(student) {
  if (!student) return "";
  const name = `${student.last_name || ""} ${student.first_name || ""}`.trim();
  const className = student.class_name || "";
  return className ? `${name} - ${className}` : name;
}

function formatDate(value) {
  if (!value) return "-";
  const parts = String(value).split("-");
  if (parts.length !== 3) return value;
  return `${parts[2]}.${parts[1]}.${parts[0]}`;
}

function gradeTone(value) {
  if (value >= 9) return "gradeBadge excellent";
  if (value >= 7) return "gradeBadge good";
  return "gradeBadge warn";
}

export default function CatalogScreen({ accessToken, roles }) {
  const [loading, setLoading] = useState(false);
  const [banner, setBanner] = useState(null);
  const [students, setStudents] = useState([]);
  const [selectedStudent, setSelectedStudent] = useState("");
  const [catalog, setCatalog] = useState(null);
  const [drafts, setDrafts] = useState({});
  const [savingId, setSavingId] = useState(null);

  const canBrowseStudents = useMemo(
    () => roles.some((role) => ["professor", "secretariat", "admin", "sysadmin"].includes(role)),
    [roles]
  );

  useEffect(() => {
    if (canBrowseStudents) {
      loadStudents();
      return;
    }
    loadMyCatalog();
  }, [accessToken, canBrowseStudents]);

  useEffect(() => {
    if (!canBrowseStudents || !selectedStudent) return;
    loadStudentCatalog(selectedStudent);
  }, [accessToken, canBrowseStudents, selectedStudent]);

  async function loadStudents() {
    setLoading(true);
    setBanner(null);
    try {
      const data = await apiGet("/catalog/students", accessToken);
      const list = Array.isArray(data) ? data : [];
      setStudents(list);
      if (list.length > 0) {
        setSelectedStudent((current) => current || list[0].username);
      }
    } catch (error) {
      setBanner({ type: "error", text: String(error?.message || error) });
    } finally {
      setLoading(false);
    }
  }

  async function loadMyCatalog() {
    setLoading(true);
    setBanner(null);
    try {
      const data = await apiGet("/catalog/me", accessToken);
      applyCatalog(data);
    } catch (error) {
      setBanner({ type: "error", text: String(error?.message || error) });
    } finally {
      setLoading(false);
    }
  }

  async function loadStudentCatalog(username) {
    setLoading(true);
    setBanner(null);
    try {
      const data = await apiGet(`/catalog/students/${username}`, accessToken);
      applyCatalog(data);
    } catch (error) {
      setBanner({ type: "error", text: String(error?.message || error) });
    } finally {
      setLoading(false);
    }
  }

  function applyCatalog(data) {
    const nextCatalog = data && typeof data === "object" ? data : null;
    setCatalog(nextCatalog);
    const gradeRows = Array.isArray(nextCatalog?.grades) ? nextCatalog.grades : [];
    setDrafts(Object.fromEntries(
      gradeRows.map((row) => [row.id, {
        grade_value: row.grade_value,
        grade_date: row.grade_date,
        version: row.version,
      }])
    ));
  }

  function updateDraft(gradeId, field, value) {
    setDrafts((current) => ({
      ...current,
      [gradeId]: {
        ...current[gradeId],
        [field]: field === "grade_value" ? Number(value) : value,
      },
    }));
  }

  async function saveGrade(row) {
    const draft = drafts[row.id];
    if (!draft) return;

    setSavingId(row.id);
    setBanner(null);
    try {
      const updated = await apiPatch(`/catalog/grades/${row.id}`, draft, accessToken);
      setCatalog((current) => ({
        ...current,
        grades: current.grades.map((grade) => (grade.id === row.id ? updated : grade)),
      }));
      setDrafts((current) => ({
        ...current,
        [row.id]: {
          grade_value: updated.grade_value,
          grade_date: updated.grade_date,
          version: updated.version,
        },
      }));
      setBanner({ type: "ok", text: "Nota a fost actualizata." });
    } catch (error) {
      setBanner({ type: "error", text: String(error?.message || error) });
    } finally {
      setSavingId(null);
    }
  }

  const student = catalog?.student || null;
  const grades = Array.isArray(catalog?.grades) ? catalog.grades : [];
  const average = grades.length > 0
    ? (grades.reduce((sum, row) => sum + Number(row.grade_value || 0), 0) / grades.length).toFixed(2)
    : null;

  return (
    <section className="contentCard">
      <div className="contentHeader">
        <div>
          <div className="title">Catalog</div>
          <div className="subtitle">Notele elevului selectat, cu editare controlata pe rol si materie.</div>
        </div>
        <div className="headerActions">
          {canBrowseStudents && (
            <>
              <label className="label">Elev</label>
              <select
                className="select"
                value={selectedStudent}
                onChange={(event) => setSelectedStudent(event.target.value)}
                disabled={loading || students.length === 0}
              >
                {students.map((item) => (
                  <option key={item.username} value={item.username}>
                    {studentLabel(item)}
                  </option>
                ))}
              </select>
            </>
          )}
          <button
            className="btn"
            onClick={() => (canBrowseStudents ? loadStudentCatalog(selectedStudent) : loadMyCatalog())}
            disabled={loading || (canBrowseStudents && !selectedStudent)}
          >
            Refresh
          </button>
        </div>
      </div>

      {banner && <div className={`banner ${banner.type}`}>{banner.text}</div>}

      {student && (
        <div className="catalogStats">
          <div className="statPill">
            <strong>Elev:</strong> {student.first_name} {student.last_name}
          </div>
          <div className="statPill">
            <strong>Clasa:</strong> {student.class_name || "-"}
          </div>
          <div className="statPill">
            <strong>Medie note:</strong> {average || "-"}
          </div>
        </div>
      )}

      {loading ? (
        <div className="mutedBlock">Loading...</div>
      ) : !student ? (
        <div className="mutedBlock">Nu exista date pentru catalog.</div>
      ) : grades.length === 0 ? (
        <div className="mutedBlock">Nu exista note pentru elevul selectat.</div>
      ) : (
        <div className="tableWrap">
          <table className="tbl">
            <thead>
              <tr>
                <th>Materie</th>
                <th>Nota</th>
                <th>Data</th>
                <th>Profesor</th>
                <th>Actiuni</th>
              </tr>
            </thead>
            <tbody>
              {grades.map((row) => {
                const draft = drafts[row.id] || {
                  grade_value: row.grade_value,
                  grade_date: row.grade_date,
                  version: row.version,
                };

                return (
                  <tr key={row.id}>
                    <td>
                      <div className="cellTitle">{row.subject_name}</div>
                    </td>
                    <td>
                      <span className={gradeTone(Number(row.grade_value || 0))}>{row.grade_value}</span>
                    </td>
                    <td>{formatDate(row.grade_date)}</td>
                    <td>{row.teacher_name || "-"}</td>
                    <td>
                      {row.editable ? (
                        <div className="catalogEditor">
                          <input
                            className="input small"
                            type="number"
                            min="1"
                            max="10"
                            value={draft.grade_value}
                            onChange={(event) => updateDraft(row.id, "grade_value", event.target.value)}
                          />
                          <input
                            className="input small"
                            type="date"
                            value={draft.grade_date}
                            onChange={(event) => updateDraft(row.id, "grade_date", event.target.value)}
                          />
                          <button
                            className="btn primary"
                            onClick={() => saveGrade(row)}
                            disabled={savingId === row.id || !draft.grade_date || !draft.grade_value}
                          >
                            Save
                          </button>
                        </div>
                      ) : (
                        <span className="mutedSmall">Vizualizare</span>
                      )}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}
