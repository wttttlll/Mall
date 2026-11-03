const currencyFormatter = new Intl.NumberFormat('zh-CN', {
  style: 'currency',
  currency: 'CNY',
  minimumFractionDigits: 2
})

export function formatMoney(value) {
  const number = Number(value ?? 0)
  return Number.isFinite(number) ? currencyFormatter.format(number) : '¥0.00'
}

export function formatDateTime(value) {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return String(value)
  return date.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
}

export function formatPriceRange(min, max) {
  if (min == null && max == null) return '价格待定'
  if (min != null && max != null && String(min) !== String(max)) {
    return `${formatMoney(min)} - ${formatMoney(max)}`
  }
  return formatMoney(min ?? max)
}

export function orderStatusText(status) {
  return {
    PENDING_PAYMENT: '待支付',
    PAID: '已支付',
    CANCELLED: '已取消'
  }[status] || status || '-'
}

export function orderStatusClass(status) {
  return {
    PENDING_PAYMENT: 'status-pending',
    PAID: 'status-paid',
    CANCELLED: 'status-cancelled'
  }[status] || 'status-default'
}
