import React, { useEffect, useState } from "react";
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

  async function deleteTimetable() {
    if (!classId) return;
    const selected = classes.find((c) => String(c.id) === classId);
    const confirmed = window.confirm(`Esti sigur ca vrei sa stergi orarul pentru clasa selectata? ${classLabel(selected)}`);
    if (!confirmed) return;

    setLoading(true);
    setBanner(null);
    try {
      await apiDelete(`/timetables/classes/${classId}`, accessToken);
      setBanner({ type: "ok", text: "Orar sters cu succes." });
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
      setBanner({ type: "ok", text: "Orarul a fost generat pentru clasa selectata." });
    } catch (e) {
      setBanner({ type: "error", text: String(e.message || e) });
    } finally {
      setLoading(false);
    }
  }

  async function deleteAndRegenerate() {
    if (!classId) return;
    const selected = classes.find((c) => String(c.id) === classId);
    const confirmed = window.confirm(`Esti sigur ca vrei sa stergi si sa regenerezi orarul pentru ${classLabel(selected)}?`);
    if (!confirmed) return;

    setLoading(true);
    setBanner(null);
    try {
      await apiDelete(`/timetables/classes/${classId}`, accessToken);
      const resp = await apiPost("/timetables/generate", { class_id: Number(classId) }, accessToken);
      const ids = resp?.job_ids ?? resp?.jobIds ?? [];
      setJobIds(Array.isArray(ids) ? ids : []);
      setBanner({ type: "ok", text: "Orarul a fost regenerat pentru clasa selectata." });
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
          <div className="subtitle">Creeaza orarul unei clase folosind profilul si profesorii disponibili.</div>
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
    </section>
  );
}
