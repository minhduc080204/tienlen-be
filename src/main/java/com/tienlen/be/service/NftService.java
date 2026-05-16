package com.tienlen.be.service;

import com.tienlen.be.dto.request.NftVerifyRequest;
import com.tienlen.be.entity.NftItem;
import com.tienlen.be.entity.UserNft;
import com.tienlen.be.exception.BadRequestException;
import com.tienlen.be.exception.ConflictException;
import com.tienlen.be.repository.NftItemRepository;
import com.tienlen.be.repository.UserNftRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.response.EthGetTransactionReceipt;
import org.web3j.protocol.core.methods.response.EthTransaction;
import org.web3j.protocol.core.methods.response.Log;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.utils.Convert;
import org.web3j.utils.Numeric;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class NftService {

    private static final String TRANSFER_SINGLE_SIGNATURE = "0xc3d58168c5592394b588934485304b46c6f71d184762c2f6d50060934891122a";

    private final UserNftRepository userNftRepository;
    private final NftItemRepository nftItemRepository;
    private final Web3j web3j;

    @Value("${blockchain.nft-contract-address}")
    private String contractAddress;

    @Value("${blockchain.admin-wallet-address}")
    private String adminWalletAddress;

    public void verifyAndSaveNft(NftVerifyRequest request, Long userId) throws IOException {
        log.info("Verifying NFT purchase: txHash={}, itemId={}, userId={}",
                request.getTxHash(), request.getItemId(), userId);

        if (userNftRepository.existsByTxHash(request.getTxHash())) {
            throw new ConflictException("Transaction has already been verified");
        }

        EthGetTransactionReceipt receiptResponse = web3j.ethGetTransactionReceipt(request.getTxHash()).send();
        Optional<TransactionReceipt> receiptOpt = receiptResponse.getTransactionReceipt();

        if (receiptOpt.isEmpty()) {
            throw new BadRequestException("Transaction receipt not found");
        }

        TransactionReceipt receipt = receiptOpt.get();

        if (!"0x1".equals(receipt.getStatus())) {
            throw new BadRequestException("Transaction failed on blockchain");
        }

        if (receipt.getTo() == null || !receipt.getTo().equalsIgnoreCase(contractAddress)) {
            throw new BadRequestException("Transaction was not sent to the NFT contract");
        }

        boolean verified = false;

        for (Log logEntry : receipt.getLogs()) {
            List<String> topics = logEntry.getTopics();
            if (topics != null && !topics.isEmpty() && topics.get(0).equalsIgnoreCase(TRANSFER_SINGLE_SIGNATURE)) {
                if (topics.size() < 4)
                    continue;

                // topics[3] is 'to' address (indexed)
                String toAddressTopic = topics.get(3);
                String extractedToAddress = "0x" + toAddressTopic.substring(toAddressTopic.length() - 40);

                if (extractedToAddress.equalsIgnoreCase(request.getWalletAddress())) {
                    // data contains id and value (both uint256)
                    String data = logEntry.getData();
                    if (data != null && data.length() >= 130) { // 0x + 64 (id) + 64 (value)
                        String idHex = data.substring(2, 66);
                        BigInteger id = Numeric.toBigInt(idHex);

                        if (id.longValue() == request.getItemId()) {
                            verified = true;
                            break;
                        }
                    }
                }
            }
        }

        if (!verified) {
            throw new BadRequestException("Could not verify NFT transfer in transaction logs");
        }

        UserNft userNft = UserNft.builder()
                .userId(userId)
                .nftItemId(request.getItemId())
                .walletAddress(request.getWalletAddress())
                .txHash(request.getTxHash())
                .createdAt(LocalDateTime.now())
                .build();

        userNftRepository.save(userNft);
        log.info("NFT purchase verified and saved successfully for user {}", userId);
    }

    /**
     * Verify giao dịch chuyển MATIC từ ví user sang ví admin,
     * sau đó unlock item cho user bằng cách lưu vào bảng user_nfts.
     */
    public void verifyTransferAndUnlockItem(NftVerifyRequest request, Long userId) throws IOException {
        log.info("Verifying MATIC transfer: txHash={}, itemId={}, walletAddress={}, userId={}",
                request.getTxHash(), request.getItemId(), request.getWalletAddress(), userId);

        // 1. Kiểm tra giao dịch đã được xử lý chưa (tránh duplicate)
        if (userNftRepository.existsByTxHash(request.getTxHash())) {
            throw new ConflictException("Transaction has already been processed");
        }

        // 2. Lấy item để biết giá cần trả
        NftItem item = nftItemRepository.findById(request.getItemId())
                .orElseThrow(() -> new BadRequestException("Item not found: " + request.getItemId()));

        if (!item.isActive()) {
            throw new BadRequestException("Item is not available for purchase");
        }

        // 3. Lấy transaction receipt để kiểm tra trạng thái
        EthGetTransactionReceipt receiptResponse = web3j.ethGetTransactionReceipt(request.getTxHash()).send();
        Optional<TransactionReceipt> receiptOpt = receiptResponse.getTransactionReceipt();

        if (receiptOpt.isEmpty()) {
            throw new BadRequestException("Transaction receipt not found — transaction may still be pending");
        }

        TransactionReceipt receipt = receiptOpt.get();

        // 4. Kiểm tra giao dịch thành công trên blockchain
        if (!"0x1".equals(receipt.getStatus())) {
            throw new BadRequestException("Transaction failed on blockchain");
        }

        // 5. Kiểm tra địa chỉ gửi (from) khớp với walletAddress của user
        if (receipt.getFrom() == null || !receipt.getFrom().equalsIgnoreCase(request.getWalletAddress())) {
            throw new BadRequestException("Transaction sender does not match the provided wallet address");
        }

        // 6. Kiểm tra địa chỉ nhận (to) là ví admin
        if (receipt.getTo() == null || !receipt.getTo().equalsIgnoreCase(adminWalletAddress)) {
            throw new BadRequestException("Transaction recipient is not the admin wallet");
        }

        // 7. Lấy giá trị MATIC gửi từ transaction object (receipt không chứa value)
        EthTransaction ethTx = web3j.ethGetTransactionByHash(request.getTxHash()).send();
        org.web3j.protocol.core.methods.response.Transaction tx = ethTx.getTransaction()
                .orElseThrow(() -> new BadRequestException("Transaction not found on blockchain"));

        // 8. Kiểm tra value gửi đi đủ để mua item (so sánh theo Wei)
        BigDecimal itemPriceWei = Convert.toWei(item.getPriceMatic(), Convert.Unit.ETHER);
        BigInteger txValueWei = tx.getValue();

        if (txValueWei.compareTo(itemPriceWei.toBigInteger()) < 0) {
            throw new BadRequestException(
                    String.format("Insufficient payment: expected %s MATIC but received %s MATIC",
                            item.getPriceMatic(),
                            Convert.fromWei(txValueWei.toString(), Convert.Unit.ETHER)));
        }

        // 9. Tất cả kiểm tra đã qua — unlock item cho user
        UserNft userNft = UserNft.builder()
                .userId(userId)
                .nftItemId(request.getItemId())
                .walletAddress(request.getWalletAddress())
                .txHash(request.getTxHash())
                .createdAt(LocalDateTime.now())
                .build();

        userNftRepository.save(userNft);
        log.info("MATIC transfer verified — item {} unlocked for user {}", request.getItemId(), userId);
    }
}
