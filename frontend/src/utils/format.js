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
    PAID: '待发货',
    SHIPPED: '待收货',
    COMPLETED: '已完成',
    CANCELLED: '已取消'
  }[status] || status || '-'
}

export function orderStatusClass(status) {
  return {
    PENDING_PAYMENT: 'status-pending',
    PAID: 'status-paid',
    SHIPPED: 'status-shipped',
    COMPLETED: 'status-completed',
    CANCELLED: 'status-cancelled'
  }[status] || 'status-default'
}

export function afterSaleStatusText(status) {
  return {
    APPLIED: '待审核',
    APPROVED: '已通过，待退货',
    REJECTED: '已拒绝',
    COMPLETED: '已完成'
  }[status] || status || '-'
}

export function afterSaleStatusClass(status) {
  return {
    APPLIED: 'status-pending',
    APPROVED: 'status-shipped',
    REJECTED: 'status-cancelled',
    COMPLETED: 'status-completed'
  }[status] || 'status-default'
}

export function afterSaleProcessStatusText(status) {
  return {
    SUCCESS: '已完成',
    PENDING: '待处理',
    NOT_STARTED: '未开始',
    NOT_APPLICABLE: '不适用'
  }[status] || status || '-'
}

export function productStatusText(status) {
  return {
    DRAFT: '草稿',
    ON_SHELF: '已上架',
    OFF_SHELF: '已下架'
  }[status] || status || '-'
}

export function productStatusClass(status) {
  return {
    DRAFT: 'status-draft',
    ON_SHELF: 'status-on-shelf',
    OFF_SHELF: 'status-off-shelf'
  }[status] || 'status-default'
}
