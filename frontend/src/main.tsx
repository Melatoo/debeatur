import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import '@fontsource-variable/atkinson-hyperlegible-next'
import './estilo/tokens.css'
import './index.css'
import App from './App.tsx'
import { Acesso } from './Acesso.tsx'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <Acesso>
      <App />
    </Acesso>
  </StrictMode>,
)
