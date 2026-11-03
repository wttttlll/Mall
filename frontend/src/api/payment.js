import http from './http'

export const paymentApi = {
  pay(orderNo) {
    return http.post(`/payments/orders/${orderNo}/pay`)
  }
}
