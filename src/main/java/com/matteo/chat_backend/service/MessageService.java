package com.matteo.chat_backend.service;

import com.matteo.chat_backend.dto.MessageRequest;
import com.matteo.chat_backend.dto.MessageResponse;
import com.matteo.chat_backend.dto.MessagePageResponse;
import com.matteo.chat_backend.dto.TypingRequest;
import com.matteo.chat_backend.entity.Channel;
import com.matteo.chat_backend.entity.DirectMessage;
import com.matteo.chat_backend.entity.Message;
import com.matteo.chat_backend.entity.User;
import com.matteo.chat_backend.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.ArrayList;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class MessageService {

    private final MessageRepository messageRepository;
    private final ChatAuthorizationService authorizationService;
    private final MessageCryptoService cryptoService;
    private final RequestRateLimiter rateLimiter;
    private final AttachmentStorageService attachmentStorageService;

    @Transactional
    public MessageResponse saveMessage(MessageRequest request, String senderEmail) {
        if (request.getContent() == null || request.getContent().isBlank()) {
            throw new IllegalArgumentException("El mensaje no puede estar vacio");
        }
        return saveMessage(request, senderEmail, null);
    }

    @Transactional
    public MessageResponse saveAttachmentMessage(MessageRequest request, MultipartFile file, String senderEmail) {
        return saveMessage(request, senderEmail, file);
    }

    private MessageResponse saveMessage(MessageRequest request, String senderEmail, MultipartFile attachmentFile) {
        validateDestination(request.getChannelId(), request.getDmId());
        rateLimiter.requireCapacity("message:" + senderEmail, 30, Duration.ofSeconds(10), "Estas enviando mensajes demasiado rapido");
        User sender = authorizationService.requireUser(senderEmail);

        Message message = new Message();
        message.setSender(sender);
        String content = request.getContent() == null ? "" : request.getContent().trim();
        if (content.length() > 4000) {
            throw new IllegalArgumentException("El mensaje no puede superar 4000 caracteres");
        }
        message.setContent(cryptoService.encrypt(content));
        message.setEdited(false);
        message.setCreatedAt(LocalDateTime.now());

        if (request.getChannelId() != null) {
            Channel channel = authorizationService.requireChannelAccess(request.getChannelId(), senderEmail);
            message.setChannel(channel);
        } else {
            DirectMessage directMessage = authorizationService.requireDirectMessageAccess(request.getDmId(), senderEmail);
            message.setDirectMessage(directMessage);
        }

        if (request.getReplyToId() != null) {
            Message replyTo = messageRepository.findActiveById(request.getReplyToId())
                    .orElseThrow(() -> new IllegalArgumentException("El mensaje al que respondes ya no existe"));
            requireSameDestination(message, replyTo);
            message.setReplyTo(replyTo);
        }

        AttachmentStorageService.StoredAttachment attachment = null;
        try {
            if (attachmentFile != null) {
                attachment = attachmentStorageService.upload(attachmentFile);
                message.setAttachmentKey(attachment.key());
                message.setAttachmentName(attachment.name());
                message.setAttachmentContentType(attachment.contentType());
                message.setAttachmentSize(attachment.size());
            }
            return toResponse(messageRepository.save(message));
        } catch (RuntimeException exception) {
            if (attachment != null) attachmentStorageService.delete(attachment.key());
            throw exception;
        }
    }

    public MessagePageResponse getChannelMessages(UUID channelId, LocalDateTime before, int limit, String requesterEmail) {
        authorizationService.requireChannelAccess(channelId, requesterEmail);
        return toPage(messageRepository.findByChannelIdAndCreatedAtBeforeOrderByCreatedAtDesc(
                channelId, cursor(before), PageRequest.of(0, normalizedLimit(limit) + 1)), normalizedLimit(limit));
    }

    public MessagePageResponse getDmMessages(UUID dmId, LocalDateTime before, int limit, String requesterEmail) {
        authorizationService.requireDirectMessageAccess(dmId, requesterEmail);
        return toPage(messageRepository.findByDirectMessageIdAndCreatedAtBeforeOrderByCreatedAtDesc(
                dmId, cursor(before), PageRequest.of(0, normalizedLimit(limit) + 1)), normalizedLimit(limit));
    }

    public List<MessageResponse> searchChannelMessages(
            UUID channelId,
            String query,
            String sender,
            LocalDateTime from,
            LocalDateTime to,
            boolean attachmentsOnly,
            int limit,
            String requesterEmail
    ) {
        authorizationService.requireChannelAccess(channelId, requesterEmail);
        validateAndRateLimitSearch(query, sender, from, to, attachmentsOnly, requesterEmail);
        return searchMessages(
                messageRepository.findByChannelIdAndCreatedAtBeforeOrderByCreatedAtDesc(
                        channelId, cursor(null), PageRequest.of(0, 1000)),
                query, sender, from, to, attachmentsOnly, limit, requesterEmail
        );
    }

    public List<MessageResponse> searchDmMessages(
            UUID dmId,
            String query,
            String sender,
            LocalDateTime from,
            LocalDateTime to,
            boolean attachmentsOnly,
            int limit,
            String requesterEmail
    ) {
        authorizationService.requireDirectMessageAccess(dmId, requesterEmail);
        validateAndRateLimitSearch(query, sender, from, to, attachmentsOnly, requesterEmail);
        return searchMessages(
                messageRepository.findByDirectMessageIdAndCreatedAtBeforeOrderByCreatedAtDesc(
                        dmId, cursor(null), PageRequest.of(0, 1000)),
                query, sender, from, to, attachmentsOnly, limit, requesterEmail
        );
    }

    public MessagePageResponse getMessageContext(UUID messageId, String requesterEmail) {
        Message center = messageRepository.findActiveById(messageId)
                .orElseThrow(() -> new IllegalArgumentException("Mensaje no encontrado"));
        UUID channelId = center.getChannel() != null ? center.getChannel().getId() : null;
        UUID dmId = center.getDirectMessage() != null ? center.getDirectMessage().getId() : null;
        if (channelId != null) authorizationService.requireChannelAccess(channelId, requesterEmail);
        else authorizationService.requireDirectMessageAccess(dmId, requesterEmail);

        int sideSize = 25;
        List<Message> older = channelId != null
                ? messageRepository.findByChannelIdAndCreatedAtBeforeOrderByCreatedAtDesc(
                        channelId, center.getCreatedAt(), PageRequest.of(0, sideSize + 1))
                : messageRepository.findByDirectMessageIdAndCreatedAtBeforeOrderByCreatedAtDesc(
                        dmId, center.getCreatedAt(), PageRequest.of(0, sideSize + 1));
        boolean hasOlder = older.size() > sideSize;
        older = new ArrayList<>(older.subList(0, Math.min(sideSize, older.size())));
        java.util.Collections.reverse(older);

        List<Message> newer = channelId != null
                ? messageRepository.findByChannelIdAndCreatedAtAfterOrderByCreatedAtAsc(
                        channelId, center.getCreatedAt(), PageRequest.of(0, sideSize))
                : messageRepository.findByDirectMessageIdAndCreatedAtAfterOrderByCreatedAtAsc(
                        dmId, center.getCreatedAt(), PageRequest.of(0, sideSize));
        List<MessageResponse> context = new ArrayList<>();
        older.stream().map(this::toResponse).forEach(context::add);
        context.add(toResponse(center));
        newer.stream().map(this::toResponse).forEach(context::add);
        return new MessagePageResponse(context, hasOlder);
    }

    @Transactional
    public MessageResponse editMessage(UUID messageId, String content, String requesterEmail) {
        Message message = requireOwnedMessage(messageId, requesterEmail);
        if (content == null || content.isBlank() || content.length() > 4000) {
            throw new IllegalArgumentException("El mensaje debe contener entre 1 y 4000 caracteres");
        }
        message.setContent(cryptoService.encrypt(content.trim()));
        message.setEdited(true);
        return toResponse(messageRepository.save(message));
    }

    @Transactional
    public MessageResponse deleteMessage(UUID messageId, String requesterEmail) {
        Message message = requireOwnedMessage(messageId, requesterEmail);
        MessageResponse response = toResponse(message);
        response.setContent("");
        response.setDeleted(true);
        message.setDeletedAt(LocalDateTime.now());
        messageRepository.save(message);
        attachmentStorageService.delete(message.getAttachmentKey());
        return response;
    }

    public DirectMessage getDirectMessage(UUID dmId, String requesterEmail) {
        return authorizationService.requireDirectMessageAccess(dmId, requesterEmail);
    }

    public User authorizeTyping(TypingRequest request, String email) {
        validateDestination(request.getChannelId(), request.getDmId());
        rateLimiter.requireCapacity("typing:" + email, 30, Duration.ofSeconds(5), "Demasiados eventos de escritura");
        if (request.getChannelId() != null) {
            authorizationService.requireChannelAccess(request.getChannelId(), email);
        } else {
            authorizationService.requireDirectMessageAccess(request.getDmId(), email);
        }
        return authorizationService.requireUser(email);
    }

    private MessageResponse toResponse(Message message) {
        return new MessageResponse(
                message.getId(),
                cryptoService.decrypt(message.getContent()),
                message.getSender().getId(),
                message.getSender().getUsername(),
                message.getSender().getAvatarUrl(),
                message.getChannel() != null ? message.getChannel().getId() : null,
                message.getDirectMessage() != null ? message.getDirectMessage().getId() : null,
                message.getEdited(),
                false,
                message.getReplyTo() != null ? message.getReplyTo().getId() : null,
                message.getReplyTo() != null ? message.getReplyTo().getSender().getUsername() : null,
                message.getReplyTo() != null ? replyPreview(message.getReplyTo()) : null,
                message.getAttachmentName(),
                message.getAttachmentContentType(),
                message.getAttachmentSize(),
                attachmentStorageService.createSignedUrl(message.getAttachmentKey()),
                message.getCreatedAt()
        );
    }

    private List<MessageResponse> searchMessages(
            List<Message> candidates,
            String query,
            String sender,
            LocalDateTime from,
            LocalDateTime to,
            boolean attachmentsOnly,
            int limit,
            String requesterEmail
    ) {
        String cleanQuery = normalizeSearchText(query, 120);
        String cleanSender = normalizeSearchText(sender, 80);
        int resultLimit = Math.max(1, Math.min(limit, 50));
        return candidates.stream()
                .filter(message -> from == null || !message.getCreatedAt().isBefore(from))
                .filter(message -> to == null || !message.getCreatedAt().isAfter(to))
                .filter(message -> !attachmentsOnly || message.getAttachmentKey() != null)
                .filter(message -> cleanSender.isBlank()
                        || message.getSender().getUsername().toLowerCase(Locale.ROOT).contains(cleanSender))
                .filter(message -> {
                    if (cleanQuery.isBlank()) return true;
                    String content = cryptoService.decrypt(message.getContent()).toLowerCase(Locale.ROOT);
                    String attachmentName = message.getAttachmentName() == null
                            ? "" : message.getAttachmentName().toLowerCase(Locale.ROOT);
                    return content.contains(cleanQuery) || attachmentName.contains(cleanQuery);
                })
                .limit(resultLimit)
                .map(this::toResponse)
                .toList();
    }

    private void validateAndRateLimitSearch(
            String query,
            String sender,
            LocalDateTime from,
            LocalDateTime to,
            boolean attachmentsOnly,
            String requesterEmail
    ) {
        String cleanQuery = normalizeSearchText(query, 120);
        String cleanSender = normalizeSearchText(sender, 80);
        if (cleanQuery.isBlank() && cleanSender.isBlank() && from == null && to == null && !attachmentsOnly) {
            throw new IllegalArgumentException("Indica texto, autor, fechas o el filtro de archivos");
        }
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException("La fecha inicial no puede ser posterior a la final");
        }
        rateLimiter.requireCapacity("message-search:" + requesterEmail, 12, Duration.ofMinutes(1),
                "Demasiadas busquedas, espera un momento");
    }

    private String normalizeSearchText(String value, int maxLength) {
        if (value == null) return "";
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException("El filtro de busqueda es demasiado largo");
        }
        return normalized;
    }

    private void requireSameDestination(Message message, Message replyTo) {
        boolean sameChannel = message.getChannel() != null && replyTo.getChannel() != null
                && message.getChannel().getId().equals(replyTo.getChannel().getId());
        boolean sameDm = message.getDirectMessage() != null && replyTo.getDirectMessage() != null
                && message.getDirectMessage().getId().equals(replyTo.getDirectMessage().getId());
        if (!sameChannel && !sameDm) {
            throw new IllegalArgumentException("Solo puedes responder a un mensaje de esta conversacion");
        }
    }

    private String replyPreview(Message message) {
        String content = cryptoService.decrypt(message.getContent());
        if (content.isBlank() && message.getAttachmentName() != null) {
            return message.getAttachmentContentType() != null && message.getAttachmentContentType().startsWith("audio/")
                    ? "Nota de voz"
                    : "Archivo: " + message.getAttachmentName();
        }
        return content.length() <= 180 ? content : content.substring(0, 180) + "...";
    }

    private Message requireOwnedMessage(UUID messageId, String requesterEmail) {
        Message message = messageRepository.findActiveById(messageId)
                .orElseThrow(() -> new IllegalArgumentException("Mensaje no encontrado"));
        authorizationService.requireUser(requesterEmail);
        if (!message.getSender().getEmail().equalsIgnoreCase(requesterEmail)) {
            throw new org.springframework.security.access.AccessDeniedException("Solo puedes modificar tus propios mensajes");
        }
        if (message.getChannel() != null) {
            authorizationService.requireChannelAccess(message.getChannel().getId(), requesterEmail);
        } else {
            authorizationService.requireDirectMessageAccess(message.getDirectMessage().getId(), requesterEmail);
        }
        return message;
    }

    private MessagePageResponse toPage(List<Message> descending, int limit) {
        boolean hasMore = descending.size() > limit;
        List<Message> page = new ArrayList<>(descending.subList(0, Math.min(limit, descending.size())));
        java.util.Collections.reverse(page);
        return new MessagePageResponse(page.stream().map(this::toResponse).toList(), hasMore);
    }

    private int normalizedLimit(int limit) {
        return Math.max(1, Math.min(limit, 100));
    }

    private LocalDateTime cursor(LocalDateTime before) {
        return before != null ? before : LocalDateTime.of(9999, 12, 31, 23, 59, 59);
    }

    private void validateDestination(UUID channelId, UUID dmId) {
        if ((channelId == null) == (dmId == null)) {
            throw new IllegalArgumentException("El mensaje debe pertenecer exactamente a un canal o una conversacion");
        }
    }
}
