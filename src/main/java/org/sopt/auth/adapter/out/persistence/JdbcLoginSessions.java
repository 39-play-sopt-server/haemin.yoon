package org.sopt.auth.adapter.out.persistence;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.sopt.auth.application.port.out.LoginSessions;
import org.sopt.global.security.AccessIdentity;
import org.sopt.global.security.SessionAuthority;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class JdbcLoginSessions implements LoginSessions, SessionAuthority {
  private final JdbcTemplate jdbc;

  @Override
  @Transactional
  public void open(AccessIdentity identity, String hash, Instant expiry) {
    jdbc.update("delete from login_sessions where expires_at <= CURRENT_TIMESTAMP");
    jdbc.update(
        "insert into login_sessions(id,member_id,refresh_hash,expires_at) values (?,?,?,?)",
        identity.sessionId(),
        identity.memberId(),
        hash,
        Timestamp.from(expiry));
  }

  @Override
  @Transactional
  public Optional<Session> rotate(String previous, String next) {
    int updated =
        jdbc.update(
            """
            UPDATE login_sessions SET refresh_hash = ?
            WHERE refresh_hash = ? AND expires_at > CURRENT_TIMESTAMP
              AND EXISTS (SELECT 1 FROM members m WHERE m.id = login_sessions.member_id AND NOT m.deleted)
            """,
            next,
            previous);
    if (updated == 0) return Optional.empty();
    return Optional.of(
        jdbc.queryForObject(
            "select id, member_id, expires_at from login_sessions where refresh_hash=?",
            (rs, i) ->
                new Session(
                    new AccessIdentity(rs.getLong("member_id"), rs.getObject("id", UUID.class)),
                    rs.getTimestamp("expires_at").toInstant()),
            next));
  }

  @Override
  public Optional<String> currentRole(AccessIdentity identity) {
    return jdbc
        .query(
            """
            SELECT m.role FROM login_sessions s JOIN members m ON m.id = s.member_id
            WHERE s.id = ? AND m.id = ? AND NOT m.deleted AND s.expires_at > CURRENT_TIMESTAMP
            """,
            (rs, i) -> rs.getString(1),
            identity.sessionId(),
            identity.memberId())
        .stream()
        .findFirst();
  }

  @Override
  public void close(UUID id) {
    jdbc.update("delete from login_sessions where id=?", id);
  }
}
