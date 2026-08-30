import { createRoot } from 'react-dom/client'
import './index.css'
import App from './App.tsx'
import InviteAuthCallback from './InviteAuthCallback.tsx'
import InviteHandoff from './InviteHandoff.tsx'

const pathname = window.location.pathname.replace(/\/+$/, '') || '/'

const rootComponent =
  pathname === '/invite'
    ? <InviteHandoff />
    : pathname === '/auth/invite'
      ? <InviteAuthCallback />
      : <App />

createRoot(document.getElementById('root')!).render(
  rootComponent,
)
