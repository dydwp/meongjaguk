package com.meongjaguk.app.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 산책로 게시판 게시글 댓글 (walk_meeting_comments)
 */
@Entity
@Table(name = "walk_meeting_comments")
public class BoardComment {

    public static final int MAX_CONTENT_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "comment_id")
    private Long commentId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "meeting_id", nullable = false,
                foreignKey = @ForeignKey(name = "fk_walk_meeting_comments_meeting"))
    private Board board;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false,
                foreignKey = @ForeignKey(name = "fk_walk_meeting_comments_user"))
    private User user;

    @Column(nullable = false, length = MAX_CONTENT_LENGTH)
    private String content;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected BoardComment() {}

    public static BoardComment create(Board board, User user, String content) {
        BoardComment comment = new BoardComment();
        comment.board = board;
        comment.user = user;
        comment.content = content;
        return comment;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public Long getCommentId() { return commentId; }
    public Board getBoard() { return board; }
    public User getUser() { return user; }
    public String getContent() { return content; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
