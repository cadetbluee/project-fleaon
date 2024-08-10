package com.ssafy.fleaOn.web.service;

import com.ssafy.fleaOn.web.domain.*;
import com.ssafy.fleaOn.web.dto.PurchaseCancleResponse;
import com.ssafy.fleaOn.web.dto.PurchaseRequest;
import com.ssafy.fleaOn.web.dto.TradeRequest;
import com.ssafy.fleaOn.web.repository.*;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class PurchaseService {

    private static final Logger logger = LoggerFactory.getLogger(PurchaseService.class);

    private final ProductRepository productRepository;
    private final LiveRepository liveRepository;
    private final ReservationRepository reservationRepository;
    private final TradeRepository tradeRepository;
    private final ChattingRepository chattingRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final UserRepository userRepository;
    private final ShortsRepository shortsRepository;
    private final ChattingListRepository chattingListRepository;
    private final TradeService tradeService;

    @Autowired
    public PurchaseService(ProductRepository productRepository, LiveRepository liveRepository,
                           ReservationRepository reservationRepository, TradeRepository tradeRepository,
                           ChattingRepository chattingRepository, RedisTemplate<String, Object> redisTemplate, UserRepository userRepository, ShortsRepository shortsRepository, ChattingListRepository chattingListRepository, TradeService tradeService) {
        this.productRepository = productRepository;
        this.liveRepository = liveRepository;
        this.reservationRepository = reservationRepository;
        this.tradeRepository = tradeRepository;
        this.chattingRepository = chattingRepository;
        this.redisTemplate = redisTemplate;
        this.userRepository = userRepository;
        this.shortsRepository = shortsRepository;
        this.chattingListRepository = chattingListRepository;
        this.tradeService = tradeService;
    }

    @Transactional
    public int processPurchaseRequest(PurchaseRequest request) {
        logger.info("Processing purchase request for productId: {} and userId: {}", request.getProductId(), request.getUserId());
        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new IllegalArgumentException("Invalid product ID"));

        // Check if the user is already the buyer
        if (product.getCurrentBuyerId() == request.getUserId()) {
            logger.warn("User {} is already the buyer for product {}", request.getUserId(), request.getProductId());
            return -2; // Already the buyer
        }

        // Check if the user is already in the reservation list
        Optional<Reservation> existingReservation = reservationRepository.findByProduct_ProductIdAndUser_UserId(request.getProductId(), request.getUserId());
        if (existingReservation.isPresent()) {
            logger.warn("User {} is already reserved for product {}", request.getUserId(), request.getProductId());
            return -3; // Already reserved
        }

        if (product.getCurrentBuyerId() == 0) {
            product.setCurrentBuyerId(request.getUserId());
            productRepository.save(product);
            return 0; // 구매 예정자
        } else if (product.getReservationCount() < 5) {
            Reservation reservation = Reservation.builder()
                    .product(product)
                    .user(User.builder().userId(request.getUserId()).build())
                    .build();
            reservationRepository.save(reservation);
            product.setReservationCount(product.getReservationCount() + 1);
            productRepository.save(product);
            return product.getReservationCount(); // 예약자 순번 반환
        } else {
            return 6; // 예약 불가능
        }
    }

    @Transactional
    public PurchaseCancleResponse cancelPurchaseProduct(PurchaseRequest request) {
        boolean chatExit = true;
        int next = 0;
        boolean isBuyer = false;

        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new IllegalArgumentException("Invalid product ID"));

        if (request.getUserId() == product.getCurrentBuyerId()) {
            isBuyer = true;
            product.setCurrentBuyerId(0);
            productRepository.save(product);

            Optional<Reservation> nextReservation = reservationRepository.findFirstByProduct_ProductIdOrderByReservationTimeAsc(request.getProductId());
            if (nextReservation.isPresent()) {
                product.setCurrentBuyerId(nextReservation.get().getUser().getUserId());
                reservationRepository.delete(nextReservation.get());
                product.setReservationCount(product.getReservationCount() - 1);
                productRepository.save(product);
                next = product.getCurrentBuyerId();
            }

            int cid = tradeRepository.findByProduct_productId(request.getProductId()).orElseThrow(() -> new IllegalArgumentException("Invalid product ID")).getChatting().getChattingId();
            logger.info("chatting id: {}", cid);
            // 현재 구매자가 없는 경우 거래 취소
            tradeRepository.deleteByProduct_ProductId(request.getProductId());

            // 해당 채팅방의 모든 거래 내역 확인
            List<Trade> remainingTrades = tradeRepository.findByChatting_ChattingId(cid).orElse(new ArrayList<>());

            if (remainingTrades.isEmpty()) {
                // 채팅 내역 삭제
                logger.info("Deleting chatting list for chatting ID: {}", cid);
                chattingListRepository.deleteByChatting_ChattingId(cid);

                // 채팅방 삭제
                logger.info("Deleting chatting room for chatting ID: {}", cid);
                chattingRepository.deleteById(cid);
                chatExit = false; // 모든 거래가 취소되어 채팅방 삭제됨
            } else {
                logger.info("Remaining trades exist, not deleting chat.");
            }
        } else {
            logger.info("User is not the current buyer, skipping chat deletion.");
        }
        logger.info("Cancel purchase result: chatExit={}, next={}, isBuyer={}", chatExit, next, isBuyer);
        return new PurchaseCancleResponse(product.getProductId(), chatExit, next, isBuyer);
    }


    // 거래 파기 메서드
    @Transactional
    public List<PurchaseCancleResponse> breakTrade(int chatId, int userId) {
        Chatting chatting = chattingRepository.findById(chatId).orElseThrow(() -> new IllegalArgumentException("Invalid chat ID"));
        int liveId = chatting.getLive().getLiveId();
        System.out.println("라이브 아이디: "+liveId);
        List<PurchaseCancleResponse> cancelResponses = new ArrayList<>();

        // 1. 해당 라이브 ID로 모든 거래를 조회
        List<Trade> trades = tradeRepository.findByLive_liveIdAndBuyerId(liveId, userId).orElseThrow(() -> new IllegalArgumentException("Invalid live ID or user ID"));
        for (Trade trade : trades) {
            Product product = trade.getProduct();
            int currentBuyerId = product.getCurrentBuyerId();

            // 2. 구매 취소를 처리
            PurchaseRequest cancelRequest = new PurchaseRequest(product.getProductId(), currentBuyerId);
            PurchaseCancleResponse cancelResponse = cancelPurchaseProduct(cancelRequest);
            cancelResponses.add(cancelResponse);

            // 3. 해당 제품의 예약 리스트 삭제
            reservationRepository.deleteByProduct_ProductId(product.getProductId());

            // 4. 제품의 구매자 및 예약자 초기화
            product.setCurrentBuyerId(0);
            product.setReservationCount(0);
            productRepository.save(product);

            System.out.println("size: "+cancelResponses.size());
        }

        // 5. 채팅방 및 채팅 메시지 삭제
        logger.info("Deleting all chatting lists and chat rooms for live ID: {}", liveId);
        chattingListRepository.deleteByChatting_ChattingId(liveId);
        chattingRepository.deleteById(liveId);

        return cancelResponses;
    }

    @Transactional
    public int processReservationRequest(PurchaseRequest request) {
        logger.info("Processing reservation request for productId: {} and userId: {}", request.getProductId(), request.getUserId());
        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new IllegalArgumentException("Invalid product ID"));

        // Check if the user is already the buyer
        if (product.getCurrentBuyerId() == request.getUserId()) {
            logger.warn("User {} is already the buyer for product {}", request.getUserId(), request.getProductId());
            return -2; // Already the buyer
        }

        // Check if the user is already in the reservation list
        Optional<Reservation> existingReservation = reservationRepository.findByProduct_ProductIdAndUser_UserId(request.getProductId(), request.getUserId());
        if (existingReservation.isPresent()) {
            logger.warn("User {} is already reserved for product {}", request.getUserId(), request.getProductId());
            return -3; // Already reserved
        }

        if (product.getReservationCount() < 5) {
            Reservation reservation = Reservation.builder()
                    .product(product)
                    .user(User.builder().userId(request.getUserId()).build())
                    .build();
            reservationRepository.save(reservation);
            product.setReservationCount(product.getReservationCount() + 1);
            productRepository.save(product);
            return product.getReservationCount(); // 그 다음 예약자 순번 반환
        }
        return 6; // 예약 불가능
    }

    @Transactional
    public int processCancelReservationRequest(PurchaseRequest request) {
        logger.info("Processing cancel reservation request for productId: {} and userId: {}", request.getProductId(), request.getUserId());
        Optional<Reservation> reservation = reservationRepository.findByProduct_ProductIdAndUser_UserId(request.getProductId(), request.getUserId());
        if (reservation.isPresent()) {
            reservationRepository.delete(reservation.get());
            Product product = productRepository.findById(request.getProductId())
                    .orElseThrow(() -> new IllegalArgumentException("Invalid product ID"));
            product.setReservationCount(product.getReservationCount() - 1);
            productRepository.save(product);
            return product.getReservationCount();
        }
        return -1; // 예약이 없는 경우
    }

    @Transactional
    public void processConfirmPurchaseRequest(TradeRequest request) {
        logger.info("Processing confirm purchase request for productId: {} and buyerId: {}", request.getProductId(), request.getBuyerId());
        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new IllegalArgumentException("Invalid product ID"));
        product.setCurrentBuyerId(request.getBuyerId());

        Live live = liveRepository.findById(request.getLiveId())
                .orElseThrow(() -> new IllegalArgumentException("Invalid live ID"));

        Shorts shorts = shortsRepository.findByProduct_ProductId(request.getProductId())
                .orElseThrow(() -> new IllegalArgumentException("Invalid product ID"));

        // Find or create chatting
        Optional<Chatting> optionalChatting = chattingRepository.findByBuyer_UserIdAndLive_LiveId(request.getBuyerId(), request.getLiveId());
        Chatting chatting;
        if (optionalChatting.isPresent()) {
            chatting = optionalChatting.get();
        } else {
            User buyer = userRepository.findById(request.getBuyerId())
                    .orElseThrow(() -> new IllegalArgumentException("Invalid buyer ID"));
            User seller = userRepository.findById(request.getSellerId())
                    .orElseThrow(() -> new IllegalArgumentException("Invalid seller ID"));
            chatting = Chatting.builder()
                    .live(live)
                    .buyer(buyer)
                    .seller(seller)
                    .build();
            chatting = chattingRepository.save(chatting);
        }
        productRepository.save(product);

        if (request.getBuyerId() == product.getCurrentBuyerId()) {
            Trade trade = request.toEntity(live, product, chatting, shorts);
            tradeRepository.save(trade);
            productRepository.save(product);

            // 구매 확정 결과를 Redis에 설정
            redisTemplate.opsForValue().set("confirmResult:" + request.getBuyerId() + ":" + request.getProductId(), "confirmed");
        } else {
            // 구매자와 현재 구매자가 일치하지 않는 경우 결과를 Redis에 설정
            redisTemplate.opsForValue().set("confirmResult:" + request.getBuyerId() + ":" + request.getProductId(), "not confirmed");
        }
    }

    @Transactional
    public void processConfirmTradeRequest(TradeRequest request) {
        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new IllegalArgumentException("Invalid product ID"));

        if (request.getBuyerId() == product.getCurrentBuyerId()) {
            // 구매 확정 처리 및 관련 데이터 삭제
            tradeService.confirmTrade(request);

            // 구매 확정 결과를 Redis에 설정
            redisTemplate.opsForValue().set("confirmResult:" + request.getBuyerId() + ":" + request.getProductId(), "confirmed");
        } else {
            // 구매자와 현재 구매자가 일치하지 않는 경우 결과를 Redis에 설정
            redisTemplate.opsForValue().set("confirmResult:" + request.getBuyerId() + ":" + request.getProductId(), "not confirmed");
        }
    }
}