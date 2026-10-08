package org.sopt.post.adapter.out.id;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SequentialPostIdGeneratorTest {
  @Test
  void startsAtOneAndIncrementsWithoutReuse() {
    var generator = new SequentialPostIdGenerator();
    assertEquals(1L, generator.nextId());
    assertEquals(2L, generator.nextId());
    assertEquals(3L, generator.nextId());
  }
}
