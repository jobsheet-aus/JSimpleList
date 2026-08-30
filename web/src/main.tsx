import { createRoot } from 'react-dom/client'
import './index.css'
import App from './App.tsx'
import InviteHandoff from './InviteHandoff.tsx'

const pathname = window.location.pathname.replace(/\/+$/, '') || '/'

const rootComponent =
  pathname === '/invite'
    ? <InviteHandoff />
    : <App />

createRoot(document.getElementById('root')!).render(
  rootComponent,
)
