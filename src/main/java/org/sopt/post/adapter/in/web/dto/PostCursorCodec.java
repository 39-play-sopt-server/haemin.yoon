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
  private static final int MAX_TOKEN_LENGTH = 160;

  private PostCursorCodec() {}

  public static String encode(PostCursor cursor) {
    if (cursor == null) return null;
    String value =
        "1|"
            + cursor.createdAt()
            + "|"
            + cursor.id()
            + "|"
            + (cursor.category() == null ? "ALL" : PostCategory.from(cursor.category()).name());
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(value.getBytes(StandardCharsets.UTF_8));
  }

  public static PostCursor decode(String token) {
    if (token == null || token.isEmpty()) return null;
    if (token.length() > MAX_TOKEN_LENGTH) throw invalidCursor();
    try {
      String[] fields =
          new String(Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8).split("\\|", -1);
      if (fields.length != 4 || !fields[0].equals("1")) throw invalidCursor();
      var cursor =
          new PostCursor(
              LocalDateTime.parse(fields[1]),
              Long.parseLong(fields[2]),
              fields[3].equals("ALL") ? null : PostCategory.valueOf(fields[3]).toDomain());
      if (!encode(cursor).equals(token)) throw invalidCursor();
      return cursor;
    } catch (IllegalArgumentException | DateTimeException exception) {
      throw invalidCursor();
    }
  }

  private static PostException invalidCursor() {
    return new PostException(PostErrorCode.INVALID_CURSOR);
  }
}
