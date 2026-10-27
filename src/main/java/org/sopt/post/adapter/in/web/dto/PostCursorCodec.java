package org.sopt.post.adapter.in.web.dto;

import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.util.Base64;
import org.sopt.post.application.port.in.query.PostCursor;
import org.sopt.post.domain.exception.PostErrorCode;
import org.sopt.post.domain.exception.PostException;

/** URL로 전달하는 커서 형식을 웹 어댑터에서 관리합니다. 내부 커서는 인코딩에 의존하지 않습니다. */
public final class PostCursorCodec {
  private static final int MAX_TOKEN_LENGTH = 2048;

  private PostCursorCodec() {}

  public static String encode(PostCursor cursor) {
    if (cursor == null) return null;
    String value =
        (cursor.authorId() != null ? "3|" : cursor.keyword() == null ? "1|" : "2|")
            + cursor.createdAt()
            + "|"
            + cursor.id()
            + "|"
            + (cursor.category() == null ? "ALL" : PostCategory.from(cursor.category()).name())
            + (cursor.authorId() == null ? "" : "|" + cursor.authorId())
            + (cursor.authorId() != null || cursor.keyword() != null
                ? "|" + (cursor.keyword() == null ? "" : cursor.keyword())
                : "");
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(value.getBytes(StandardCharsets.UTF_8));
  }

  public static PostCursor decode(String token) {
    if (token == null || token.isEmpty()) return null;
    if (token.length() > MAX_TOKEN_LENGTH) throw invalidCursor();
    try {
      String raw = new String(Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8);
      String[] fields = raw.split("\\|", raw.startsWith("3|") ? 6 : 5);
      boolean versionOne = fields.length == 4 && fields[0].equals("1");
      boolean versionTwo = fields.length == 5 && fields[0].equals("2");
      boolean versionThree = fields.length == 6 && fields[0].equals("3");
      if (!versionOne && !versionTwo && !versionThree) throw invalidCursor();
      var cursor =
          new PostCursor(
              LocalDateTime.parse(fields[1]),
              Long.parseLong(fields[2]),
              fields[3].equals("ALL") ? null : PostCategory.valueOf(fields[3]).toDomain(),
              versionThree ? fields[5] : versionTwo ? fields[4] : null,
              versionThree ? Long.valueOf(fields[4]) : null);
      if (!encode(cursor).equals(token)) throw invalidCursor();
      return cursor;
    } catch (IllegalArgumentException | DateTimeException | PostException exception) {
      throw invalidCursor();
    }
  }

  private static PostException invalidCursor() {
    return new PostException(PostErrorCode.INVALID_CURSOR);
  }
}
