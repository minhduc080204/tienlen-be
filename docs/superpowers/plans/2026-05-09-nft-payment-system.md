# NFT Payment & Digital Reward System Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Triển khai hệ thống xác thực thanh toán NFT trên mạng Polygon để người dùng sở hữu các vật phẩm kỹ thuật số trong game.

**Architecture:** Sử dụng phương tiếp cận "Verify": Frontend thực hiện giao dịch mint trên Blockchain, sau đó gửi Transaction Hash về Backend. Backend sử dụng thư viện Web3j để xác thực giao dịch on-chain, kiểm tra tính hợp lệ (contract address, event, amount, receiver) trước khi cập nhật quyền sở hữu vào database.

**Tech Stack:** Spring Boot, JPA, MySQL, Web3j, Polygon (Amoy Testnet).

---

### Task 1: Thêm Dependency và Cấu hình

**Files:**
- Modify: `pom.xml`
- Modify: `src/main/resources/application.properties`

- [ ] **Step 1: Thêm Web3j dependency vào pom.xml**

```xml
<dependency>
    <groupId>org.web3j</groupId>
    <artifactId>core</artifactId>
    <version>4.10.0</version>
</dependency>
```

- [ ] **Step 2: Cấu hình RPC và Contract Address**

Thêm vào `src/main/resources/application.properties`:
```properties
blockchain.polygon.rpc-url=https://rpc-amoy.polygon.technology
blockchain.nft-contract-address=0x0000000000000000000000000000000000000000
```
(Địa chỉ contract sẽ được cập nhật sau khi deploy).

- [ ] **Step 3: Commit**

```bash
git add pom.xml src/main/resources/application.properties
git commit -m "chore: add web3j dependency and blockchain config"
```

---

### Task 2: Tạo Entities và Repositories

**Files:**
- Create: `src/main/java/com/tienlen/be/entity/NftItem.java`
- Create: `src/main/java/com/tienlen/be/entity/UserNft.java`
- Create: `src/main/java/com/tienlen/be/repository/NftItemRepository.java`
- Create: `src/main/java/com/tienlen/be/repository/UserNftRepository.java`

- [ ] **Step 1: Tạo NftItem Entity**

```java
package com.tienlen.be.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity
@Table(name = "nft_items")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class NftItem {
    @Id
    private Long id; // Khớp với ID trong Smart Contract
    private String name;
    private String description;
    private BigDecimal priceMatic;
    private String imageUrl;
    private boolean active;
}
```

- [ ] **Step 2: Tạo UserNft Entity**

```java
package com.tienlen.be.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_nfts")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class UserNft {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long userId;
    private Long nftItemId;
    private String walletAddress;
    @Column(unique = true)
    private String txHash;
    private LocalDateTime createdAt;
}
```

- [ ] **Step 3: Tạo Repositories**

(Tạo `NftItemRepository` và `UserNftRepository` mở rộng `JpaRepository`).

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/tienlen/be/entity/ src/main/java/com/tienlen/be/repository/
git commit -m "feat: add NftItem and UserNft entities and repositories"
```

---

### Task 3: Triển khai NftService (Logic Xác thực)

**Files:**
- Create: `src/main/java/com/tienlen/be/dto/request/NftVerifyRequest.java`
- Create: `src/main/java/com/tienlen/be/service/NftService.java`

- [ ] **Step 1: Tạo DTO Request**

```java
package com.tienlen.be.dto.request;

import lombok.Data;

@Data
public class NftVerifyRequest {
    private String txHash;
    private Long itemId;
    private String walletAddress;
}
```

- [ ] **Step 2: Triển khai logic Verify trong NftService**

Sử dụng `Web3j` để lấy `TransactionReceipt` và kiểm tra logs của Event `TransferSingle`.
Lưu ý: Event ERC-1155 `TransferSingle` có signature: `TransferSingle(address,address,address,uint256,uint256)`.

- [ ] **Step 3: Viết Unit Test cho NftService**

Mock `Web3j` và các Repository để kiểm tra các trường hợp: tx hợp lệ, tx sai contract, tx đã tồn tại.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/tienlen/be/dto/request/NftVerifyRequest.java src/main/java/com/tienlen/be/service/NftService.java
git commit -m "feat: implement NftService for on-chain verification"
```

---

### Task 4: Tạo NftController và API Endpoints

**Files:**
- Create: `src/main/java/com/tienlen/be/controller/NftController.java`

- [ ] **Step 1: Tạo NftController**

Expose các API:
- `POST /api/v1/nfts/verify`: Xác thực mua.
- `GET /api/v1/nfts`: Lấy danh sách item đang bán.
- `GET /api/v1/nfts/my`: Lấy danh sách item user đã sở hữu.

- [ ] **Step 2: Tích hợp Security**

Đảm bảo các endpoint được bảo vệ bởi JWT và lấy được `userId` từ context.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/tienlen/be/controller/NftController.java
git commit -m "feat: add NftController with verify and list endpoints"
```

---

### Task 5: Seed Data và Kiểm thử tích hợp

- [ ] **Step 1: Tạo script SQL seed data cho NftItem**
- [ ] **Step 2: Chạy ứng dụng và dùng Postman/Curl để test API verify (sử dụng một txHash giả lập hoặc thực tế trên Amoy)**
- [ ] **Step 3: Hoàn tất tài liệu hướng dẫn cho FE**
- [ ] **Step 4: Commit và kết thúc**
