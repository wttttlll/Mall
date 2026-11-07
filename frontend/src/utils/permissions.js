const ADMIN_PERMISSION_PREFIXES = ['product:', 'inventory:', 'order:', 'aftersale:']

export function hasPermission(permissions, code) {
  return Array.isArray(permissions) && permissions.includes(code)
}

export function canAccessAdmin(permissions) {
  if (!Array.isArray(permissions)) return false
  return permissions.some((code) =>
    ADMIN_PERMISSION_PREFIXES.some((prefix) => code.startsWith(prefix))
  )
}

export function adminHomePath(permissions) {
  if (hasPermission(permissions, 'product:spu:read')) return '/admin/products'
  if (hasPermission(permissions, 'order:info:read')) return '/admin/orders'
  if (hasPermission(permissions, 'aftersale:order:read')) return '/admin/after-sales'
  if (hasPermission(permissions, 'inventory:stock:read')) return '/admin/inventory'
  if (hasPermission(permissions, 'product:category:read')) return '/admin/categories'
  return '/products'
}
