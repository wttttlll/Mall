export function validateProductForm(form) {
  if (!form.name?.trim()) return '请填写商品名称'
  if (!form.categoryId) return '请选择商品分类'
  if (!form.mainImage?.trim()) return '请填写商品主图地址'
  if (form.mainImage.trim().length > 512) return '主图地址不能超过 512 个字符'
  if (form.imagesText) {
    const images = form.imagesText
      .split(/\r?\n|,/)
      .map((item) => item.trim())
      .filter(Boolean)
    if (images.length > 20) return '商品图片最多 20 张'
    if (images.some((item) => item.length > 512)) return '图片地址不能超过 512 个字符'
  }
  return ''
}

export function validateSkuForm(form, specs = []) {
  if (!form.skuCode?.trim()) return '请填写 SKU 编码'
  if (form.skuCode.trim().length > 64) return 'SKU 编码不能超过 64 个字符'
  if (form.price === '' || form.price == null || Number(form.price) <= 0) {
    return '请填写大于 0 的销售价'
  }
  if (!/^\d+(\.\d{1,2})?$/.test(String(form.price))) {
    return '销售价最多保留两位小数'
  }
  if (form.marketPrice && Number(form.marketPrice) < 0) {
    return '市场价不能为负数'
  }
  for (const spec of specs) {
    if (!form.selectedValueBySpec?.[spec.id]) {
      return `请为规格“${spec.name}”选择一个值`
    }
  }
  return ''
}
