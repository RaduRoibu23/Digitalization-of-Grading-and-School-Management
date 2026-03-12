import React, { useEffect, useMemo, useState } from 'react'
import { apiGet } from '../services/apiService'

const PAGE_SIZE = 15

function buildStudentName(student) {
  const lastName = student.last_name || student.lastName || ''
  const firstName = student.first_name || student.firstName || ''
  return `${lastName} ${firstName}`.trim() || 'Fara nume'
}

function buildInitials(name) {
  return name
    .split(' ')
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase() || '')
    .join('')
}

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
        return buildStudentName(a).localeCompare(buildStudentName(b))
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
  const pageStart = (currentPage - 1) * PAGE_SIZE
  const paginatedStudents = filteredStudents.slice(pageStart, pageStart + PAGE_SIZE)

  return (
    <section className="contentCard">
      <div className="contentHeader">
        <div>
          <div className="title">Lista studenti</div>
          <div className="subtitle">Vizualizare clara a elevilor din sistem, cu cautare si paginare.</div>
        </div>
        <div className="headerActions studentHeaderActions">
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
            Sorteaza dupa nume
          </button>
          <button
            className={`btn ${sortBy === 'class_name' ? 'primary' : ''}`}
            onClick={() => setSortBy(sortBy === 'class_name' ? null : 'class_name')}
            disabled={loading || students.length === 0}
          >
            Sorteaza dupa clasa
          </button>
        </div>
      </div>

      <div className="catalogStats studentStats">
        <div className="statPill">Total elevi: <strong>{students.length}</strong></div>
        <div className="statPill">Rezultate filtrate: <strong>{filteredStudents.length}</strong></div>
        <div className="statPill">Pagina curenta: <strong>{currentPage}</strong></div>
      </div>

      {banner && <div className={`banner ${banner.type}`}>{banner.text}</div>}

      {loading ? (
        <div className="mutedBlock">Se incarca lista de studenti...</div>
      ) : filteredStudents.length === 0 ? (
        <div className="mutedBlock">Nu exista studenti pentru filtrul selectat.</div>
      ) : (
        <>
          <div className="tableWrap studentTableWrap">
            <table className="tbl studentTable">
              <thead>
                <tr>
                  <th className="thin">#</th>
                  <th>Elev</th>
                  <th>Username</th>
                  <th>Clasa</th>
                </tr>
              </thead>
              <tbody>
                {paginatedStudents.map((student, index) => {
                  const fullName = buildStudentName(student)
                  const className = student.class_name || student.className || (student.class_id ? `Clasa ${student.class_id}` : '-')
                  return (
                    <tr key={student.id || student.username}>
                      <td className="thin">{pageStart + index + 1}</td>
                      <td>
                        <div className="studentNameCell">
                          <div className="studentInitials">{buildInitials(fullName)}</div>
                          <div>
                            <div className="studentName">{fullName}</div>
                            <div className="studentMeta">Cont activ in platforma</div>
                          </div>
                        </div>
                      </td>
                      <td>
                        <span className="studentUsername">@{student.username || '-'}</span>
                      </td>
                      <td>
                        <span className="studentClassBadge">{className}</span>
                      </td>
                    </tr>
                  )
                })}
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
