import React, { useEffect, useMemo, useState } from 'react'
import { apiGet } from '../services/apiService'

const PAGE_SIZE = 15

export default function StudentsScreen({ accessToken }) {
  const [loading, setLoading] = useState(false)
  const [banner, setBanner] = useState(null)
  const [students, setStudents] = useState([])
  const [sortBy, setSortBy] = useState(null)
  const [search, setSearch] = useState('')
  const [page, setPage] = useState(1)

  useEffect(() => {
    ;(async () => {
      setLoading(true)
      setBanner(null)
      try {
        const data = await apiGet('/profiles?role=student', accessToken)
        setStudents(Array.isArray(data) ? data : [])
      } catch (error) {
        setBanner({ type: 'error', text: String(error?.message || error) })
      } finally {
        setLoading(false)
      }
    })()
  }, [accessToken])

  useEffect(() => {
    setPage(1)
  }, [search, sortBy])

  const filteredStudents = useMemo(() => {
    const query = search.trim().toLowerCase()
    let list = students
    if (query) {
      list = list.filter((student) => {
        const username = student.username || ''
        const lastName = student.last_name || student.lastName || ''
        const firstName = student.first_name || student.firstName || ''
        const className = student.class_name || student.className || ''
        return `${username} ${lastName} ${firstName} ${className}`.toLowerCase().includes(query)
      })
    }

    if (!sortBy) {
      return list
    }

    const sorted = [...list]
    sorted.sort((a, b) => {
      if (sortBy === 'last_name') {
        return (a.last_name || a.lastName || '').localeCompare(b.last_name || b.lastName || '')
      }
      if (sortBy === 'class_name') {
        return (a.class_name || a.className || '').localeCompare(b.class_name || b.className || '')
      }
      return 0
    })
    return sorted
  }, [search, sortBy, students])

  const totalPages = Math.max(1, Math.ceil(filteredStudents.length / PAGE_SIZE))
  const currentPage = Math.min(page, totalPages)
  const paginatedStudents = filteredStudents.slice((currentPage - 1) * PAGE_SIZE, currentPage * PAGE_SIZE)

  return (
    <section className="contentCard">
      <div className="contentHeader">
        <div>
          <div className="title">Lista Studenti</div>
          <div className="subtitle">Toti studentii din sistem.</div>
        </div>
        <div className="headerActions">
          <input
            className="input"
            placeholder="Cauta dupa nume, username sau clasa"
            value={search}
            onChange={(event) => setSearch(event.target.value)}
          />
          <button
            className={`btn ${sortBy === 'last_name' ? 'primary' : ''}`}
            onClick={() => setSortBy(sortBy === 'last_name' ? null : 'last_name')}
            disabled={loading || students.length === 0}
          >
            Sort by Name
          </button>
          <button
            className={`btn ${sortBy === 'class_name' ? 'primary' : ''}`}
            onClick={() => setSortBy(sortBy === 'class_name' ? null : 'class_name')}
            disabled={loading || students.length === 0}
          >
            Sort by Class
          </button>
        </div>
      </div>

      {banner && <div className={`banner ${banner.type}`}>{banner.text}</div>}

      {loading ? (
        <div className="mutedBlock">Loading...</div>
      ) : filteredStudents.length === 0 ? (
        <div className="mutedBlock">Nu exista studenti pentru filtrul selectat.</div>
      ) : (
        <>
          <div className="tableWrap">
            <table className="dataTable">
              <thead>
                <tr>
                  <th>Username</th>
                  <th>Nume</th>
                  <th>Prenume</th>
                  <th>Clasa</th>
                </tr>
              </thead>
              <tbody>
                {paginatedStudents.map((student) => (
                  <tr key={student.id || student.username}>
                    <td>{student.username || '-'}</td>
                    <td>{student.last_name || student.lastName || '-'}</td>
                    <td>{student.first_name || student.firstName || '-'}</td>
                    <td>{student.class_name || student.className || (student.class_id ? `Clasa ${student.class_id}` : '-')}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          <div className="paginationBar">
            <div className="mutedSmall">Pagina {currentPage} din {totalPages}</div>
            <div className="paginationActions">
              <button className="btn" onClick={() => setPage(1)} disabled={currentPage === 1}>Prima</button>
              <button className="btn" onClick={() => setPage((value) => Math.max(1, value - 1))} disabled={currentPage === 1}>Inapoi</button>
              <button className="btn" onClick={() => setPage((value) => Math.min(totalPages, value + 1))} disabled={currentPage === totalPages}>Inainte</button>
              <button className="btn" onClick={() => setPage(totalPages)} disabled={currentPage === totalPages}>Ultima</button>
            </div>
          </div>
        </>
      )}
    </section>
  )
}
