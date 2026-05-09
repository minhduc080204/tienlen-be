# NFT Payment System Integration Guide

Tài liệu này hướng dẫn cách tích hợp và kiểm thử hệ thống thanh toán NFT cho Frontend.

## 1. Thông tin Network (Polygon Amoy)
- **Network Name:** Polygon Amoy Testnet
- **RPC URL:** `https://rpc-amoy.polygon.technology`
- **Chain ID:** `80002`
- **Currency Symbol:** `MATIC`
- **Block Explorer:** `https://amoy.polyscan.com/`

## 2. Thông tin Smart Contract (NFT)
- **Contract Address:** `0x0000000000000000000000000000000000000000` (Cập nhật sau khi deploy chính thức)
- **Standard:** ERC-1155 (hoặc tùy theo implementation thực tế)

## 3. Quy trình thanh toán & Verify
1. **B1: Người dùng chọn mua NFT**
   - FE gọi API `GET /api/v1/nfts/` để lấy danh sách NFT đang bán.
2. **B2: Người dùng thực hiện thanh toán trên Blockchain**
   - FE sử dụng `ethers.js` hoặc `wagmi` để gọi hàm `mint` hoặc `purchase` trên Smart Contract.
   - Sau khi giao dịch thành công, FE sẽ nhận được `transactionHash`.
3. **B3: Verify giao dịch với Backend**
   - FE gửi `transactionHash`, `itemId`, và `walletAddress` lên Backend để xác thực.
   - Backend sẽ kiểm tra Transaction trên Polygon Scan. Nếu hợp lệ, Backend sẽ lưu quyền sở hữu NFT cho người dùng.

### API Verify
- **Endpoint:** `POST /api/v1/nfts/verify`
- **Headers:** `Authorization: Bearer <token>`
- **Request Body:**
```json
{
  "txHash": "0x...",
  "itemId": 1,
  "walletAddress": "0x..."
}
```
- **Response:** `200 OK` (thành công) hoặc `400 Bad Request` (thất bại).

## 4. Danh sách Seed Data (Sample)
Dưới đây là các Item ID đã được seed sẵn trong database:
- **ID 1:** Classic Blue Back (Price: 0.1 MATIC)
- **ID 2:** Golden Dragon Back (Price: 0.5 MATIC)
- **ID 3:** Cyberpunk Frame (Price: 0.2 MATIC)

## 5. Hướng dẫn Test
1. Sử dụng ví Metamask kết nối mạng **Polygon Amoy**.
2. Nhận MATIC test từ [Polygon Faucet](https://faucet.polygon.technology/).
3. Thực hiện giao dịch (hoặc giả lập giao dịch nếu chưa có contract thực tế).
4. Gọi API `/verify` để đồng bộ dữ liệu.
