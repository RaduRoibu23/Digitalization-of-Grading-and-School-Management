import React from 'react'
import ReactDOM from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import 'bootstrap/dist/css/bootstrap.min.css'
import App from './App'
import { ThemeProvider } from '../hooks/useTheme'
import '../styles/index.css'
import '../styles/tokens.css'
import '../styles/layout.css'
import '../styles/components.css'
import '../styles/pages.css'
import '../styles/animations.css'
import '../styles/dark-aurora.css'

ReactDOM.createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    <ThemeProvider>
      <BrowserRouter>
        <App />
      </BrowserRouter>
    </ThemeProvider>
  </React.StrictMode>
)
