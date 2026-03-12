import React, { useEffect, useMemo, useState } from "react";
import { apiGet, apiPatch, apiPost } from "../services/apiService";

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

function formatAverage(value) {
  if (value === null || value === undefined) return "";
  return Number(value).toFixed(2);
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
  const [newGrades, setNewGrades] = useState({});
  const [savingId, setSavingId] = useState(null);
  const [addingSubject, setAddingSubject] = useState(null);

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

    const rows = Array.isArray(nextCatalog?.subjects) ? nextCatalog.subjects : [];
    const nextDrafts = {};
    const nextNewGrades = {};

    rows.forEach((row) => {
      const grades = Array.isArray(row.grades) ? row.grades : [];
      grades.forEach((grade) => {
        nextDrafts[grade.id] = {
          grade_value: grade.grade_value,
          grade_date: grade.grade_date,
          version: grade.version,
        };
      });
      nextNewGrades[row.subject_name] = {
        grade_value: "",
        grade_date: "",
      };
    });

    setDrafts(nextDrafts);
    setNewGrades(nextNewGrades);
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

  function updateNewGrade(subjectName, field, value) {
    setNewGrades((current) => ({
      ...current,
      [subjectName]: {
        ...current[subjectName],
        [field]: field === "grade_value" ? value : value,
      },
    }));
  }

  async function reloadCurrentCatalog() {
    if (canBrowseStudents) {
      await loadStudentCatalog(selectedStudent);
      return;
    }
    await loadMyCatalog();
  }

  async function saveGrade(grade) {
    const draft = drafts[grade.id];
    if (!draft) return;

    setSavingId(grade.id);
    setBanner(null);
    try {
      await apiPatch(`/catalog/grades/${grade.id}`, draft, accessToken);
      await reloadCurrentCatalog();
      setBanner({ type: "ok", text: "Nota a fost actualizata." });
    } catch (error) {
      if ([409, 412, 423].includes(error?.status)) {
        setBanner({ type: "error", text: "Nota a fost modificata intre timp. Da Refresh si incearca din nou." });
      } else {
        setBanner({ type: "error", text: String(error?.message || error) });
      }
    } finally {
      setSavingId(null);
    }
  }

  async function addGrade(row) {
    const draft = newGrades[row.subject_name];
    const student = catalog?.student;
    if (!draft || !student) return;

    setAddingSubject(row.subject_name);
    setBanner(null);
    try {
      await apiPost(
        "/catalog/grades",
        {
          student_username: student.username,
          subject_name: row.subject_name,
          grade_value: Number(draft.grade_value),
          grade_date: draft.grade_date,
        },
        accessToken
      );
      await reloadCurrentCatalog();
      setBanner({ type: "ok", text: "Nota a fost adaugata." });
    } catch (error) {
      setBanner({ type: "error", text: String(error?.message || error) });
    } finally {
      setAddingSubject(null);
    }
  }

  const student = catalog?.student || null;
  const subjects = Array.isArray(catalog?.subjects) ? catalog.subjects : [];

  return (
    <section className="contentCard">
      <div className="contentHeader">
        <div>
          <div className="title">Catalog</div>
          <div className="subtitle">Media se afiseaza doar daca exista minim numarul de note cerut pentru materia respectiva.</div>
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
          <button className="btn" onClick={reloadCurrentCatalog} disabled={loading || (canBrowseStudents && !selectedStudent)}>
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
        </div>
      )}

      {loading ? (
        <div className="mutedBlock">Loading...</div>
      ) : !student ? (
        <div className="mutedBlock">Nu exista date pentru catalog.</div>
      ) : subjects.length === 0 ? (
        <div className="mutedBlock">Nu exista materii pentru elevul selectat.</div>
      ) : (
        <div className="tableWrap">
          <table className="tbl">
            <thead>
              <tr>
                <th>Materie</th>
                <th>Medie</th>
                <th>Nota si data</th>
                <th>Profesor</th>
              </tr>
            </thead>
            <tbody>
              {subjects.map((row) => {
                const grades = Array.isArray(row.grades) ? row.grades : [];
                const teachers = Array.isArray(row.teacher_names) ? row.teacher_names : [];
                const addDraft = newGrades[row.subject_name] || { grade_value: "", grade_date: "" };

                return (
                  <tr key={row.subject_name}>
                    <td>
                      <div className="cellTitle">{row.subject_name}</div>
                      <div className="catalogHint">{row.weekly_hours} ore/saptamana</div>
                    </td>
                    <td>
                      <div className="averageCell">{formatAverage(row.average)}</div>
                      {row.average === null || row.average === undefined ? (
                        <div className="catalogHint">minim {row.minimum_grades_for_average} note</div>
                      ) : null}
                    </td>
                    <td>
                      {grades.length === 0 && !row.can_add ? (
                        <span className="mutedSmall">-</span>
                      ) : (
                        <div className="catalogGradeList">
                          {grades.map((grade) => {
                            const draft = drafts[grade.id] || {
                              grade_value: grade.grade_value,
                              grade_date: grade.grade_date,
                              version: grade.version,
                            };
                            return (
                              <div key={grade.id} className="catalogGradeItem">
                                {grade.editable ? (
                                  <div className="catalogEditor">
                                    <input
                                      className="input small"
                                      type="number"
                                      min="1"
                                      max="10"
                                      value={draft.grade_value}
                                      onChange={(event) => updateDraft(grade.id, "grade_value", event.target.value)}
                                    />
                                    <input
                                      className="input small"
                                      type="date"
                                      value={draft.grade_date}
                                      onChange={(event) => updateDraft(grade.id, "grade_date", event.target.value)}
                                    />
                                    <button
                                      className="btn primary"
                                      onClick={() => saveGrade(grade)}
                                      disabled={savingId === grade.id || !draft.grade_date || !draft.grade_value}
                                    >
                                      Save
                                    </button>
                                  </div>
                                ) : (
                                  <div className="catalogPair">
                                    <span className={gradeTone(Number(grade.grade_value || 0))}>{grade.grade_value}</span>
                                    <span className="catalogDate">{formatDate(grade.grade_date)}</span>
                                  </div>
                                )}
                              </div>
                            );
                          })}

                          {row.can_add && (
                            <div className="catalogGradeItem">
                              <div className="catalogEditor">
                                <input
                                  className="input small"
                                  type="number"
                                  min="1"
                                  max="10"
                                  value={addDraft.grade_value}
                                  onChange={(event) => updateNewGrade(row.subject_name, "grade_value", event.target.value)}
                                  placeholder="Nota"
                                />
                                <input
                                  className="input small"
                                  type="date"
                                  value={addDraft.grade_date}
                                  onChange={(event) => updateNewGrade(row.subject_name, "grade_date", event.target.value)}
                                />
                                <button
                                  className="btn primary"
                                  onClick={() => addGrade(row)}
                                  disabled={addingSubject === row.subject_name || !addDraft.grade_date || !addDraft.grade_value}
                                >
                                  Add
                                </button>
                              </div>
                            </div>
                          )}
                        </div>
                      )}
                    </td>
                    <td>{teachers.length > 0 ? teachers.join(", ") : "-"}</td>
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

