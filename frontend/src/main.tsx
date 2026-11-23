import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import '@fontsource-variable/atkinson-hyperlegible-next'
import './estilo/tokens.css'
import './index.css'
import App from './App.tsx'
import { Acesso } from './Acesso.tsx'
import { PaginaConvite } from './Convite.tsx'

// O link do e-mail de convite abre /convite, que o nginx serve com este mesmo index.html.
const convite = window.location.pathname === '/convite'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    {convite ? (
      <PaginaConvite />
    ) : (
      <Acesso>
        <App />
      </Acesso>
    )}
  </StrictMode>,
)
