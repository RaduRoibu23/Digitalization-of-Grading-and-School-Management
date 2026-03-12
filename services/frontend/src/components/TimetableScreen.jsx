import React, { useEffect, useMemo, useState } from "react";
import { apiGet, apiPatch, apiDelete } from "../services/apiService";

const WEEKDAY = ["Luni", "Marti", "Miercuri", "Joi", "Vineri"];
const TIME_LABELS = {
  1: "08:00-08:50",
  2: "09:00-09:50",
  3: "10:00-10:50",
  4: "11:00-11:50",
  5: "12:00-12:50",
  6: "13:00-13:50",
  7: "14:00-14:50",
};

const POLL_MS = 8000;

function canEdit(roles) {
  const allowed = ["secretariat", "scheduler", "admin", "sysadmin"];
  return roles.some((r) => allowed.includes(r));
}

function normalizeEntry(entry) {
  if (!entry || typeof entry !== "object") return null;
  return {
    id: entry.id,
    classId: entry.classId ?? entry.class_id ?? null,
    className: entry.className ?? entry.class_name ?? null,
    subjectId: entry.subjectId ?? entry.subject_id ?? null,
    subjectName: entry.subjectName ?? entry.subject_name ?? null,
    roomId: entry.roomId ?? entry.room_id ?? null,
    roomName: entry.roomName ?? entry.room_name ?? null,
    teacherUsername: entry.teacherUsername ?? entry.teacher_username ?? null,
    teacherName: entry.teacherName ?? entry.teacher_name ?? null,
    weekday: entry.weekday ?? null,
    indexInDay: entry.indexInDay ?? entry.index_in_day ?? null,
    version: entry.version ?? null,
  };
}

function signature(list) {
  return JSON.stringify(
    (Array.isArray(list) ? list : [])
      .slice()
      .sort((a, b) => a.id - b.id)
      .map((e) => ({ id: e.id, s: e.subjectId, r: e.roomId ?? null, v: e.version }))
  );
}

