// AI-assisted shared UI state with Codex.
import { reactive } from 'vue'
import { request, session } from './api'
export const shop = reactive({ user: null, ready: false, authOpen: false, cartQuantity: 0, toast: '', toastKind: 'success', route: '/', pendingProduct: null })
let toastTimeout
export function notify(text, kind = 'success') {
  clearTimeout(toastTimeout); shop.toast = text; shop.toastKind = kind
  toastTimeout = setTimeout(() => { shop.toast = '' }, 4500)
}
export async function initialize() {
  try { shop.user = await session(); if (shop.user) await refreshCart() }
  catch { /* Catalog can still be viewed; mutations show actionable errors. */ }
  finally { shop.ready = true }
}
export async function refreshCart() {
  if (!shop.user) { shop.cartQuantity = 0; return }
  const cart = await request('/cart'); shop.cartQuantity = cart.quantity; return cart
}
export async function addToCart(productId, quantity = 1) {
  if (!shop.user) { shop.pendingProduct = { productId, quantity }; shop.authOpen = true; return false }
  try {
    const cart = await request('/cart', { method: 'POST', body: { productId, quantity } })
    shop.cartQuantity = cart.quantity; notify('已放进购物袋，喜欢的小物替你留好了。'); return true
  } catch (e) {
    if (e.status === 401) { shop.user = null; shop.authOpen = true }
    notify(e.message, 'error'); return false
  }
}
export function navigate(path) { window.location.hash = path; shop.route = path; window.scrollTo({ top: 0, behavior: 'smooth' }) }
export const money = value => Number(value || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
export const statuses = { PENDING_PAYMENT: '待付款', PAID: '待发货', SHIPPED: '已发货', COMPLETED: '已完成', CANCELLED: '已取消' }
export const time = value => value ? value.replace('T', ' ').slice(0, 16) : '—'
