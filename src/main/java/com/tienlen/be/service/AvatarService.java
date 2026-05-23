package com.tienlen.be.service;

import com.tienlen.be.dto.request.AvatarVerifyRequest;
import com.tienlen.be.dto.response.AvatarItemResponse;
import com.tienlen.be.entity.AvatarItem;
import com.tienlen.be.entity.User;
import com.tienlen.be.entity.UserAvatar;
import com.tienlen.be.entity.Transaction;
import com.tienlen.be.exception.BadRequestException;
import com.tienlen.be.exception.ConflictException;
import com.tienlen.be.repository.AvatarItemRepository;
import com.tienlen.be.repository.UserAvatarRepository;
import com.tienlen.be.repository.UserRepository;
import com.tienlen.be.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
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
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AvatarService {

    private static final Set<String> FREE_STYLES = Set.of("adventurer", "adventurer-neutral", "big-ears");
    private static final Set<String> PAID_STYLES = Set.of("avataaars", "bottts", "thumbs");

    private final AvatarItemRepository avatarItemRepository;
    private final UserAvatarRepository userAvatarRepository;
    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;
    private final Web3j web3j;

    @Value("${blockchain.admin-wallet-address:0x0000000000000000000000000000000000000000}")
    private String adminWalletAddress;

    /**
     * Lấy danh sách các hình ảnh avatar có sẵn cho user, đánh dấu xem đã sở hữu chưa.
     */
    public List<AvatarItemResponse> getAvatarsForUser(Long userId) {
        log.info("Getting avatars for user id: {}", userId);
        List<AvatarItem> allItems = avatarItemRepository.findByActiveTrue();
        
        // Lấy danh sách các bộ style avatar mà user đã mua
        Set<String> ownedStyles = userAvatarRepository.findByUserId(userId).stream()
                .map(UserAvatar::getStyle)
                .map(String::toLowerCase)
                .collect(Collectors.toSet());

        return allItems.stream().map(item -> {
            String style = item.getStyle().toLowerCase();
            boolean isFree = FREE_STYLES.contains(style) || 
                             (item.getPriceTokens() != null && item.getPriceTokens() == 0) ||
                             (item.getPriceMatic() != null && item.getPriceMatic().compareTo(BigDecimal.ZERO) == 0);
            
            boolean isOwned = isFree || ownedStyles.contains(style);

            return AvatarItemResponse.builder()
                    .id(item.getId())
                    .name(item.getName())
                    .srcUrl(item.getSrcUrl())
                    .priceMatic(item.getPriceMatic())
                    .priceTokens(item.getPriceTokens())
                    .style(item.getStyle())
                    .owned(isOwned)
                    .active(item.isActive())
                    .build();
        }).collect(Collectors.toList());
    }

    /**
     * Mua avatar bằng token trong game (tokenBalance).
     */
    public void buyAvatarWithTokens(Long userId, Long avatarItemId) {
        log.info("User {} is buying avatar {} with tokens", userId, avatarItemId);

        AvatarItem item = avatarItemRepository.findById(avatarItemId)
                .orElseThrow(() -> new BadRequestException("Không tìm thấy avatar có ID: " + avatarItemId));

        if (!item.isActive()) {
            throw new BadRequestException("Avatar này không khả dụng để mua");
        }

        String style = item.getStyle().toLowerCase();

        // Kiểm tra xem đã sở hữu chưa
        boolean isFree = FREE_STYLES.contains(style) || 
                         (item.getPriceTokens() != null && item.getPriceTokens() == 0);
        
        if (isFree || userAvatarRepository.existsByUserIdAndStyle(userId, style)) {
            throw new ConflictException("Bạn đã sở hữu bộ avatar \"" + item.getStyle() + "\" này rồi");
        }

        long price = item.getPriceTokens() != null ? item.getPriceTokens() : 0L;
        if (price < 0) {
            throw new BadRequestException("Avatar này không thể mua bằng token");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BadRequestException("Không tìm thấy người dùng"));

        if (user.getTokenBalance() < price) {
            throw new BadRequestException("Số dư token không đủ để thực hiện giao dịch này");
        }

        // Trừ token balance
        user.setTokenBalance(user.getTokenBalance() - price);
        userRepository.save(user);

        // Lưu transaction
        Transaction transaction = Transaction.builder()
                .userId(userId)
                .amount(-price)
                .type("AVATAR_PURCHASE")
                .description("Mua bộ avatar: " + item.getStyle())
                .createdAt(LocalDateTime.now())
                .status("SUCCESS")
                .build();
        transactionRepository.save(transaction);

        // Mở khóa avatar cho user
        UserAvatar userAvatar = UserAvatar.builder()
                .userId(userId)
                .style(style)
                .createdAt(LocalDateTime.now())
                .build();
        userAvatarRepository.save(userAvatar);

        log.info("User {} successfully purchased avatar style {} using tokens", userId, style);
    }

    /**
     * Xác thực giao dịch MATIC trên Blockchain và mở khóa avatar cho user (tương tự skin lá bài).
     */
    public void verifyBlockchainTransferAndUnlockAvatar(Long userId, AvatarVerifyRequest request) throws IOException {
        log.info("Verifying MATIC transfer for avatar: txHash={}, itemId={}, walletAddress={}, userId={}",
                request.getTxHash(), request.getItemId(), request.getWalletAddress(), userId);

        // 1. Kiểm tra transaction đã được xử lý chưa
        if (userAvatarRepository.existsByTxHash(request.getTxHash())) {
            throw new ConflictException("Giao dịch blockchain này đã được xác thực trước đó");
        }

        // 2. Lấy avatar để biết giá MATIC
        AvatarItem item = avatarItemRepository.findById(request.getItemId())
                .orElseThrow(() -> new BadRequestException("Không tìm thấy avatar có ID: " + request.getItemId()));

        if (!item.isActive()) {
            throw new BadRequestException("Avatar này hiện không khả dụng để mua");
        }

        String style = item.getStyle().toLowerCase();

        // 3. Lấy receipt từ blockchain
        EthGetTransactionReceipt receiptResponse = web3j.ethGetTransactionReceipt(request.getTxHash()).send();
        Optional<TransactionReceipt> receiptOpt = receiptResponse.getTransactionReceipt();

        if (receiptOpt.isEmpty()) {
            throw new BadRequestException("Không tìm thấy biên lai giao dịch trên Blockchain — có thể giao dịch chưa hoàn tất");
        }

        TransactionReceipt receipt = receiptOpt.get();

        // 4. Kiểm tra trạng thái giao dịch
        if (!"0x1".equals(receipt.getStatus())) {
            throw new BadRequestException("Giao dịch trên Blockchain bị thất bại");
        }

        // 5. Kiểm tra người gửi
        if (receipt.getFrom() == null || !receipt.getFrom().equalsIgnoreCase(request.getWalletAddress())) {
            throw new BadRequestException("Người gửi trên Blockchain không khớp với ví đã nhập");
        }

        // 6. Kiểm tra người nhận có phải admin wallet
        if (receipt.getTo() == null || !receipt.getTo().equalsIgnoreCase(adminWalletAddress)) {
            throw new BadRequestException("Địa chỉ nhận trên Blockchain không khớp với ví Admin");
        }

        // 7. Lấy transaction để so sánh giá trị gửi đi
        EthTransaction ethTx = web3j.ethGetTransactionByHash(request.getTxHash()).send();
        org.web3j.protocol.core.methods.response.Transaction tx = ethTx.getTransaction()
                .orElseThrow(() -> new BadRequestException("Không tìm thấy thông tin chi tiết của giao dịch trên Blockchain"));

        BigDecimal itemPriceWei = Convert.toWei(item.getPriceMatic() != null ? item.getPriceMatic() : BigDecimal.ZERO, Convert.Unit.ETHER);
        BigInteger txValueWei = tx.getValue();

        if (txValueWei.compareTo(itemPriceWei.toBigInteger()) < 0) {
            throw new BadRequestException(
                    String.format("Số tiền thanh toán thiếu: Yêu cầu %s MATIC nhưng nhận được %s MATIC",
                            item.getPriceMatic(),
                            Convert.fromWei(txValueWei.toString(), Convert.Unit.ETHER)));
        }

        // 8. Lưu mở khóa avatar cho user
        UserAvatar userAvatar = UserAvatar.builder()
                .userId(userId)
                .style(style)
                .walletAddress(request.getWalletAddress())
                .txHash(request.getTxHash())
                .createdAt(LocalDateTime.now())
                .build();
        userAvatarRepository.save(userAvatar);

        // 9. Lưu log transaction cục bộ
        Transaction transaction = Transaction.builder()
                .userId(userId)
                .amount(0L) // Giao dịch thực hiện trực tiếp trên blockchain nên balance trong game không thay đổi
                .type("AVATAR_PURCHASE_MATIC")
                .description("Mua bộ avatar bằng MATIC: " + item.getStyle())
                .txHash(request.getTxHash())
                .walletAddress(request.getWalletAddress())
                .createdAt(LocalDateTime.now())
                .status("SUCCESS")
                .build();
        transactionRepository.save(transaction);

        log.info("Blockchain MATIC transfer verified successfully — avatar style {} unlocked for user {}", style, userId);
    }

    /**
     * User chọn avatar từ danh sách đã mở khóa để cài đặt làm avatar chính thức.
     */
    public void selectAvatar(Long userId, Long avatarItemId) {
        log.info("User {} is selecting avatar {}", userId, avatarItemId);

        AvatarItem item = avatarItemRepository.findById(avatarItemId)
                .orElseThrow(() -> new BadRequestException("Không tìm thấy avatar có ID: " + avatarItemId));

        String style = item.getStyle().toLowerCase();

        // Kiểm tra xem user có sở hữu avatar này không
        boolean isFree = FREE_STYLES.contains(style) || 
                         (item.getPriceTokens() != null && item.getPriceTokens() == 0) ||
                         (item.getPriceMatic() != null && item.getPriceMatic().compareTo(BigDecimal.ZERO) == 0);

        if (!isFree && !userAvatarRepository.existsByUserIdAndStyle(userId, style)) {
            throw new BadRequestException("Bạn chưa sở hữu/mở khóa bộ avatar \"" + item.getStyle() + "\" này. Hãy mua nó trước!");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BadRequestException("Không tìm thấy người dùng"));

        user.setAvatarUrl(item.getSrcUrl());
        userRepository.save(user);
        
        log.info("User {} selected avatar successfully. New avatarUrl: {}", userId, item.getSrcUrl());
    }

    /**
     * Cho phép user tự sinh và chọn hình ảnh avatar tùy biến bằng custom seed / URL.
     * Xác thực: nếu thuộc các bộ mất phí (avataaars, bottts, thumbs), user phải đã mở khóa bộ đó.
     */
    public void selectCustomAvatar(Long userId, String srcUrl) {
        log.info("User {} is setting custom avatar: {}", userId, srcUrl);

        if (srcUrl == null || srcUrl.trim().isEmpty()) {
            throw new BadRequestException("Đường dẫn ảnh avatar không hợp lệ");
        }

        String extractedStyle = extractStyleFromUrl(srcUrl);

        if (extractedStyle == null) {
            // Nếu không thể trích xuất style từ URL Dicebear, mặc định cho phép hoặc từ chối?
            // Để linh hoạt, nếu URL không chứa dicebear, ta có thể cho phép hoặc bắt buộc là DiceBear.
            // Theo yêu cầu: "Nguồn các hình ảnh lấy từ https://api.dicebear.com/9.x/xxxx.svg"
            throw new BadRequestException("Đường dẫn avatar phải xuất phát từ https://api.dicebear.com/9.x/");
        }

        String lowerStyle = extractedStyle.toLowerCase();

        if (FREE_STYLES.contains(lowerStyle)) {
            // Bộ miễn phí, cho phép cài đặt ngay lập tức
            updateUserAvatarUrl(userId, srcUrl);
            return;
        }

        if (PAID_STYLES.contains(lowerStyle)) {
            // Bộ mất phí, kiểm tra xem người dùng đã mua bộ này chưa
            boolean hasUnlockedStyle = userAvatarRepository.existsByUserIdAndStyle(userId, lowerStyle);

            if (!hasUnlockedStyle) {
                throw new BadRequestException("Bạn chưa mở khóa bộ thiết kế trả phí \"" + extractedStyle + "\". Hãy mua bộ này trong cửa hàng để mở khóa tính năng tự sinh!");
            }

            updateUserAvatarUrl(userId, srcUrl);
            return;
        }

        throw new BadRequestException("Bộ thiết kế \"" + extractedStyle + "\" không được hỗ trợ");
    }

    private void updateUserAvatarUrl(Long userId, String srcUrl) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BadRequestException("Không tìm thấy người dùng"));
        user.setAvatarUrl(srcUrl);
        userRepository.save(user);
        log.info("Updated custom avatar successfully for user {}", userId);
    }

    /**
     * Hỗ trợ trích xuất style của Dicebear từ URL.
     */
    public static String extractStyleFromUrl(String url) {
        if (url == null) return null;
        for (String style : List.of("adventurer-neutral", "adventurer", "big-ears", "avataaars", "bottts", "thumbs")) {
            if (url.contains("/" + style + "/") || url.contains("/" + style + "?") || url.endsWith("/" + style)) {
                return style;
            }
        }
        return null;
    }

    // ==========================================
    // ADMIN CRUD METHODS
    // ==========================================

    public List<com.tienlen.be.dto.response.admin.AdminAvatarDTO> getAllAvatarsForAdmin() {
        return avatarItemRepository.findAll().stream().map(item -> 
            com.tienlen.be.dto.response.admin.AdminAvatarDTO.builder()
                .id(item.getId())
                .name(item.getName())
                .srcUrl(item.getSrcUrl())
                .priceMatic(item.getPriceMatic() != null ? item.getPriceMatic().toString() : "0.0")
                .priceTokens(item.getPriceTokens() != null ? item.getPriceTokens() : 0L)
                .style(item.getStyle())
                .active(item.isActive())
                .createdAt(item.getCreatedAt())
                .build()
        ).collect(Collectors.toList());
    }

    public com.tienlen.be.dto.response.admin.AdminAvatarDTO addAvatarForAdmin(com.tienlen.be.dto.response.admin.AdminAvatarDTO dto) {
        String extractedStyle = extractStyleFromUrl(dto.getSrcUrl());
        if (extractedStyle == null) {
            throw new BadRequestException("Đường dẫn avatar phải xuất phát từ https://api.dicebear.com/9.x/");
        }

        String name = dto.getName();
        if (name == null || name.trim().isEmpty()) {
            name = extractedStyle.substring(0, 1).toUpperCase() + extractedStyle.substring(1) + " Design";
        }

        AvatarItem item = AvatarItem.builder()
                .name(name)
                .srcUrl(dto.getSrcUrl())
                .priceMatic(dto.getPriceMatic() != null ? new BigDecimal(dto.getPriceMatic()) : BigDecimal.ZERO)
                .priceTokens(dto.getPriceTokens() != null ? dto.getPriceTokens() : 0L)
                .style(extractedStyle.toLowerCase())
                .active(true)
                .createdAt(LocalDateTime.now())
                .build();

        avatarItemRepository.save(item);

        dto.setId(item.getId());
        dto.setName(item.getName());
        dto.setStyle(item.getStyle());
        dto.setActive(item.isActive());
        dto.setCreatedAt(item.getCreatedAt());
        return dto;
    }

    public com.tienlen.be.dto.response.admin.AdminAvatarDTO updateAvatarForAdmin(Long id, com.tienlen.be.dto.response.admin.AdminAvatarDTO dto) {
        AvatarItem item = avatarItemRepository.findById(id)
                .orElseThrow(() -> new BadRequestException("Không tìm thấy avatar có ID: " + id));

        if (dto.getSrcUrl() != null) {
            item.setSrcUrl(dto.getSrcUrl());
            String extractedStyle = extractStyleFromUrl(dto.getSrcUrl());
            if (extractedStyle != null) {
                item.setStyle(extractedStyle.toLowerCase());
            }
        }
        if (dto.getName() != null) {
            item.setName(dto.getName());
        }
        if (dto.getPriceMatic() != null) {
            item.setPriceMatic(new BigDecimal(dto.getPriceMatic()));
        }
        if (dto.getPriceTokens() != null) {
            item.setPriceTokens(dto.getPriceTokens());
        }
        item.setActive(dto.isActive());

        avatarItemRepository.save(item);

        dto.setId(item.getId());
        dto.setName(item.getName());
        dto.setStyle(item.getStyle());
        dto.setActive(item.isActive());
        dto.setCreatedAt(item.getCreatedAt());
        return dto;
    }

    public void deleteAvatarForAdmin(Long id) {
        if (!avatarItemRepository.existsById(id)) {
            throw new BadRequestException("Không tìm thấy avatar có ID: " + id);
        }
        avatarItemRepository.deleteById(id);
    }
}
