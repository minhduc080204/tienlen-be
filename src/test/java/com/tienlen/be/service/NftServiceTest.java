package com.tienlen.be.service;

import com.tienlen.be.dto.request.NftVerifyRequest;
import com.tienlen.be.entity.UserNft;
import com.tienlen.be.exception.BadRequestException;
import com.tienlen.be.exception.ConflictException;
import com.tienlen.be.repository.UserNftRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.Request;
import org.web3j.protocol.core.methods.response.EthGetTransactionReceipt;
import org.web3j.protocol.core.methods.response.Log;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NftServiceTest {

    @Mock
    private UserNftRepository userNftRepository;

    @Mock
    private Web3j web3j;

    @InjectMocks
    private NftService nftService;

    private static final String CONTRACT_ADDRESS = "0x1234567890123456789012345678901234567890";
    private static final String WALLET_ADDRESS = "0x0987654321098765432109876543210987654321";
    private static final String TX_HASH = "0x1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef";
    private static final Long ITEM_ID = 1L;
    private static final Long USER_ID = 100L;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(nftService, "contractAddress", CONTRACT_ADDRESS);
    }

    @Test
    void verifyAndSaveNft_Success() throws IOException {
        NftVerifyRequest request = createRequest();
        when(userNftRepository.existsByTxHash(TX_HASH)).thenReturn(false);

        TransactionReceipt receipt = new TransactionReceipt();
        receipt.setStatus("0x1");
        receipt.setTo(CONTRACT_ADDRESS);
        
        Log logEntry = new Log();
        logEntry.setTopics(List.of(
                "0xc3d58168c5592394b588934485304b46c6f71d184762c2f6d50060934891122a",
                "0xOperator",
                "0xFrom",
                "0x000000000000000000000000" + WALLET_ADDRESS.substring(2)
        ));
        // data: id=1, value=1 (both uint256)
        logEntry.setData("0x0000000000000000000000000000000000000000000000000000000000000001" +
                         "0000000000000000000000000000000000000000000000000000000000000001");
        receipt.setLogs(List.of(logEntry));

        mockWeb3jResponse(receipt);

        nftService.verifyAndSaveNft(request, USER_ID);

        verify(userNftRepository, times(1)).save(any(UserNft.class));
    }

    @Test
    void verifyAndSaveNft_ConflictException_AlreadyVerified() {
        NftVerifyRequest request = createRequest();
        when(userNftRepository.existsByTxHash(TX_HASH)).thenReturn(true);

        assertThrows(ConflictException.class, () -> nftService.verifyAndSaveNft(request, USER_ID));
        verify(userNftRepository, never()).save(any());
    }

    @Test
    void verifyAndSaveNft_BadRequestException_ReceiptNotFound() throws IOException {
        NftVerifyRequest request = createRequest();
        when(userNftRepository.existsByTxHash(TX_HASH)).thenReturn(false);

        mockWeb3jResponse(null);

        BadRequestException ex = assertThrows(BadRequestException.class, () -> nftService.verifyAndSaveNft(request, USER_ID));
        assertEquals("Transaction receipt not found", ex.getMessage());
    }

    @Test
    void verifyAndSaveNft_BadRequestException_TransactionFailed() throws IOException {
        NftVerifyRequest request = createRequest();
        when(userNftRepository.existsByTxHash(TX_HASH)).thenReturn(false);

        TransactionReceipt receipt = new TransactionReceipt();
        receipt.setStatus("0x0");
        mockWeb3jResponse(receipt);

        BadRequestException ex = assertThrows(BadRequestException.class, () -> nftService.verifyAndSaveNft(request, USER_ID));
        assertEquals("Transaction failed on blockchain", ex.getMessage());
    }

    @Test
    void verifyAndSaveNft_BadRequestException_WrongContract() throws IOException {
        NftVerifyRequest request = createRequest();
        when(userNftRepository.existsByTxHash(TX_HASH)).thenReturn(false);

        TransactionReceipt receipt = new TransactionReceipt();
        receipt.setStatus("0x1");
        receipt.setTo("0xWrongContract");
        mockWeb3jResponse(receipt);

        BadRequestException ex = assertThrows(BadRequestException.class, () -> nftService.verifyAndSaveNft(request, USER_ID));
        assertEquals("Transaction was not sent to the NFT contract", ex.getMessage());
    }

    @Test
    void verifyAndSaveNft_BadRequestException_LogNotVerified() throws IOException {
        NftVerifyRequest request = createRequest();
        when(userNftRepository.existsByTxHash(TX_HASH)).thenReturn(false);

        TransactionReceipt receipt = new TransactionReceipt();
        receipt.setStatus("0x1");
        receipt.setTo(CONTRACT_ADDRESS);
        receipt.setLogs(Collections.emptyList());
        mockWeb3jResponse(receipt);

        BadRequestException ex = assertThrows(BadRequestException.class, () -> nftService.verifyAndSaveNft(request, USER_ID));
        assertEquals("Could not verify NFT transfer in transaction logs", ex.getMessage());
    }

    private NftVerifyRequest createRequest() {
        NftVerifyRequest request = new NftVerifyRequest();
        request.setTxHash(TX_HASH);
        request.setItemId(ITEM_ID);
        request.setWalletAddress(WALLET_ADDRESS);
        return request;
    }

    private void mockWeb3jResponse(TransactionReceipt receipt) throws IOException {
        EthGetTransactionReceipt response = mock(EthGetTransactionReceipt.class);
        when(response.getTransactionReceipt()).thenReturn(Optional.ofNullable(receipt));

        Request<?, EthGetTransactionReceipt> request = mock(Request.class);
        when(request.send()).thenReturn(response);

        doReturn(request).when(web3j).ethGetTransactionReceipt(anyString());
    }
}
