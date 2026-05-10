package com.tienlen.be.service;

import com.tienlen.be.dto.request.NftVerifyRequest;
import com.tienlen.be.entity.UserNft;
import com.tienlen.be.exception.BadRequestException;
import com.tienlen.be.exception.ConflictException;
import com.tienlen.be.repository.UserNftRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.response.EthGetTransactionReceipt;
import org.web3j.protocol.core.methods.response.Log;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.utils.Numeric;

import java.io.IOException;
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
    private final Web3j web3j;

    @Value("${blockchain.nft-contract-address}")
    private String contractAddress;

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
                if (topics.size() < 4) continue;

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
}
