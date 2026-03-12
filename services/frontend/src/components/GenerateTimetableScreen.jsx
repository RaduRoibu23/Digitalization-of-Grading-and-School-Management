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

  const selectedClass = useMemo(
    () => classes.find((item) => String(item.id) === String(classId)) ?? null,
    [classes, classId]
  );

  useEffect(() => {
    (async () => {
      try {
        const data = await apiGet("/classes", accessToken);
        const list = Array.isArray(data) ? data : [];
        setClasses(list);
        if (list.length > 0) {
          setClassId(String(list[0].id));
        }
      } catch (error) {
        setBanner({ type: "error", text: String(error?.message || error) });
      }
    })();
  }, [accessToken]);

  async function deleteTimetable() {
    if (!classId) return;
    const confirmed = window.confirm(`Esti sigur ca vrei sa stergi orarul pentru clasa selectata? ${classLabel(selectedClass)}`);
    if (!confirmed) return;

    setLoading(true);
    setBanner(null);
    try {
      await apiDelete(`/timetables/classes/${classId}`, accessToken);
      setBanner({ type: "ok", text: "Orarul a fost sters." });
    } catch (error) {
      setBanner({ type: "error", text: String(error?.message || error) });
    } finally {
      setLoading(false);
    }
  }

  async function generate() {
    if (!classId) return;
    setLoading(true);
    setBanner(null);
    try {
      await apiPost("/timetables/generate", { class_id: Number(classId) }, accessToken);
      setBanner({ type: "ok", text: "Orarul a fost generat cu succes." });
    } catch (error) {
      setBanner({ type: "error", text: String(error?.message || error) });
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
      await apiPost("/timetables/generate", { class_id: Number(classId) }, accessToken);
      setBanner({ type: "ok", text: "Orarul a fost regenerat cu succes." });
    } catch (error) {
      setBanner({ type: "error", text: String(error?.message || error) });
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
          <select className="select" value={classId} onChange={(event) => setClassId(event.target.value)} disabled={loading}>
            {classes.map((schoolClass) => (
              <option key={schoolClass.id} value={String(schoolClass.id)}>
                {classLabel(schoolClass)}
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
    </section>
  );
}
