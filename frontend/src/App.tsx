import { useEffect } from 'react'
import { BrowserRouter, Navigate, Route, Routes, useLocation } from 'react-router-dom'
import { MotionConfig } from 'motion/react'
import { ShopProvider } from './state'
import CustomerLayout from './components/CustomerLayout'
import Landing from './landing/Landing'
import Login from './pages/Login'
import Shop from './pages/Shop'
import OrderPage from './pages/OrderPage'
import Orders from './pages/Orders'
import OpsConsole from './ops/OpsConsole'

function ScrollToTop() {
  const { pathname, hash } = useLocation()
  useEffect(() => { if (!hash) window.scrollTo(0, 0) }, [pathname, hash])
  return null
}

export default function App() {
  return (
    <MotionConfig reducedMotion="user">
      <ShopProvider>
        <BrowserRouter>
          <ScrollToTop />
          <Routes>
            <Route path="/" element={<Landing />} />
            <Route path="/login" element={<Login />} />
            <Route path="/ops" element={<OpsConsole />} />
            <Route path="/ops/orders/:id" element={<div className="px-5 py-8"><OrderPage ops /></div>} />
            <Route path="/shop" element={<CustomerLayout />}>
              <Route index element={<Shop />} />
              <Route path="orders" element={<Orders />} />
              <Route path="orders/:id" element={<OrderPage />} />
            </Route>
            {/* Old customer URLs */}
            <Route path="/orders/*" element={<Navigate to="/shop/orders" replace />} />
            <Route path="*" element={<Navigate to="/" replace />} />
          </Routes>
        </BrowserRouter>
      </ShopProvider>
    </MotionConfig>
  )
}
