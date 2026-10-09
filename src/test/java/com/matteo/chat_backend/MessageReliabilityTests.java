package com.matteo.chat_backend;

import com.matteo.chat_backend.dto.MessageRequest;
import com.matteo.chat_backend.entity.DirectMessage;
import com.matteo.chat_backend.entity.User;
import com.matteo.chat_backend.repository.*;
import com.matteo.chat_backend.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {"spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.datasource.url=jdbc:h2:mem:message_reliability;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"})
@ActiveProfiles("test")
@Transactional
class MessageReliabilityTests {
    @Autowired MessageService service;
    @Autowired FriendshipService friends;
    @Autowired UserSafetyService safety;
    @Autowired UserRepository users;
    @Autowired DirectMessageRepository dms;
    @Autowired MessageRepository messages;
    @Autowired PlatformTransactionManager transactions;
    @Autowired MessageCryptoService crypto;
    @Autowired jakarta.persistence.EntityManager entityManager;
    @MockitoBean AttachmentStorageService storage;
    private User sender;
    private User receiver;
    private DirectMessage dm;

    @Test
    void blockingPreventsBothDirectionsAndRetriesButPreservesHistory() {
        var request = request(dm, UUID.randomUUID());
        var original = service.saveMessage(request, sender.getEmail());
        safety.block(receiver.getId(), sender.getEmail());
        safety.block(receiver.getId(), sender.getEmail());
        assertEquals(1, safety.blockedUsers(sender.getEmail()).size());
        assertTrue(safety.blockedUsers(receiver.getEmail()).isEmpty());
        assertFalse(safety.canMessage(sender.getId(), receiver.getId()));
        assertFalse(friends.getMyDms(sender.getEmail(), org.springframework.data.domain.Pageable.unpaged()).content().getFirst().isCanMessage());
        assertEquals(original.getId(), service.getMessage(original.getId(), receiver.getEmail()).getId());
        assertEquals(1, service.getDmMessages(dm.getId(), null, 50, sender.getEmail()).getMessages().size());
        assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> service.saveMessage(request, sender.getEmail()));
        assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> service.saveMessage(request(dm, null), receiver.getEmail()));
        assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> friends.sendRequest(sender.getId(), receiver.getEmail()));
        assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> friends.getOrCreateDm(receiver.getId(), sender.getEmail()));
        var typing = new com.matteo.chat_backend.dto.TypingRequest(); typing.setDmId(dm.getId()); typing.setTyping(true);
        assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> service.authorizeTyping(typing, receiver.getEmail()));
        assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> service.editMessage(original.getId(), "Bypass", sender.getEmail()));
    }

    @Test
    void blockedAttachmentsAreRejectedBeforeUploading() {
        safety.block(sender.getId(), receiver.getEmail());
        var file = new MockMultipartFile("file", "photo.png", "image/png", new byte[]{1, 2, 3});
        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> service.saveAttachmentMessage(request(dm, UUID.randomUUID()), file, sender.getEmail()));
        verifyNoInteractions(storage);
    }

    @Test
    void removingFriendPreservesDmAndAllowsNewRequestWhileUnblockingDoesNotRestoreFriendship() {
        var first = friends.sendRequest(receiver.getId(), sender.getEmail());
        friends.acceptRequest(first.getId(), receiver.getEmail());
        safety.removeFriend(receiver.getId(), sender.getEmail());
        assertTrue(friends.getFriends(sender.getEmail(), org.springframework.data.domain.Pageable.unpaged()).content().isEmpty());
        service.saveMessage(request(dm, UUID.randomUUID()), sender.getEmail());
        var next = friends.sendRequest(receiver.getId(), sender.getEmail());
        assertNotEquals(first.getId(), next.getId());
        safety.block(receiver.getId(), sender.getEmail());
        assertTrue(friends.getPendingRequests(receiver.getEmail(), org.springframework.data.domain.Pageable.unpaged()).content().isEmpty());
        safety.block(sender.getId(), receiver.getEmail());
        safety.unblock(receiver.getId(), sender.getEmail());
        assertFalse(safety.canMessage(sender.getId(), receiver.getId()));
        safety.unblock(sender.getId(), receiver.getEmail());
        assertTrue(safety.canMessage(sender.getId(), receiver.getId()));
        assertTrue(friends.getFriends(sender.getEmail(), org.springframework.data.domain.Pageable.unpaged()).content().isEmpty());
        assertNotNull(friends.sendRequest(sender.getId(), receiver.getEmail()));
    }

    @Test
    void attachmentMetadataRefreshRequiresConversationMembership() {
        var original = service.saveMessage(request(dm, UUID.randomUUID()), sender.getEmail());
        var stranger = user("stranger");
        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> service.getMessage(original.getId(), stranger.getEmail()));
        assertThrows(IllegalArgumentException.class, () -> safety.block(sender.getId(), sender.getEmail()));
    }

    @BeforeEach
    void seed() {
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            sender = user("sender");
            receiver = user("receiver");
            dm = conversation(sender, receiver);
        });
    }

    @Test
    void retryReturnsOriginalMessageAndSummaryCountsItOnce() {
        var request = request(dm, UUID.randomUUID());
        var first = service.saveMessage(request, sender.getEmail());
        var retry = service.saveMessage(request, sender.getEmail());
        assertEquals(first.getId(), retry.getId());
        assertEquals(request.getClientMessageId(), retry.getClientMessageId());
        var page = service.getDmMessages(dm.getId(), null, 50, receiver.getEmail());
        assertEquals(1, page.getMessages().size());
        var summary = friends.getMyDms(receiver.getEmail(), org.springframework.data.domain.PageRequest.of(0, 50)).content().getFirst();
        assertEquals(1, summary.getUnreadCount());
        assertEquals("Hello", summary.getLastMessagePreview());
        assertNotNull(summary.getLastMessageAt());
    }

    @Test
    void latestActivityOrdersBeforePaginationAndReadOrDeletionCannotMoveItBack() {
        var otherDm = conversation(sender, user("third"));
        var initial = service.saveMessage(request(dm, UUID.randomUUID()), sender.getEmail());
        var later = service.saveMessage(request(otherDm, UUID.randomUUID()), sender.getEmail());
        var newest = service.saveMessage(request(dm, UUID.randomUUID()), receiver.getEmail());
        entityManager.flush();
        entityManager.clear();
        var pageable = org.springframework.data.domain.PageRequest.of(0, 1,
                org.springframework.data.domain.Sort.by("createdAt"));
        assertEquals(dm.getId(), friends.getMyDms(sender.getEmail(), pageable).content().getFirst().getId());
        friends.markDmRead(dm.getId(), sender.getEmail(), newest.getId());
        service.deleteMessage(newest.getId(), receiver.getEmail());
        entityManager.flush();
        entityManager.clear();
        var summary = friends.getMyDms(sender.getEmail(), pageable).content().getFirst();
        assertEquals(dm.getId(), summary.getId());
        assertEquals(newest.getCreatedAt(), summary.getLastActivityAt());
        assertEquals(initial.getCreatedAt(), summary.getLastMessageAt());
        friends.updateDmPreferences(otherDm.getId(), sender.getEmail(),
                new com.matteo.chat_backend.dto.DmPreferencesRequest(true, null, null));
        assertEquals(otherDm.getId(), friends.getMyDms(sender.getEmail(), pageable).content().getFirst().getId());
    }

    @Test
    void preferencesArePersistentAndBelongOnlyToCaller() {
        friends.updateDmPreferences(dm.getId(), sender.getEmail(),
                new com.matteo.chat_backend.dto.DmPreferencesRequest(true, true, true));
        entityManager.flush();
        entityManager.clear();
        var pageable = org.springframework.data.domain.PageRequest.of(0, 50);
        var own = friends.getMyDms(sender.getEmail(), pageable).content().getFirst();
        var other = friends.getMyDms(receiver.getEmail(), pageable).content().getFirst();
        assertTrue(own.isPinned() && own.isMuted() && own.isArchived());
        assertFalse(other.isPinned() || other.isMuted() || other.isArchived());
        var outsider = user("outsider");
        assertThrows(com.matteo.chat_backend.exception.ForbiddenException.class,
                () -> friends.updateDmPreferences(dm.getId(), outsider.getEmail(),
                        new com.matteo.chat_backend.dto.DmPreferencesRequest(false, false, false)));
    }

    @Test
    void readReceiptsRespectLoadedMessageAndNeverMoveBackwards() {
        var first = service.saveMessage(request(dm, UUID.randomUUID()), sender.getEmail());
        var second = service.saveMessage(request(dm, UUID.randomUUID()), sender.getEmail());
        friends.markDmRead(dm.getId(), receiver.getEmail(), first.getId());
        var page = service.getDmMessages(dm.getId(), null, 50, sender.getEmail()).getMessages();
        assertNotNull(page.get(0).getReadAt());
        assertNull(page.get(1).getReadAt());
        friends.markDmRead(dm.getId(), receiver.getEmail(), second.getId());
        friends.markDmRead(dm.getId(), receiver.getEmail(), first.getId());
        assertTrue(service.getDmMessages(dm.getId(), null, 50, sender.getEmail()).getMessages()
                .stream().allMatch(m -> m.getReadAt() != null));
        var otherDm = conversation(sender, user("third"));
        var foreignMessage = service.saveMessage(request(otherDm, UUID.randomUUID()), sender.getEmail());
        assertThrows(RuntimeException.class, () -> friends.markDmRead(dm.getId(), receiver.getEmail(), foreignMessage.getId()));
    }

    @Test
    void sharedGalleryScansEncryptedHistoryWithoutSkippingTiedTimestamps() {
        LocalDateTime timestamp = LocalDateTime.now().minusHours(1);
        for (int i = 0; i < 105; i++) {
            var message = new com.matteo.chat_backend.entity.Message();
            message.setSender(sender);
            message.setDirectMessage(dm);
            message.setCreatedAt(timestamp);
            message.setContent(crypto.encrypt("https://example.invalid/item/" + i));
            if (i == 0) {
                message.setAttachmentName("photo.png");
                message.setAttachmentContentType("image/png");
            } else if (i == 1) {
                message.setAttachmentName("document.pdf");
                message.setAttachmentContentType("application/pdf");
            }
            messages.save(message);
        }
        messages.flush();
        var first = service.getSharedItems(dm.getId(), "links", null, receiver.getEmail());
        assertEquals(100, first.messages().size());
        assertTrue(first.hasMore());
        var second = service.getSharedItems(dm.getId(), "links", first.nextBefore(), receiver.getEmail());
        assertEquals(5, second.messages().size());
        assertFalse(second.hasMore());
        assertTrue(first.messages().stream().noneMatch(m -> second.messages().stream().anyMatch(n -> n.getId().equals(m.getId()))));
        var media = service.getSharedItems(dm.getId(), "media", null, receiver.getEmail());
        var mediaNext = service.getSharedItems(dm.getId(), "media", media.nextBefore(), receiver.getEmail());
        assertEquals(1, media.messages().size() + mediaNext.messages().size());
        var outsider = user("outsider");
        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> service.getSharedItems(dm.getId(), "links", null, outsider.getEmail()));
        service.deleteMessage(first.messages().getFirst().getId(), sender.getEmail());
        assertFalse(service.getSharedItems(dm.getId(), "links", null, receiver.getEmail()).messages().stream()
                .anyMatch(m -> m.getId().equals(first.messages().getFirst().getId())));
    }

    @Test
    void identifierIsScopedToSenderAndCannotBeReusedAcrossConversations() {
        var id = UUID.randomUUID();
        var first = service.saveMessage(request(dm, id), sender.getEmail());
        var other = service.saveMessage(request(dm, id), receiver.getEmail());
        assertNotEquals(first.getId(), other.getId());
        var secondDm = conversation(sender, user("third"));
        assertThrows(IllegalArgumentException.class, () -> service.saveMessage(request(secondDm, id), sender.getEmail()));
    }

    @Test
    void attachmentRetryDoesNotUploadTwice() {
        var request = request(dm, UUID.randomUUID());
        var file = new MockMultipartFile("file", "note.txt", "text/plain", new byte[]{1, 2});
        when(storage.upload(any())).thenReturn(new AttachmentStorageService.StoredAttachment("key", "note.txt", "text/plain", 2));
        var first = service.saveAttachmentMessage(request, file, sender.getEmail());
        var retry = service.saveAttachmentMessage(request, file, sender.getEmail());
        assertEquals(first.getId(), retry.getId());
        verify(storage, times(1)).upload(any());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentRetriesPersistOnlyOneMessage() throws Exception {
        var request = request(dm, UUID.randomUUID());
        var start = new CountDownLatch(1);
        java.util.function.Supplier<UUID> send = () -> {
            try { assertTrue(start.await(5, TimeUnit.SECONDS)); }
            catch (InterruptedException e) { throw new RuntimeException(e); }
            return service.saveMessage(request, sender.getEmail()).getId();
        };
        var first = CompletableFuture.supplyAsync(send);
        var second = CompletableFuture.supplyAsync(send);
        start.countDown();
        assertEquals(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
        new TransactionTemplate(transactions).executeWithoutResult(status ->
                assertEquals(1, service.getDmMessages(dm.getId(), null, 50, sender.getEmail()).getMessages().size()));
    }

    private User user(String name) {
        var user = new User();
        user.setUsername(name + UUID.randomUUID().toString().substring(0, 8));
        user.setEmail(UUID.randomUUID() + "@example.invalid");
        user.setPasswordHash("test-only");
        return users.saveAndFlush(user);
    }

    private DirectMessage conversation(User one, User two) {
        var conversation = new DirectMessage();
        conversation.setUser1(one);
        conversation.setUser2(two);
        conversation.setCreatedAt(LocalDateTime.now().minusDays(1));
        conversation.setUser1LastReadAt(conversation.getCreatedAt());
        conversation.setUser2LastReadAt(conversation.getCreatedAt());
        return dms.saveAndFlush(conversation);
    }

    private MessageRequest request(DirectMessage destination, UUID identifier) {
        var request = new MessageRequest();
        request.setDmId(destination.getId());
        request.setContent("Hello");
        request.setClientMessageId(identifier);
        return request;
    }
}
