# Design Spec: NFT Payment & Digital Reward System

## 1. Tổng quan (Overview)
Hệ thống cho phép người dùng mua các vật phẩm kỹ thuật số độc quyền (mặt sau bài, khung avatar, v.v.) dưới dạng NFT trên mạng lưới Polygon. Hệ thống sử dụng phương pháp **Client-side Transaction & Backend Verification** để tối ưu hóa chi phí và trải nghiệm người dùng.

*   **Mạng lưới:** Polygon (Amoy Testnet cho Dev, Mainnet cho Prod).
*   **Tiêu chuẩn NFT:** ERC-1155 (Hỗ trợ nhiều loại vật phẩm trong 1 contract).
*   **Phương thức thanh toán:** MATIC (Trực tiếp on-chain).

## 2. Kiến trúc hệ thống (System Architecture)
1.  **Frontend (FE):** Kết nối ví, thực hiện giao dịch `mint` NFT.
2.  **Smart Contract (SC):** Xử lý việc nhận tiền và phát hành NFT.
3.  **Backend (BE):** Xác thực giao dịch on-chain qua Transaction Hash và cập nhật quyền sở hữu vào Database.

## 3. Thiết kế Dữ liệu (Data Modeling)

### 3.1. Entity mới: `NftItem`
Lưu danh mục các NFT có sẵn để bán.
*   `id`: Long (Primary Key, khớp với ID trong Smart Contract).
*   `name`: String.
*   `description`: String.
*   `priceMatic`: BigDecimal.
*   `imageUrl`: String.
*   `active`: Boolean.

### 3.2. Entity mới: `UserNft`
Lưu vết sở hữu của người dùng.
*   `id`: Long (PK).
*   `userId`: Long (FK to Users).
*   `nftItemId`: Long (FK to NftItems).
*   `walletAddress`: String (Địa chỉ ví đã mua).
*   `txHash`: String (Duy nhất - Unique để tránh Replay Attack).
*   `createdAt`: LocalDateTime.

## 4. API Design (Backend)

### 4.1. Verify NFT Purchase
Xác thực giao dịch sau khi người dùng đã thanh toán trên FE.

*   **Endpoint:** `POST /api/v1/nfts/verify`
*   **Request Body:**
    ```json
    {
      "txHash": "String",
      "itemId": "Long",
      "walletAddress": "String"
    }
    ```
*   **Logic xử lý (Pseudocode):**
    1. Kiểm tra `txHash` đã tồn tại trong bảng `UserNft` chưa? Nếu có -> Reject (Double spend).
    2. Sử dụng Web3j gọi RPC: `eth_getTransactionReceipt(txHash)`.
    3. Kiểm tra:
        - `status == "0x1"` (Success).
        - `to == NFT_CONTRACT_ADDRESS`.
        - `logs` chứa Event `TransferSingle(operator, from, to, id, value)`.
        - `to` trong Event khớp với `walletAddress`.
        - `id` trong Event khớp với `itemId`.
    4. Nếu OK: Lưu vào `UserNft`, đánh dấu User đã sở hữu item.

### 4.2. Get User Items
Lấy danh sách NFT user đang sở hữu.
*   **Endpoint:** `GET /api/v1/users/me/nfts`

## 5. Hướng dẫn Frontend (FE Implementation)

### 5.1. Tech stack đề xuất
*   `ethers.js` (v6) hoặc `viem`.
*   `@tanstack/react-query` để quản lý state.

### 5.2. Luồng thực hiện
1.  **Connect Wallet:** Yêu cầu user kết nối ví (Metamask/Coinbase).
2.  **Switch Network:** Đảm bảo user đang ở mạng Polygon (Chain ID: 137 hoặc 80002).
3.  **Call Contract:**
    ```javascript
    const contract = new ethers.Contract(CONTRACT_ADDRESS, ABI, signer);
    const tx = await contract.mint(itemId, 1, { 
        value: ethers.parseEther(priceInMatic) 
    });
    const receipt = await tx.wait(); // Đợi block xác nhận
    ```
4.  **Notify BE:** Gửi `receipt.hash` lên API `/verify`.

## 6. Smart Contract Interface (Solidity)
```solidity
interface ITienLenNFT {
    // Mint NFT mới bằng cách trả MATIC
    function mint(uint256 id, uint256 amount) external payable;
    
    // Rút tiền về ví admin (chỉ Owner)
    function withdraw() external;
}
```

## 7. Kế hoạch triển khai (Roadmap)
1.  **Phase 1:** Viết và Deploy Smart Contract lên Amoy Testnet.
2.  **Phase 2:** Cài đặt Web3j và viết logic Verify ở Backend.
3.  **Phase 3:** Tích hợp UI mua sắm và Connect Wallet ở Frontend.
4.  **Phase 4:** Kiểm thử toàn trình (E2E Test).