export default function TimetableScreen({ accessToken, roles, mode }) {
  const [loading, setLoading] = useState(false);
  const [banner, setBanner] = useState(null);
  const [classes, setClasses] = useState([]);
  const [selectedClassId, setSelectedClassId] = useState(1);
  const [classSearch, setClassSearch] = useState("");
  const [subjects, setSubjects] = useState([]);
  const [rooms, setRooms] = useState([]);
  const [entries, setEntries] = useState([]);
  const [original, setOriginal] = useState([]);
  const [isEditing, setIsEditing] = useState(false);
  const [needsRefresh, setNeedsRefresh] = useState(false);
  const [lastSig, setLastSig] = useState("");

  const subjectsById = useMemo(() => {
    const map = new Map();
    subjects.forEach((subject) => map.set(subject.id, subject));
    return map;
  }, [subjects]);

  const roomsById = useMemo(() => {
    const map = new Map();
    rooms.forEach((room) => map.set(room.id, room));
    return map;
  }, [rooms]);

  const editingAllowed = canEdit(roles);

  const filteredClasses = useMemo(() => {
    if (!classSearch.trim()) return classes;
    const query = classSearch.trim().toLowerCase();
    return classes.filter((schoolClass) =>
      String(schoolClass.name ?? schoolClass.class_name ?? schoolClass.id).toLowerCase().includes(query)
    );
  }, [classes, classSearch]);

  const selectedClassName = useMemo(() => {
    const schoolClass = classes.find((item) => item.id === selectedClassId);
    return schoolClass?.name ?? schoolClass?.class_name ?? "";
  }, [classes, selectedClassId]);

  async function loadSubjects() {
    const data = await apiGet("/subjects", accessToken);
    setSubjects(Array.isArray(data) ? data : []);
  }

  async function loadRooms() {
    try {
      const data = await apiGet("/rooms", accessToken);
      setRooms(Array.isArray(data) ? data : []);
    } catch (error) {
      setRooms([]);
    }
  }

  async function loadClasses() {
    const data = await apiGet("/classes", accessToken);
    const list = Array.isArray(data) ? data : [];
    setClasses(list);
    if (list.length > 0) {
      setSelectedClassId((current) => current || list[0].id);
    }
  }

  async function loadTimetableForClass(classId) {
    const data = await apiGet(`/timetables/classes/${classId}`, accessToken);
    const list = (Array.isArray(data) ? data : [])
      .map(normalizeEntry)
      .filter((entry) => entry && entry.id && typeof entry.id === "number");
    list.sort((a, b) => (a.weekday - b.weekday) || (a.indexInDay - b.indexInDay));
    setEntries(list);
    setOriginal(JSON.parse(JSON.stringify(list)));
    setLastSig(signature(list));
  }

  async function loadMyTimetable() {
    if (roles.includes("professor")) {
      const data = await apiGet("/timetables/me/teacher", accessToken);
      const list = (Array.isArray(data) ? data : [])
        .map(normalizeEntry)
        .filter((entry) => entry && entry.id && typeof entry.id === "number");
      list.sort((a, b) => (a.weekday - b.weekday) || (a.indexInDay - b.indexInDay));
      setEntries(list);
      setOriginal(JSON.parse(JSON.stringify(list)));
      setLastSig(signature(list));
      return;
    }

    const me = await apiGet("/me", accessToken);
    const classId = me?.class_id ?? me?.classId ?? me?.class?.id;
    if (!classId) {
      setEntries([]);
      setOriginal([]);
      setLastSig("");
      setBanner({ type: "warn", text: "Nu pot determina clasa ta din profil." });
      return;
    }

    await loadTimetableForClass(classId);
  }

  async function initialLoad() {
    setLoading(true);
    setBanner(null);
    try {
      await loadSubjects();
      await loadRooms();
      if (mode === "class") {
        await loadClasses();
      }
    } catch (error) {
      setBanner({ type: "error", text: String(error?.message || error) });
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    initialLoad();
  }, [mode]);

  useEffect(() => {
    (async () => {
      setBanner(null);
      setLoading(true);
      try {
        if (mode === "class") {
          await loadTimetableForClass(selectedClassId);
        } else {
          await loadMyTimetable();
        }
      } catch (error) {
        setBanner({ type: "error", text: String(error?.message || error) });
      } finally {
        setLoading(false);
      }
    })();
  }, [mode, selectedClassId]);

  useEffect(() => {
    if (isEditing) return;

    const intervalId = setInterval(async () => {
      try {
        const classId = mode === "class"
          ? selectedClassId
          : (await apiGet("/me", accessToken))?.class_id ?? (await apiGet("/me", accessToken))?.classId ?? (await apiGet("/me", accessToken))?.class?.id;
        if (!classId) return;

        const data = await apiGet(`/timetables/classes/${classId}`, accessToken);
        const list = (Array.isArray(data) ? data : []).map(normalizeEntry).filter(Boolean);
        list.sort((a, b) => (a.weekday - b.weekday) || (a.indexInDay - b.indexInDay));
        const nextSig = signature(list);
        if (nextSig !== lastSig) {
          setEntries(list);
          setOriginal(JSON.parse(JSON.stringify(list)));
          setLastSig(nextSig);
        }
      } catch {
      }
    }, POLL_MS);

    return () => clearInterval(intervalId);
  }, [accessToken, isEditing, lastSig, mode, selectedClassId]);

  function updateEntryLocal(entryId, patch) {
    setEntries((current) => current.map((entry) => (entry.id === entryId ? { ...entry, ...patch } : entry)));
  }

  function computeChanges() {
    const originalById = new Map(original.map((entry) => [entry.id, entry]));
    const changes = [];

    for (const entry of entries) {
      const initial = originalById.get(entry.id);
      if (!initial) continue;
      const subjectChanged = entry.subjectId !== initial.subjectId;
      const roomChanged = (entry.roomId ?? null) !== (initial.roomId ?? null);
      if (subjectChanged || roomChanged) {
        const body = { version: entry.version };
        if (subjectChanged) body.subject_id = entry.subjectId;
        if (roomChanged) body.room_id = entry.roomId ?? null;
        changes.push({ id: entry.id, body });
      }
    }

    return changes;
  }

  function beginEdit() {
    setNeedsRefresh(false);
    setIsEditing(true);
    setBanner(null);
  }

  function cancelEdit() {
    setIsEditing(false);
    setEntries(JSON.parse(JSON.stringify(original)));
    setBanner(null);
  }

  async function saveAll() {
    setLoading(true);
    setBanner(null);
    const changes = computeChanges();
    if (changes.length === 0) {
      setBanner({ type: "ok", text: "Nu exista modificari de salvat." });
      setLoading(false);
      setIsEditing(false);
      return;
    }

    try {
      for (const change of changes) {
        await apiPatch(`/timetables/entries/${change.id}`, change.body, accessToken);
      }
      setIsEditing(false);
      setNeedsRefresh(false);
      if (mode === "class") {
        await loadTimetableForClass(selectedClassId);
      } else {
        await loadMyTimetable();
      }
      setBanner({ type: "ok", text: "Modificarile au fost salvate." });
    } catch (error) {
      const status = error?.status || 500;
      const message = String(error?.message || error);
      const versionConflict = [409, 412, 423].includes(status) && message.toLowerCase().includes("intre timp");
      if (status === 404 || versionConflict) {
        setNeedsRefresh(true);
        setBanner({ type: "warn", text: "Orarul a fost modificat intre timp. Da refresh si incearca din nou." });
      } else {
        setNeedsRefresh(false);
        setBanner({ type: "error", text: message });
      }
    } finally {
      setLoading(false);
    }
  }

  async function refreshNow() {
    setNeedsRefresh(false);
    setIsEditing(false);
    setBanner(null);
    setLoading(true);
    try {
      if (mode === "class") {
        await loadTimetableForClass(selectedClassId);
      } else {
        await loadMyTimetable();
      }
    } catch (error) {
      setBanner({ type: "error", text: String(error?.message || error) });
    } finally {
      setLoading(false);
    }
  }

  const byKey = new Map();
  for (const entry of entries) {
    byKey.set(`${entry.weekday}-${entry.indexInDay}`, entry);
  }
  const rows = Array.from({ length: Math.max(7, ...entries.map((entry) => entry.indexInDay || 0)) }, (_, index) => index + 1);
  const weekdays = [1, 2, 3, 4, 5];

  return (
    <section className="contentCard">
      <div className="contentHeader">
        <div>
          <div className="title">
            {mode === "class" ? `Orar pe clasa${selectedClassName ? ` - ${selectedClassName}` : ""}` : "Orarul meu"}
          </div>
          <div className="subtitle">
            {isEditing ? "Modul editare este activ." : "Vizualizare orar pe zile si intervale."}
          </div>
        </div>

        <div className="headerActions">
          {mode === "class" && (
            <div className="row">
              <label className="label">Clasa</label>
              <input
                className="input"
                placeholder="Cauta (ex: IX A)"
                value={classSearch}
                onChange={(event) => setClassSearch(event.target.value)}
                disabled={loading || isEditing}
                style={{ width: 180 }}
              />
              <select
                className="select"
                value={selectedClassId}
                onChange={(event) => setSelectedClassId(Number(event.target.value))}
                disabled={loading || isEditing}
              >
                {filteredClasses.map((schoolClass) => (
                  <option key={schoolClass.id} value={schoolClass.id}>
                    {schoolClass.name ?? schoolClass.class_name ?? `Clasa ${schoolClass.id}`}
                  </option>
                ))}
              </select>
            </div>
          )}

          {editingAllowed && !isEditing && (
            <button className="btn primary" onClick={beginEdit} disabled={loading || entries.length === 0 || needsRefresh}>
              Edit
            </button>
          )}

          {editingAllowed && isEditing && (
            <>
              <button className="btn" onClick={cancelEdit} disabled={loading}>Cancel</button>
              <button className="btn primary" onClick={saveAll} disabled={loading}>Save</button>
            </>
          )}

          {editingAllowed && !isEditing && mode === "class" && selectedClassId && (
            <button
              className="btn danger"
              onClick={async () => {
                const confirmed = window.confirm(`Esti sigur ca vrei sa stergi orarul pentru ${selectedClassName || `clasa ${selectedClassId}`}?`);
                if (!confirmed) return;
                setLoading(true);
                setBanner(null);
                try {
                  await apiDelete(`/timetables/classes/${selectedClassId}`, accessToken);
                  await loadTimetableForClass(selectedClassId);
                  setBanner({ type: "ok", text: "Orarul a fost sters." });
                } catch (error) {
                  setBanner({ type: "error", text: String(error?.message || error) });
                } finally {
                  setLoading(false);
                }
              }}
              disabled={loading || entries.length === 0}
            >
              Delete Timetable
            </button>
          )}
        </div>
      </div>

      {banner && (
        <div className={`banner ${banner.type}`}>
          <div style={{ display: "flex", justifyContent: "space-between", gap: 12, alignItems: "center" }}>
            <div>{banner.text}</div>
            {needsRefresh && <button className="btn" onClick={refreshNow} disabled={loading}>Refresh</button>}
          </div>
        </div>
      )}

      {loading ? (
        <div className="mutedBlock">Loading...</div>
      ) : entries.length === 0 ? (
        <div className="mutedBlock">Nu exista intrari de orar.</div>
      ) : mode === "my" && roles.includes("professor") ? (
        <div className="tableWrap">
          <table className="dataTable">
            <thead>
              <tr>
                <th>Zi</th>
                <th>Ora</th>
                <th>Materie</th>
                <th>Clasa</th>
                <th>Sala</th>
              </tr>
            </thead>
            <tbody>
              {entries.map((entry) => (
                <tr key={entry.id}>
                  <td>{WEEKDAY[(entry.weekday || 1) - 1] || "-"}</td>
                  <td>{TIME_LABELS[entry.indexInDay || 0] || `Slot ${entry.indexInDay}`}</td>
                  <td>{entry.subjectName || `#${entry.subjectId}`}</td>
                  <td>{entry.className || `Clasa ${entry.classId}`}</td>
                  <td>{entry.roomName || (entry.roomId ? `Sala ${entry.roomId}` : "-")}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : (
        <div className="tableWrap">
          <table className="tbl tblGrid">
            <thead>
              <tr>
                <th className="stickyLeft">Orar</th>
                <th>Luni</th>
                <th>Marti</th>
                <th>Miercuri</th>
                <th>Joi</th>
                <th>Vineri</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((slot) => (
                <tr key={slot}>
                  <td className="stickyLeft timeCell">
                    <div className="timeBig">{TIME_LABELS[slot] ?? `Slot ${slot}`}</div>
                    <div className="timeSmall">({slot})</div>
                  </td>
                  {weekdays.map((weekday) => {
                    const cell = byKey.get(`${weekday}-${slot}`);
                    if (!cell) {
                      return <td key={`${weekday}-${slot}`} className="cellEmpty">-</td>;
                    }

                    return (
                      <td key={cell.id} className="cell">
                        {!isEditing ? (
                          <>
                            <div className="cellTitle">{cell.subjectName ?? `#${cell.subjectId}`}</div>
                            <div className="cellMeta">
                              {cell.roomName ?? (cell.roomId == null ? "-" : `Sala ${cell.roomId}`)}
                              {cell.teacherName && <div className="cellTeacher">{cell.teacherName}</div>}
                              <span className="cellVersion">v{cell.version}</span>
                            </div>
                          </>
                        ) : (
                          <div className="cellEdit">
                            <select
                              className="select small"
                              value={cell.subjectId}
                              onChange={(event) => {
                                const subjectId = Number(event.target.value);
                                const subject = subjectsById.get(subjectId);
                                updateEntryLocal(cell.id, {
                                  subjectId,
                                  subjectName: subject?.name ?? cell.subjectName,
                                });
                              }}
                            >
                              {subjects.map((subject) => (
                                <option key={subject.id} value={subject.id}>{subject.name ?? `Materie ${subject.id}`}</option>
                              ))}
                            </select>

                            <select
                              className="select small"
                              value={cell.roomId ?? ""}
                              onChange={(event) => {
                                const roomId = event.target.value === "" ? null : Number(event.target.value);
                                const room = roomId ? roomsById.get(roomId) : null;
                                updateEntryLocal(cell.id, {
                                  roomId,
                                  roomName: room?.name ?? null,
                                });
                              }}
                            >
                              <option value="">- No room -</option>
                              {rooms.map((room) => (
                                <option key={room.id} value={room.id}>{room.name} (Capacitate: {room.capacity})</option>
                              ))}
                            </select>
                            <div className="cellVersion">v{cell.version}</div>
                          </div>
                        )}
                      </td>
                    );
                  })}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}





