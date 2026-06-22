package com.tienlen.be.service;

import com.tienlen.be.dto.request.TokenDepositVerifyRequest;
import com.tienlen.be.dto.response.UserResponse;
import com.tienlen.be.dto.response.admin.AdminTokenPackageDTO;
import com.tienlen.be.entity.TokenPackage;
import com.tienlen.be.entity.Transaction;
import com.tienlen.be.entity.User;
import com.tienlen.be.exception.BadRequestException;
import com.tienlen.be.exception.ConflictException;
import com.tienlen.be.repository.TokenPackageRepository;
import com.tienlen.be.repository.TransactionRepository;
import com.tienlen.be.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.response.EthGetTransactionReceipt;
import org.web3j.protocol.core.methods.response.EthTransaction;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.utils.Convert;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TokenDepositService {

    private final TokenPackageRepository tokenPackageRepository;
    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final Web3j web3j;

    @Value("${blockchain.admin-wallet-address:0x0000000000000000000000000000000000000000}")
    private String adminWalletAddress;

    // ==========================================
    // PUBLIC API METHODS
    // ==========================================

    /**
     * Trả danh sách gói nạp token đang active (dành cho user xem).
     */
    public List<AdminTokenPackageDTO> getActivePackages() {
        return tokenPackageRepository.findByActiveTrueOrderByPriceMaticAsc()
                .stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    /**
     * Xác thực giao dịch MATIC trên blockchain và cộng token vào tài khoản user.
     *
     * Luồng:
     *  1. Kiểm tra txHash chưa được xử lý (chống duplicate)
     *  2. Lấy gói nạp theo packageId, kiểm tra còn active không
     *  3. Lấy receipt từ blockchain, kiểm tra status == "0x1"
     *  4. Verify người gửi (from) == walletAddress của user
     *  5. Verify người nhận (to) == adminWalletAddress
     *  6. Lấy EthTransaction, so sánh value >= priceMatic (tính theo Wei)
     *  7. Cộng tokenAmount vào user.tokenBalance
     *  8. Lưu Transaction log (type = TOKEN_DEPOSIT)
     *  9. Trả về UserResponse với tokenBalance mới
     */
    @Transactional
    public UserResponse verifyAndDepositTokens(Long userId, TokenDepositVerifyRequest request) throws IOException {
        log.info("Verifying token deposit: txHash={}, packageId={}, walletAddress={}, userId={}",
                request.getTxHash(), request.getPackageId(), request.getWalletAddress(), userId);

        // 1. Kiểm tra txHash đã được dùng chưa
        if (transactionRepository.existsByTxHash(request.getTxHash())) {
            throw new ConflictException("Giao dịch blockchain này đã được xác thực trước đó");
        }

        // 2. Lấy gói nạp
        TokenPackage pkg = tokenPackageRepository.findById(request.getPackageId())
                .orElseThrow(() -> new BadRequestException("Không tìm thấy gói nạp có ID: " + request.getPackageId()));

        if (!pkg.isActive()) {
            throw new BadRequestException("Gói nạp này hiện không khả dụng");
        }

        // 3. Lấy receipt từ blockchain
        EthGetTransactionReceipt receiptResponse = web3j.ethGetTransactionReceipt(request.getTxHash()).send();
        Optional<TransactionReceipt> receiptOpt = receiptResponse.getTransactionReceipt();

        if (receiptOpt.isEmpty()) {
            throw new BadRequestException("Không tìm thấy biên lai giao dịch trên Blockchain — giao dịch có thể chưa hoàn tất");
        }

        TransactionReceipt receipt = receiptOpt.get();

        // 4. Kiểm tra trạng thái giao dịch
        if (!"0x1".equals(receipt.getStatus())) {
            throw new BadRequestException("Giao dịch trên Blockchain bị thất bại");
        }

        // 5. Kiểm tra người gửi khớp với ví của user
        if (receipt.getFrom() == null || !receipt.getFrom().equalsIgnoreCase(request.getWalletAddress())) {
            throw new BadRequestException("Người gửi trên Blockchain không khớp với địa chỉ ví đã cung cấp");
        }

        // 6. Kiểm tra người nhận là ví admin
        if (receipt.getTo() == null || !receipt.getTo().equalsIgnoreCase(adminWalletAddress)) {
            throw new BadRequestException("Địa chỉ nhận trên Blockchain không phải ví Admin");
        }

        // 7. Lấy EthTransaction để so sánh value gửi đi
        EthTransaction ethTx = web3j.ethGetTransactionByHash(request.getTxHash()).send();
        org.web3j.protocol.core.methods.response.Transaction tx = ethTx.getTransaction()
                .orElseThrow(() -> new BadRequestException("Không tìm thấy thông tin chi tiết giao dịch trên Blockchain"));

        BigDecimal requiredWei = Convert.toWei(pkg.getPriceMatic(), Convert.Unit.ETHER);
        BigInteger txValueWei = tx.getValue();

        if (txValueWei.compareTo(requiredWei.toBigInteger()) < 0) {
            throw new BadRequestException(String.format(
                    "Số tiền thanh toán không đủ: Yêu cầu %s MATIC nhưng chỉ nhận được %s MATIC",
                    pkg.getPriceMatic(),
                    Convert.fromWei(txValueWei.toString(), Convert.Unit.ETHER)));
        }

        // 8. Cộng token vào tài khoản user
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BadRequestException("Không tìm thấy người dùng"));

        long newBalance = user.getTokenBalance() + pkg.getTokenAmount();
        user.setTokenBalance(newBalance);
        userRepository.save(user);

        // 9. Lưu log giao dịch
        Transaction transaction = Transaction.builder()
                .userId(userId)
                .amount(pkg.getTokenAmount())
                .type("TOKEN_DEPOSIT")
                .description(String.format("Nạp token — Gói: %s (+%d token)", pkg.getName(), pkg.getTokenAmount()))
                .txHash(request.getTxHash())
                .walletAddress(request.getWalletAddress())
                .createdAt(LocalDateTime.now())
                .status("SUCCESS")
                .build();
        transactionRepository.save(transaction);

        log.info("Token deposit verified — user {} received {} tokens (new balance: {})",
                userId, pkg.getTokenAmount(), newBalance);

        return new UserResponse(user);
    }

    // ==========================================
    // ADMIN CRUD METHODS
    // ==========================================

    public List<AdminTokenPackageDTO> getAllPackages() {
        return tokenPackageRepository.findAll().stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    public AdminTokenPackageDTO createPackage(AdminTokenPackageDTO dto) {
        validatePackageDTO(dto);

        TokenPackage pkg = TokenPackage.builder()
                .name(dto.getName().trim())
                .description(dto.getDescription())
                .priceMatic(new BigDecimal(dto.getPriceMatic()))
                .tokenAmount(dto.getTokenAmount())
                .active(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        tokenPackageRepository.save(pkg);
        log.info("Admin created token package: id={}, name={}, price={} MATIC, tokens={}",
                pkg.getId(), pkg.getName(), pkg.getPriceMatic(), pkg.getTokenAmount());
        return mapToDTO(pkg);
    }

    public AdminTokenPackageDTO updatePackage(Long id, AdminTokenPackageDTO dto) {
        TokenPackage pkg = tokenPackageRepository.findById(id)
                .orElseThrow(() -> new BadRequestException("Không tìm thấy gói nạp có ID: " + id));

        if (dto.getName() != null && !dto.getName().trim().isEmpty()) {
            pkg.setName(dto.getName().trim());
        }
        if (dto.getDescription() != null) {
            pkg.setDescription(dto.getDescription());
        }
        if (dto.getPriceMatic() != null) {
            try {
                BigDecimal price = new BigDecimal(dto.getPriceMatic());
                if (price.compareTo(BigDecimal.ZERO) <= 0) {
                    throw new BadRequestException("Giá MATIC phải lớn hơn 0");
                }
                pkg.setPriceMatic(price);
            } catch (NumberFormatException e) {
                throw new BadRequestException("Giá MATIC không hợp lệ: " + dto.getPriceMatic());
            }
        }
        if (dto.getTokenAmount() != null) {
            if (dto.getTokenAmount() <= 0) {
                throw new BadRequestException("Số token phải lớn hơn 0");
            }
            pkg.setTokenAmount(dto.getTokenAmount());
        }
        pkg.setActive(dto.isActive());

        tokenPackageRepository.save(pkg);
        log.info("Admin updated token package id={}", id);
        return mapToDTO(pkg);
    }

    public void deletePackage(Long id) {
        if (!tokenPackageRepository.existsById(id)) {
            throw new BadRequestException("Không tìm thấy gói nạp có ID: " + id);
        }
        tokenPackageRepository.deleteById(id);
        log.info("Admin deleted token package id={}", id);
    }

    // ==========================================
    // PRIVATE HELPERS
    // ==========================================

    private AdminTokenPackageDTO mapToDTO(TokenPackage pkg) {
        return AdminTokenPackageDTO.builder()
                .id(pkg.getId())
                .name(pkg.getName())
                .description(pkg.getDescription())
                .priceMatic(pkg.getPriceMatic() != null ? pkg.getPriceMatic().stripTrailingZeros().toPlainString() : "0")
                .tokenAmount(pkg.getTokenAmount())
                .active(pkg.isActive())
                .createdAt(pkg.getCreatedAt())
                .updatedAt(pkg.getUpdatedAt())
                .build();
    }

    private void validatePackageDTO(AdminTokenPackageDTO dto) {
        if (dto.getName() == null || dto.getName().trim().isEmpty()) {
            throw new BadRequestException("Tên gói nạp không được để trống");
        }
        if (dto.getPriceMatic() == null) {
            throw new BadRequestException("Giá MATIC không được để trống");
        }
        try {
            BigDecimal price = new BigDecimal(dto.getPriceMatic());
            if (price.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BadRequestException("Giá MATIC phải lớn hơn 0");
            }
        } catch (NumberFormatException e) {
            throw new BadRequestException("Giá MATIC không hợp lệ: " + dto.getPriceMatic());
        }
        if (dto.getTokenAmount() == null || dto.getTokenAmount() <= 0) {
            throw new BadRequestException("Số token phải lớn hơn 0");
        }
    }
}
