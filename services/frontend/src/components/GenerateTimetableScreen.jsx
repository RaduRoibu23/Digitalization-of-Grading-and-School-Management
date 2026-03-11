import React, { useEffect, useMemo, useState } from "react";
import { apiGet, apiPost, apiDelete } from "../services/apiService";

function classLabel(c) {
  const name = c?.name ?? c?.class_name ?? `Clasa ${c?.id}`;
  const profile = c?.profile ?? c?.class_profile;
  return profile ? `${name} - ${profile}` : name;
}

export default function GenerateTimetableScreen({ accessToken }) {
  const [classes, setClasses] = useState([]);
  const [classId, setClassId] = useState("");
  const [loading, setLoading] = useState(false);
  const [banner, setBanner] = useState(null);
  const [jobIds, setJobIds] = useState([]);
  const [generatedEntries, setGeneratedEntries] = useState([]);

  const selectedClass = useMemo(
    () => classes.find((item) => String(item.id) === String(classId)) ?? null,
    [classes, classId]
  );

  useEffect(() => {
    (async () => {
      try {
        const c = await apiGet("/classes", accessToken);
        const list = Array.isArray(c) ? c : [];
        setClasses(list);
        if (list.length > 0) setClassId(String(list[0].id));
      } catch (e) {
        setBanner({ type: "error", text: String(e.message || e) });
      }
    })();
  }, [accessToken]);

  useEffect(() => {
    if (!classId) {
      setGeneratedEntries([]);
      return;
    }
    loadGeneratedEntries(classId);
  }, [accessToken, classId]);

  async function loadGeneratedEntries(nextClassId) {
    try {
      const data = await apiGet(`/timetables/classes/${nextClassId}`, accessToken);
      setGeneratedEntries(Array.isArray(data) ? data : []);
    } catch (e) {
      setGeneratedEntries([]);
    }
  }

  async function deleteTimetable() {
    if (!classId) return;
    const confirmed = window.confirm(`Esti sigur ca vrei sa stergi orarul pentru clasa selectata? ${classLabel(selectedClass)}`);
    if (!confirmed) return;

    setLoading(true);
    setBanner(null);
    try {
      await apiDelete(`/timetables/classes/${classId}`, accessToken);
      setGeneratedEntries([]);
      setBanner({ type: "ok", text: "Orarul a fost sters. Clasa nu mai are intrari pana la o noua generare." });
    } catch (e) {
      setBanner({ type: "error", text: String(e.message || e) });
    } finally {
      setLoading(false);
    }
  }

  async function generate() {
    if (!classId) return;
    setLoading(true);
    setBanner(null);
    try {
      const resp = await apiPost("/timetables/generate", { class_id: Number(classId) }, accessToken);
      const ids = resp?.job_ids ?? resp?.jobIds ?? [];
      setJobIds(Array.isArray(ids) ? ids : []);
      await loadGeneratedEntries(classId);
      setBanner({ type: "ok", text: "Orarul a fost generat si este disponibil mai jos pentru verificare." });
    } catch (e) {
      setBanner({ type: "error", text: String(e.message || e) });
    } finally {
      setLoading(false);
    }
  }

  async function deleteAndRegenerate() {
    if (!classId) return;
    const confirmed = window.confirm(`Esti sigur ca vrei sa stergi si sa regenerezi orarul pentru ${classLabel(selectedClass)}?`);
    if (!confirmed) return;

    setLoading(true);
    setBanner(null);
    try {
      await apiDelete(`/timetables/classes/${classId}`, accessToken);
      const resp = await apiPost("/timetables/generate", { class_id: Number(classId) }, accessToken);
      const ids = resp?.job_ids ?? resp?.jobIds ?? [];
      setJobIds(Array.isArray(ids) ? ids : []);
      await loadGeneratedEntries(classId);
      setBanner({ type: "ok", text: "Orarul a fost regenerat si actualizat." });
    } catch (e) {
      setBanner({ type: "error", text: String(e.message || e) });
    } finally {
      setLoading(false);
    }
  }

  return (
    <section className="contentCard">
      <div className="contentHeader">
        <div>
          <div className="title">Genereaza orar</div>
          <div className="subtitle">Creeaza orarul unei clase folosind profilul, profesorii si salile disponibile.</div>
        </div>
        <div className="headerActions">
          <label className="label">Clasa</label>
          <select className="select" value={classId} onChange={(e) => setClassId(e.target.value)} disabled={loading}>
            {classes.map((c) => (
              <option key={c.id} value={String(c.id)}>
                {classLabel(c)}
              </option>
            ))}
          </select>

          <button className="btn primary" onClick={generate} disabled={loading || !classId}>
            Generate
          </button>
          <button className="btn" onClick={deleteTimetable} disabled={loading || !classId}>
            Delete
          </button>
          <button className="btn" onClick={deleteAndRegenerate} disabled={loading || !classId}>
            Delete & Regenerate
          </button>
        </div>
      </div>

      {banner && <div className={`banner ${banner.type}`}>{banner.text}</div>}

      {jobIds.length > 0 && (
        <div className="mutedBlock">
          <div style={{ marginBottom: 8, color: "var(--text)", fontWeight: 800 }}>Job IDs:</div>
          <ul style={{ margin: 0, paddingLeft: 18 }}>
            {jobIds.map((id) => (
              <li key={id}>{id}</li>
            ))}
          </ul>
        </div>
      )}

      <div className="mutedBlock" style={{ marginTop: 16 }}>
        {selectedClass ? `${classLabel(selectedClass)} are acum ${generatedEntries.length} sloturi generate.` : "Selecteaza o clasa."}
      </div>

      {generatedEntries.length > 0 && (
        <div className="tableWrap" style={{ marginTop: 16 }}>
          <table className="tbl">
            <thead>
              <tr>
                <th>Zi</th>
                <th>Slot</th>
                <th>Materie</th>
                <th>Profesor</th>
                <th>Sala</th>
              </tr>
            </thead>
            <tbody>
              {generatedEntries.slice(0, 8).map((entry) => (
                <tr key={entry.id}>
                  <td>{entry.weekday}</td>
                  <td>{entry.index_in_day}</td>
                  <td>{entry.subject_name}</td>
                  <td>{entry.teacher_name}</td>
                  <td>{entry.room_name}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}
